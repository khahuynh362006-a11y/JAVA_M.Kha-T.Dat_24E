package com.cineverse.streaming;

import com.cineverse.catalog.*;
import com.cineverse.shared.*;
import com.cineverse.users.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MediaService {
    public record Ticket(
            Long userId,
            int sessionVersion,
            Long movieId,
            Long episodeId,
            boolean trailer,
            Instant expiresAt) {}

    public record Playback(
            Long movieId,
            Long episodeId,
            String title,
            String slug,
            String poster,
            String url,
            String subtitleUrl,
            Instant expiresAt,
            boolean demo,
            String license) {}

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final CatalogService catalog;
    private final EpisodeRepository episodes;
    private final SeasonRepository seasons;
    private final UserRepository users;
    private final Path root;
    private final JdbcTemplate db;

    public MediaService(
            CatalogService catalog,
            EpisodeRepository episodes,
            SeasonRepository seasons,
            UserRepository users,
            @Value("${app.media-root}") String root,
            JdbcTemplate db) {
        this.catalog = catalog;
        this.episodes = episodes;
        this.seasons = seasons;
        this.users = users;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.db = db;
    }

    public Playback movie(User u, Long id) {
        Movie m = catalog.published(id);
        if (m.type.equals("SERIES")) throw ApiException.bad("Vui lòng chọn tập phim.");
        authorize(u, m.vip);
        return ticket(u, m, null, false);
    }

    public Playback episode(User u, Long id) {
        Episode e =
                episodes.findById(id).filter(x -> x.published).orElseThrow(ApiException::missing);
        Season s = seasons.findById(e.seasonId).orElseThrow(ApiException::missing);
        Movie m = catalog.published(s.movieId);
        authorize(u, m.vip || e.vip);
        return ticket(u, m, e, false);
    }

    public Playback trailer(Long id) {
        return ticket(null, catalog.published(id), null, true);
    }

    private Playback ticket(User u, Movie m, Episode e, boolean trailer) {
        String filename = trailer ? m.trailerFile : e == null ? m.videoFile : e.videoFile;
        file(filename, "mp4");
        String sub = trailer ? null : e == null ? m.subtitleFile : e.subtitleFile;
        Instant expires = Instant.now().plusSeconds(900);
        String key = Text.random();
        tickets.entrySet().removeIf(x -> x.getValue().expiresAt.isBefore(Instant.now()));
        tickets.put(
                key,
                new Ticket(
                        u == null ? null : u.id,
                        u == null ? 0 : u.sessionVersion,
                        m.id,
                        e == null ? null : e.id,
                        trailer,
                        expires));
        if (!trailer) {
            Timestamp recent = Timestamp.from(Instant.now().minusSeconds(1800));
            if (db.queryForObject(
                            "select count(*) from movie_views where movie_id=? and user_id=? and"
                                    + " viewed_at>?",
                            Long.class,
                            m.id,
                            u.id,
                            recent)
                    == 0)
                db.update(
                        "insert into movie_views(movie_id,user_id,viewed_at) values(?,?,?)",
                        m.id,
                        u.id,
                        Timestamp.from(Instant.now()));
        }
        return new Playback(
                m.id,
                e == null ? null : e.id,
                trailer
                        ? "Trailer · " + m.title
                        : e == null ? m.title : m.title + " · Tập " + e.number,
                m.slug,
                m.backdrop,
                "/api/v1/media/" + key + "/video",
                sub == null ? null : "/api/v1/media/" + key + "/subtitle",
                expires,
                true,
                "Blender Foundation · CC BY 3.0 · Video minh họa kỹ thuật, không phải nội dung phim"
                        + " hư cấu.");
    }

    public Path resolve(String key, boolean subtitle) {
        Ticket t = tickets.get(key);
        if (t == null || t.expiresAt.isBefore(Instant.now()))
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "MEDIA_TICKET_EXPIRED",
                    "Quyền truy cập video đã hết hạn. Vui lòng tải lại trình phát.");
        Movie m = catalog.published(t.movieId);
        Episode e =
                t.episodeId == null
                        ? null
                        : episodes.findById(t.episodeId)
                                .filter(x -> x.published)
                                .orElseThrow(ApiException::missing);
        if (!t.trailer) {
            User u = users.findById(t.userId).orElseThrow(ApiException::missing);
            if (u.locked || u.sessionVersion != t.sessionVersion) throw ApiException.forbidden();
            authorize(u, m.vip || (e != null && e.vip));
        }
        String filename =
                subtitle
                        ? (e == null ? m.subtitleFile : e.subtitleFile)
                        : t.trailer ? m.trailerFile : e == null ? m.videoFile : e.videoFile;
        return file(filename, subtitle ? "vtt" : "mp4");
    }

    private void authorize(User u, boolean vip) {
        if (vip && (u.vipUntil == null || !u.vipUntil.isAfter(Instant.now())))
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "VIP_REQUIRED",
                    "Nâng cấp CineVerse VIP để xem nội dung này.");
    }

    private Path file(String filename, String ext) {
        CatalogService.mediaName(filename, ext);
        if (filename == null)
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MEDIA_UNAVAILABLE",
                    "Media chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        Path p = root.resolve(filename).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p))
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MEDIA_UNAVAILABLE",
                    "Không tìm thấy tệp video. Hãy chạy scripts/setup-media.ps1.");
        return p;
    }
}
