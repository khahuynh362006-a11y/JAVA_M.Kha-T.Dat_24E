package com.cineverse.community;

import com.cineverse.catalog.*;
import com.cineverse.shared.*;
import com.cineverse.users.*;

import jakarta.validation.constraints.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class CommunityService {
    private final FavoriteRepository favorites;
    private final WatchHistoryRepository histories;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final CatalogService catalog;
    private final EpisodeRepository episodes;
    private final SeasonRepository seasons;
    private final JdbcTemplate db;

    public CommunityService(
            FavoriteRepository favorites,
            WatchHistoryRepository histories,
            ReviewRepository reviews,
            UserRepository users,
            CatalogService catalog,
            EpisodeRepository episodes,
            SeasonRepository seasons,
            JdbcTemplate db) {
        this.favorites = favorites;
        this.histories = histories;
        this.reviews = reviews;
        this.users = users;
        this.catalog = catalog;
        this.episodes = episodes;
        this.seasons = seasons;
        this.db = db;
    }

    public record FavoriteInput(@NotNull Long movieId) {}

    public record Progress(
            @NotNull Long movieId,
            Long episodeId,
            @DecimalMin("0.0") @DecimalMax("100000.0") double positionSeconds,
            @DecimalMin("0.1") @DecimalMax("100000.0") double durationSeconds) {}

    public record HistoryView(
            Long id,
            CatalogDtos.MovieView movie,
            Long episodeId,
            double positionSeconds,
            double durationSeconds,
            boolean completed,
            Instant updatedAt) {}

    public record ReviewInput(
            @Min(1) @Max(5) int stars, @NotBlank @Size(max = 2000) String content) {}

    public record ReviewView(
            Long id,
            Long userId,
            String name,
            String avatar,
            int stars,
            String content,
            Instant createdAt,
            Instant updatedAt) {}

    public record Report(@NotBlank @Size(max = 500) String reason) {}

    @Transactional(readOnly = true)
    public List<CatalogDtos.MovieView> favorites(Long id) {
        return favorites.findByUserIdOrderByCreatedAtDesc(id).stream()
                .map(
                        f -> {
                            try {
                                return CatalogDtos.view(catalog.published(f.movieId));
                            } catch (ApiException e) {
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    public void favorite(Long user, Long movie) {
        catalog.published(movie);
        users.lockById(user).orElseThrow(ApiException::missing);
        if (favorites.findByUserIdAndMovieId(user, movie).isEmpty()) {
            Favorite f = new Favorite();
            f.userId = user;
            f.movieId = movie;
            favorites.save(f);
        }
    }

    public void unfavorite(Long user, Long movie) {
        favorites.findByUserIdAndMovieId(user, movie).ifPresent(favorites::delete);
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(Long id) {
        return histories.findByUserIdOrderByUpdatedAtDesc(id).stream()
                .map(
                        h -> {
                            try {
                                return new HistoryView(
                                        h.id,
                                        CatalogDtos.view(catalog.published(h.movieId)),
                                        h.episodeId,
                                        h.positionSeconds,
                                        h.durationSeconds,
                                        h.completed,
                                        h.updatedAt);
                            } catch (ApiException e) {
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    public void progress(Long user, Progress p) {
        User u = users.lockById(user).orElseThrow(ApiException::missing);
        Movie m = catalog.published(p.movieId());
        boolean vip = m.vip;
        if (p.episodeId() != null) {
            Episode e =
                    episodes.findById(p.episodeId())
                            .filter(x -> x.published)
                            .orElseThrow(ApiException::missing);
            Season s = seasons.findById(e.seasonId).orElseThrow(ApiException::missing);
            if (!s.movieId.equals(m.id)) throw ApiException.bad("Tập phim không thuộc phim này.");
            vip |= e.vip;
        }
        if (vip && (u.vipUntil == null || u.vipUntil.isBefore(Instant.now())))
            throw ApiException.forbidden();
        if (!Double.isFinite(p.positionSeconds())
                || !Double.isFinite(p.durationSeconds())
                || p.positionSeconds() > p.durationSeconds() + 1)
            throw ApiException.bad("Tiến độ xem không hợp lệ.");
        var h = histories.findByUserIdAndMovieId(user, p.movieId()).orElseGet(WatchHistory::new);
        h.userId = user;
        h.movieId = m.id;
        h.episodeId = p.episodeId();
        h.positionSeconds = p.positionSeconds();
        h.durationSeconds = p.durationSeconds();
        h.completed = p.positionSeconds() / p.durationSeconds() >= 0.95;
        h.updatedAt = Instant.now();
        histories.save(h);
    }

    public void removeHistory(Long user, Long id) {
        var h = histories.findById(id).orElseThrow(ApiException::missing);
        if (!h.userId.equals(user)) throw ApiException.forbidden();
        histories.delete(h);
    }

    public void clearHistory(Long user) {
        db.update("delete from watch_history where user_id=?", user);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.Paged<ReviewView> reviews(Long movie, int page) {
        catalog.published(movie);
        var r =
                reviews.findByMovieIdAndHiddenFalseOrderByCreatedAtDesc(
                        movie, PageRequest.of(Math.max(0, page), 10));
        var ids = r.getContent().stream().map(x -> x.userId).toList();
        Map<Long, User> names = new HashMap<>();
        users.findAllById(ids).forEach(u -> names.put(u.id, u));
        return new CatalogDtos.Paged<>(
                r.getContent().stream().map(x -> view(x, names.get(x.userId))).toList(),
                r.getTotalElements(),
                r.getTotalPages(),
                r.getNumber(),
                10);
    }

    public ReviewView review(Long user, Long movie, ReviewInput in) {
        catalog.published(movie);
        User u = users.lockById(user).orElseThrow(ApiException::missing);
        var r = reviews.findByUserIdAndMovieId(user, movie).orElseGet(Review::new);
        r.userId = user;
        r.movieId = movie;
        r.stars = in.stars();
        r.content = in.content().strip();
        r.updatedAt = Instant.now();
        reviews.saveAndFlush(r);
        return view(r, u);
    }

    public ReviewView editReview(Long user, Long id, ReviewInput in) {
        var r = reviews.findById(id).orElseThrow(ApiException::missing);
        if (!r.userId.equals(user)) throw ApiException.forbidden();
        r.stars = in.stars();
        r.content = in.content().strip();
        r.updatedAt = Instant.now();
        return view(r, users.findById(user).orElseThrow(ApiException::missing));
    }

    public void deleteReview(Long user, Long id) {
        var r = reviews.findById(id).orElseThrow(ApiException::missing);
        if (!r.userId.equals(user)) throw ApiException.forbidden();
        db.update("delete from comment_reports where review_id=?", id);
        reviews.delete(r);
    }

    public void report(Long user, Long id, String reason) {
        reviews.findById(id).filter(r -> !r.hidden).orElseThrow(ApiException::missing);
        users.lockById(user).orElseThrow(ApiException::missing);
        if (db.queryForObject(
                        "select count(*) from comment_reports where review_id=? and user_id=?",
                        Long.class,
                        id,
                        user)
                == 0)
            db.update(
                    "insert into comment_reports(review_id,user_id,reason,created_at)"
                            + " values(?,?,?,?)",
                    id,
                    user,
                    reason,
                    Timestamp.from(Instant.now()));
    }

    private ReviewView view(Review r, User u) {
        return new ReviewView(
                r.id, r.userId, u.name, u.avatar, r.stars, r.content, r.createdAt, r.updatedAt);
    }
}
