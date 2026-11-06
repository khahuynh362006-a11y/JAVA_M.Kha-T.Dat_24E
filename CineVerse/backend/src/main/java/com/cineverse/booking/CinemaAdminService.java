package com.cineverse.booking;

import com.cineverse.catalog.CatalogService;
import com.cineverse.shared.*;

import jakarta.validation.constraints.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class CinemaAdminService {
    private final JdbcTemplate db;
    private final Audit audit;
    private final CatalogService catalog;

    public CinemaAdminService(JdbcTemplate db, Audit audit, CatalogService catalog) {
        this.db = db;
        this.audit = audit;
        this.catalog = catalog;
    }

    public record CinemaInput(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 500) String address,
            boolean active) {}

    public Long cinema(Long id, CinemaInput in, Long actor) {
        if (id != null && !in.active()) deactivate("cinemas", id, actor);
        long result =
                id == null
                        ? insert(
                                "insert into cinemas(name,city,address,active) values(?,?,?,?)",
                                in.name(),
                                in.city(),
                                in.address(),
                                in.active())
                        : id;
        if (id != null
                && db.update(
                                "update cinemas set name=?,city=?,address=?,active=? where id=?",
                                in.name(),
                                in.city(),
                                in.address(),
                                in.active(),
                                id)
                        == 0) throw ApiException.missing();
        audit.log(actor, "CINEMA_SAVE", "CINEMA", result, in.name());
        return result;
    }

    public record RoomInput(
            @NotNull Long cinemaId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "2D|DOLBY 2D|3D|IMAX") String format,
            @Min(3) @Max(12) int rows,
            @Min(4) @Max(16) int columns,
            boolean active) {}

    public Long room(Long id, RoomInput in, Long actor) {
        if (id != null && !in.active()) deactivate("auditoriums", id, actor);
        if (db.queryForObject("select count(*) from cinemas where id=?", Long.class, in.cinemaId())
                == 0) throw ApiException.bad("Rạp không tồn tại.");
        long result =
                id == null
                        ? insert(
                                "insert into auditoriums(cinema_id,name,format,active)"
                                        + " values(?,?,?,?)",
                                in.cinemaId(),
                                in.name(),
                                in.format(),
                                in.active())
                        : id;
        if (id == null) {
            for (int r = 0; r < in.rows(); r++)
                for (int n = 1; n <= in.columns(); n++)
                    db.update(
                            "insert into seats(auditorium_id,row_label,seat_number,kind,active)"
                                    + " values(?,?,?,'STANDARD',true)",
                            result,
                            String.valueOf((char) ('A' + r)),
                            n);
        } else if (db.update(
                        "update auditoriums set name=?,format=?,active=? where id=?",
                        in.name(),
                        in.format(),
                        in.active(),
                        id)
                == 0) throw ApiException.missing();
        audit.log(actor, "AUDITORIUM_SAVE", "AUDITORIUM", result, in.name());
        return result;
    }

    public record SeatInput(
            @Pattern(regexp = "STANDARD|VIP") @NotNull String kind, boolean active) {}

    public void seat(Long id, SeatInput in, Long actor) {
        if (db.queryForList("select id from seats where id=? for update", Long.class, id).isEmpty())
            throw ApiException.missing();
        if (db.queryForObject(
                        "select count(*) from seat_reservations where seat_id=?", Long.class, id)
                > 0) throw ApiException.bad("Không sửa ghế đang được giữ hoặc đã bán.");
        if (db.update("update seats set kind=?,active=? where id=?", in.kind(), in.active(), id)
                == 0) throw ApiException.missing();
        audit.log(actor, "SEAT_UPDATE", "SEAT", id, in.kind());
    }

    public record ShowtimeInput(
            @NotNull Long movieId,
            @NotNull Long auditoriumId,
            @NotNull Instant startsAt,
            @Min(1000) @Max(2000000) long basePrice,
            boolean active) {}

    public Long showtime(Long id, ShowtimeInput in, Long actor) {
        if (id != null
                && db.queryForList("select id from showtimes where id=? for update", Long.class, id)
                        .isEmpty()) throw ApiException.missing();
        var movie = catalog.published(in.movieId());
        if (in.startsAt().isBefore(Instant.now().plusSeconds(900)))
            throw ApiException.bad("Suất chiếu phải bắt đầu sau ít nhất 15 phút.");
        if (db.queryForList(
                        "select id from auditoriums where id=? and active=true for update",
                        Long.class,
                        in.auditoriumId())
                .isEmpty()) throw ApiException.bad("Phòng chiếu không khả dụng.");
        if (id != null
                && db.queryForObject(
                                "select count(*) from bookings where showtime_id=? and status in"
                                        + " ('HELD','PENDING','CONFIRMED','PAYMENT_REVIEW')",
                                Long.class,
                                id)
                        > 0)
            throw ApiException.bad("Không sửa suất đã có đặt ghế. Hãy tạo suất khác.");
        Instant end = in.startsAt().plusSeconds((movie.duration + 15L) * 60);
        long overlap =
                db.queryForObject(
                        "select count(*) from showtimes where auditorium_id=? and active=true and"
                                + " starts_at<? and ends_at>? and id<>?",
                        Long.class,
                        in.auditoriumId(),
                        Timestamp.from(end),
                        Timestamp.from(in.startsAt()),
                        id == null ? -1L : id);
        if (overlap > 0 && in.active())
            throw ApiException.bad(
                    "Suất chiếu bị trùng giờ trong phòng này (gồm 15 phút chuẩn bị).");
        long result =
                id == null
                        ? insert(
                                "insert into"
                                    + " showtimes(movie_id,auditorium_id,starts_at,ends_at,base_price,active)"
                                    + " values(?,?,?,?,?,?)",
                                in.movieId(),
                                in.auditoriumId(),
                                Timestamp.from(in.startsAt()),
                                Timestamp.from(end),
                                in.basePrice(),
                                in.active())
                        : id;
        if (id != null
                && db.update(
                                "update showtimes set"
                                    + " movie_id=?,auditorium_id=?,starts_at=?,ends_at=?,base_price=?,active=?"
                                    + " where id=?",
                                in.movieId(),
                                in.auditoriumId(),
                                Timestamp.from(in.startsAt()),
                                Timestamp.from(end),
                                in.basePrice(),
                                in.active(),
                                id)
                        == 0) throw ApiException.missing();
        audit.log(actor, "SHOWTIME_SAVE", "SHOWTIME", result, movie.title);
        return result;
    }

    public void deactivate(String resource, Long id, Long actor) {
        String table =
                switch (resource) {
                    case "cinemas" -> "cinemas";
                    case "auditoriums" -> "auditoriums";
                    case "showtimes" -> "showtimes";
                    default -> throw ApiException.missing();
                };
        String condition =
                switch (resource) {
                    case "cinemas" -> "a.cinema_id=?";
                    case "auditoriums" -> "a.id=?";
                    default -> "s.id=?";
                };
        db.queryForList(
                "select s.id from showtimes s join auditoriums a on a.id=s.auditorium_id where "
                        + condition
                        + " order by s.id for update",
                Long.class,
                id);
        long count =
                db.queryForObject(
                        "select count(*) from bookings b join showtimes s on s.id=b.showtime_id"
                                + " join auditoriums a on a.id=s.auditorium_id where "
                                + condition
                                + " and b.status in ('HELD','PENDING','CONFIRMED','PAYMENT_REVIEW')"
                                + " and s.ends_at>?",
                        Long.class,
                        id,
                        Timestamp.from(Instant.now()));
        if (count > 0)
            throw ApiException.bad("Có đơn vé đang hiệu lực. Không thể đóng rạp/phòng/suất này.");
        db.update("update " + table + " set active=false where id=?", id);
        audit.log(actor, "CINEMA_RESOURCE_CLOSE", resource, id, "Ngừng hoạt động");
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        db.update(
                c -> {
                    var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
                    return s;
                },
                key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
