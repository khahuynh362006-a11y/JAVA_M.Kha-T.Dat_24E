package com.cineverse.catalog;

import static com.cineverse.catalog.CatalogDtos.*;

import com.cineverse.shared.*;

import jakarta.persistence.criteria.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final CountryRepository countries;
    private final SeasonRepository seasons;
    private final EpisodeRepository episodes;
    private final JdbcTemplate db;
    private final Audit audit;

    public CatalogService(
            MovieRepository movies,
            GenreRepository genres,
            CountryRepository countries,
            SeasonRepository seasons,
            EpisodeRepository episodes,
            JdbcTemplate db,
            Audit audit) {
        this.movies = movies;
        this.genres = genres;
        this.countries = countries;
        this.seasons = seasons;
        this.episodes = episodes;
        this.db = db;
        this.audit = audit;
    }

    public Paged<MovieView> list(Map<String, String> q, boolean admin) {
        int page = number(q.get("page"), 0, 0, 10000), size = number(q.get("size"), 18, 1, 48);
        List<Long> screeningIds =
                has(q, "screening")
                        ? db.queryForList(
                                "select distinct movie_id from showtimes where active=true and"
                                        + " starts_at>?",
                                Long.class,
                                java.sql.Timestamp.from(Instant.now().plusSeconds(600)))
                        : List.of();
        Specification<Movie> spec =
                (root, query, cb) -> {
                    List<Predicate> p = new ArrayList<>();
                    p.add(cb.isFalse(root.get("deleted")));
                    if (!admin) p.add(root.get("status").in("PUBLISHED", "COMPLETED"));
                    if (has(q, "status")) p.add(cb.equal(root.get("status"), q.get("status")));
                    if (has(q, "q")) {
                        String text =
                                Text.normalize(q.get("q"))
                                        .replace("\\", "\\\\")
                                        .replace("%", "\\%")
                                        .replace("_", "\\_");
                        p.add(cb.or(cb.like(root.get("searchText"), "%" + text + "%", '\\'),cb.equal(root.get("slug"),q.get("q").strip())));
                    }
                    if (has(q, "type")) p.add(cb.equal(root.get("type"), q.get("type")));
                    if (has(q, "screening")) {
                        var showing =
                                root.get("id")
                                        .in(screeningIds.isEmpty() ? List.of(-1L) : screeningIds);
                        p.add(q.get("screening").equals("upcoming") ? cb.not(showing) : showing);
                    }
                    if (has(q, "year"))
                        p.add(
                                cb.equal(
                                        root.get("releaseYear"),
                                        number(q.get("year"), 2026, 1888, 2100)));
                    if (has(q, "vip"))
                        p.add(cb.equal(root.get("vip"), Boolean.parseBoolean(q.get("vip"))));
                    if (has(q, "featured"))
                        p.add(
                                cb.equal(
                                        root.get("featured"),
                                        Boolean.parseBoolean(q.get("featured"))));
                    if (has(q, "genre")) {
                        p.add(cb.equal(root.join("genres").get("slug"), q.get("genre")));
                        query.distinct(true);
                    }
                    if (has(q, "country")) {
                        var c =
                                countries.findAll().stream()
                                        .filter(x -> x.slug.equals(q.get("country")))
                                        .findFirst();
                        p.add(cb.equal(root.get("countryId"), c.map(x -> x.id).orElse(-1L)));
                    }
                    if (has(q, "rating"))
                        p.add(cb.ge(root.get("rating"), number(q.get("rating"), 0, 0, 5)));
                    return cb.and(p.toArray(Predicate[]::new));
                };
        Sort sort =
                switch (q.getOrDefault("sort", "new")) {
                    case "popular" -> Sort.by(Sort.Direction.DESC, "views", "priority", "id");
                    case "rating" -> Sort.by(Sort.Direction.DESC, "rating", "id");
                    case "az" -> Sort.by("title", "id");
                    case "featured" -> Sort.by(Sort.Direction.DESC, "priority", "id");
                    default -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
                };
        var result = movies.findAll(spec, PageRequest.of(page, size, sort));
        return new Paged<>(
                result.getContent().stream().map(CatalogDtos::view).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                page,
                size);
    }

    public Movie published(Long id) {
        Movie m = movies.findById(id).orElseThrow(ApiException::missing);
        if (m.deleted || !(m.status.equals("PUBLISHED") || m.status.equals("COMPLETED")))
            throw ApiException.missing();
        return m;
    }

    public Detail detail(String slug) {
        Movie m = movies.findBySlugAndDeletedFalse(slug).orElseThrow(ApiException::missing);
        published(m.id);
        var seasonViews =
                seasons.findByMovieIdOrderByNumber(m.id).stream()
                        .map(
                                s ->
                                        new SeasonView(
                                                s.id,
                                                s.number,
                                                s.title,
                                                episodes.findBySeasonIdOrderByNumber(s.id).stream()
                                                        .filter(e -> e.published)
                                                        .map(
                                                                e ->
                                                                        new EpisodeView(
                                                                                e.id,
                                                                                e.number,
                                                                                e.title,
                                                                                e.description,
                                                                                e.duration,
                                                                                e.published,
                                                                                e.vip))
                                                        .toList()))
                        .toList();
        return new Detail(view(m), seasonViews, related(m));
    }

    public List<MovieView> related(Movie m) {
        String genre = m.genres.stream().findFirst().map(g -> g.slug).orElse("");
        return list(Map.of("genre", genre, "size", "12", "sort", "rating"), false)
                .content()
                .stream()
                .filter(x -> !x.id().equals(m.id))
                .limit(6)
                .toList();
    }

    public List<MovieView> related(Long id) {
        return related(published(id));
    }

    public Paged<MovieView> featured() {
        var ids =
                db.queryForList(
                        "select b.movie_id from banners b join movies m on m.id=b.movie_id where"
                                + " b.active=true and m.deleted=false and m.status in"
                                + " ('PUBLISHED','COMPLETED') order by b.sort_order,b.id limit 5",
                        Long.class);
        var result = ids.stream().distinct().map(this::published).map(CatalogDtos::view).toList();
        return new Paged<>(result, result.size(), result.isEmpty() ? 0 : 1, 0, 5);
    }

    public List<MovieView> recommend(Long userId) {
        var ids =
                db.queryForList(
                        "select mg.genre_id from movie_genres mg where mg.movie_id in (select"
                            + " movie_id from favorites where user_id=? union select movie_id from"
                            + " watch_history where user_id=?) group by mg.genre_id order by"
                            + " count(*) desc limit 3",
                        Long.class,
                        userId,
                        userId);
        if (ids.isEmpty()) return list(Map.of("sort", "popular", "size", "12"), false).content();
        String slug = genres.findById(ids.getFirst()).map(g -> g.slug).orElse("");
        return list(Map.of("genre", slug, "sort", "rating", "size", "12"), false).content();
    }

    @Transactional
    public MovieView save(Long id, MovieInput in, Long actor) {
        Movie m = id == null ? new Movie() : movies.findById(id).orElseThrow(ApiException::missing);
        if (!countries.existsById(in.countryId())) throw ApiException.bad("Quốc gia không hợp lệ.");
        var gs = genres.findAllById(in.genreIds());
        if (gs.size() != new HashSet<>(in.genreIds()).size())
            throw ApiException.bad("Thể loại không hợp lệ.");
        asset(in.poster());
        asset(in.backdrop());
        mediaName(in.videoFile(), "mp4");
        mediaName(in.trailerFile(), "mp4");
        mediaName(in.subtitleFile(), "vtt");
        m.title = in.title().strip();
        m.originalTitle = in.originalTitle().strip();
        m.slug = in.slug();
        m.synopsis = in.synopsis();
        m.shortDescription = in.shortDescription();
        m.type = in.type();
        m.countryId = in.countryId();
        m.releaseYear = in.releaseYear();
        m.duration = in.duration();
        m.ageRating = in.ageRating();
        m.language = in.language();
        m.director = in.director();
        m.castNames = in.castNames();
        m.tags = in.tags() == null ? "" : in.tags();
        m.poster = in.poster();
        m.backdrop = in.backdrop();
        m.status = in.status();
        m.vip = in.vip();
        m.featured = in.featured();
        m.priority = in.priority();
        m.videoFile = blank(in.videoFile());
        m.trailerFile = blank(in.trailerFile());
        m.subtitleFile = blank(in.subtitleFile());
        m.genres = new LinkedHashSet<>(gs);
        m.updatedAt = Instant.now();
        m.searchText =
                Text.normalize(
                        m.title
                                + " "
                                + m.originalTitle
                                + " "
                                + m.director
                                + " "
                                + m.castNames
                                + " "
                                + m.tags
                                + " "
                                + String.join(" ", gs.stream().map(g -> g.name).toList()));
        movies.saveAndFlush(m);
        audit.log(actor, id == null ? "MOVIE_CREATE" : "MOVIE_UPDATE", "MOVIE", m.id, m.title);
        return view(m);
    }

    @Transactional
    public void delete(Long id, Long actor) {
        Movie m = movies.findById(id).orElseThrow(ApiException::missing);
        m.deleted = true;
        audit.log(actor, "MOVIE_DELETE", "MOVIE", id, m.title);
    }

    public MovieInput edit(Long id) {
        Movie m = movies.findById(id).orElseThrow(ApiException::missing);
        return new MovieInput(
                m.title,
                m.originalTitle,
                m.slug,
                m.synopsis,
                m.shortDescription,
                m.type,
                m.countryId,
                m.releaseYear,
                m.duration,
                m.ageRating,
                m.language,
                m.director,
                m.castNames,
                m.tags,
                m.poster,
                m.backdrop,
                m.status,
                m.vip,
                m.featured,
                m.priority,
                m.genres.stream().map(g -> g.id).toList(),
                m.videoFile,
                m.trailerFile,
                m.subtitleFile);
    }

    public List<Lookup> genres() {
        return genres.findAll().stream().filter(g -> g.active).map(CatalogDtos::lookup).toList();
    }

    public List<Lookup> countries() {
        return countries.findAll().stream().filter(c -> c.active).map(CatalogDtos::lookup).toList();
    }

    public static int number(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void mediaName(String s, String extension) {
        if (s != null && !s.isBlank() && !s.matches("[a-zA-Z0-9_-]+\\." + extension))
            throw ApiException.bad("Tên tệp media không hợp lệ. Hãy dùng chức năng tải lên.");
    }

    private void asset(String s) {
        if (!s.startsWith("/art/") && !s.startsWith("/api/v1/assets/"))
            throw ApiException.bad(
                    "Ảnh phải là tài nguyên local đã tải lên hoặc artwork trong /art/.");
        if (s.contains("..") || s.contains("<"))
            throw ApiException.bad("Đường dẫn ảnh không hợp lệ.");
    }

    private boolean has(Map<String, String> q, String key) {
        return q.containsKey(key) && !q.get(key).isBlank();
    }

    private String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
