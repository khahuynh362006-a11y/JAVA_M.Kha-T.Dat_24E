package com.cineverse.admin;

import com.cineverse.catalog.*;
import com.cineverse.payments.*;
import com.cineverse.shared.*;
import com.cineverse.users.*;

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
public class AdminService {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final Audit audit;

    public AdminService(JdbcTemplate db, UserRepository users, Audit audit) {
        this.db = db;
        this.users = users;
        this.audit = audit;
    }

    private record Resource(String from, String columns, String search, String order) {}

    private static final Map<String, Resource> RESOURCES =
            Map.ofEntries(
                    Map.entry(
                            "cinemas",
                            new Resource("cinemas t", "t.*", "concat(t.name,' ',t.city)", "t.id")),
                    Map.entry(
                            "auditoriums",
                            new Resource(
                                    "auditoriums t join cinemas c on c.id=t.cinema_id",
                                    "t.*,c.name as cinema_name,(select count(*) from seats se where"
                                            + " se.auditorium_id=t.id) as capacity",
                                    "concat(t.name,' ',c.name)",
                                    "t.id")),
                    Map.entry(
                            "seats",
                            new Resource(
                                    "seats t join auditoriums a on a.id=t.auditorium_id join"
                                            + " cinemas c on c.id=a.cinema_id",
                                    "t.*,a.name as auditorium_name,c.name as cinema_name",
                                    "concat(c.name,' ',a.name,' ',t.row_label)",
                                    "t.id")),
                    Map.entry(
                            "showtimes",
                            new Resource(
                                    "showtimes t join movies m on m.id=t.movie_id join auditoriums"
                                            + " a on a.id=t.auditorium_id join cinemas c on"
                                            + " c.id=a.cinema_id",
                                    "t.*,m.title as movie_title,a.name as auditorium_name,c.name as"
                                            + " cinema_name",
                                    "concat(m.title,' ',c.name)",
                                    "t.starts_at")),
                    Map.entry(
                            "bookings",
                            new Resource(
                                    "bookings t join users u on u.id=t.user_id join showtimes s on"
                                        + " s.id=t.showtime_id join movies m on m.id=s.movie_id",
                                    "t.*,u.email,m.title as movie_title,(select count(*) from"
                                            + " booking_items bi where bi.booking_id=t.id) as"
                                            + " seat_count",
                                    "concat(t.id,' ',u.email,' ',m.title)",
                                    "t.created_at")),
                    Map.entry(
                            "tickets",
                            new Resource(
                                    "tickets t join booking_items bi on bi.booking_id=t.booking_id"
                                            + " and bi.seat_id=t.seat_id join bookings b on"
                                            + " b.id=t.booking_id join users u on u.id=b.user_id",
                                    "t.*,bi.seat_label,u.email",
                                    "concat(t.id,' ',u.email,' ',bi.seat_label)",
                                    "t.issued_at")),
                    Map.entry(
                            "users",
                            new Resource(
                                    "users t",
                                    "t.id,t.name,t.email,t.role,t.verified,t.locked,t.vip_until,t.created_at,t.last_login",
                                    "concat(t.name,' ',t.email)",
                                    "t.id")),
                    Map.entry(
                            "genres",
                            new Resource(
                                    "genres t",
                                    "t.*,(select count(*) from movie_genres mg where"
                                            + " mg.genre_id=t.id) as movie_count",
                                    "t.name",
                                    "t.id")),
                    Map.entry(
                            "countries",
                            new Resource(
                                    "countries t",
                                    "t.*,(select count(*) from movies m where m.country_id=t.id and"
                                            + " m.deleted=false) as movie_count",
                                    "t.name",
                                    "t.id")),
                    Map.entry(
                            "seasons",
                            new Resource(
                                    "seasons t join movies m on m.id=t.movie_id",
                                    "t.*,m.title as movie_title",
                                    "concat(t.title,' ',m.title)",
                                    "t.id")),
                    Map.entry(
                            "episodes",
                            new Resource(
                                    "episodes t join seasons s on t.season_id=s.id join movies m on"
                                            + " m.id=s.movie_id",
                                    "t.*,s.number as season_number,m.title as movie_title",
                                    "concat(t.title,' ',m.title)",
                                    "t.id")),
                    Map.entry(
                            "subscription-plans",
                            new Resource("subscription_plans t", "t.*", "t.name", "t.sort_order")),
                    Map.entry(
                            "subscriptions",
                            new Resource(
                                    "subscription_history t join users u on u.id=t.user_id",
                                    "t.*,u.name,u.email",
                                    "u.email",
                                    "t.id")),
                    Map.entry(
                            "payments",
                            new Resource(
                                    "payment_orders t join users u on u.id=t.user_id",
                                    "t.*,u.email",
                                    "concat(t.id,' ',u.email)",
                                    "t.created_at")),
                    Map.entry(
                            "comments",
                            new Resource(
                                    "reviews t join users u on u.id=t.user_id join movies m on"
                                            + " m.id=t.movie_id",
                                    "t.*,u.name,m.title as movie_title,(select count(*) from"
                                        + " comment_reports cr where cr.review_id=t.id) as reports",
                                    "concat(t.content,' ',u.name,' ',m.title)",
                                    "t.created_at")),
                    Map.entry(
                            "banners",
                            new Resource(
                                    "banners t join movies m on m.id=t.movie_id",
                                    "t.*,m.title,m.poster,m.slug",
                                    "m.title",
                                    "t.sort_order")),
                    Map.entry(
                            "notifications",
                            new Resource(
                                    "notifications t join users u on u.id=t.user_id",
                                    "t.*,u.email",
                                    "concat(t.title,' ',u.email)",
                                    "t.id")),
                    Map.entry(
                            "email-logs",
                            new Resource(
                                    "email_outbox t",
                                    "t.id,t.recipient,t.subject,t.template,t.status,t.attempts,t.created_at,t.sent_at,t.last_error",
                                    "concat(t.recipient,' ',t.subject)",
                                    "t.id")),
                    Map.entry(
                            "audit-logs",
                            new Resource(
                                    "audit_logs t left join users u on u.id=t.actor_id",
                                    "t.*,u.email as actor_email",
                                    "concat(t.action,' ',t.detail)",
                                    "t.id")));

    @Transactional(readOnly = true)
    public CatalogDtos.Paged<Map<String, Object>> list(String resource, Map<String, String> q) {
        Resource r = RESOURCES.get(resource);
        if (r == null) throw ApiException.missing();
        List<Object> args = new ArrayList<>();
        String where = " where 1=1";
        if (resource.equals("payments")) where += " and t.purpose='BOOKING'";
        if (q.containsKey("q") && !q.get("q").isBlank()) {
            where += " and lower(" + r.search + ") like ?";
            args.add(
                    "%"
                            + q.get("q")
                                    .toLowerCase(Locale.ROOT)
                                    .replace("%", "\\%")
                                    .replace("_", "\\_")
                            + "%");
        }
        if (resource.equals("payments") && q.containsKey("status") && !q.get("status").isBlank()) {
            where += " and t.status=?";
            args.add(q.get("status"));
        }
        int page = CatalogService.number(q.get("page"), 0, 0, 10000),
                size = CatalogService.number(q.get("size"), 15, 1, 100);
        long total =
                Objects.requireNonNull(
                        db.queryForObject(
                                "select count(*) from " + r.from + where,
                                Long.class,
                                args.toArray()));
        args.add(size);
        args.add(page * size);
        var rows =
                db.queryForList(
                        "select "
                                + r.columns
                                + " from "
                                + r.from
                                + where
                                + " order by "
                                + r.order
                                + " desc limit ? offset ?",
                        args.toArray());
        return new CatalogDtos.Paged<>(
                rows, total, (int) Math.ceil((double) total / size), page, size);
    }

    public record LookupInput(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 100) String slug,
            boolean active) {}

    public Long lookup(String kind, Long id, LookupInput in, Long actor) {
        String table =
                switch (kind) {
                    case "genres" -> "genres";
                    case "countries" -> "countries";
                    default -> throw ApiException.missing();
                };
        long result;
        if (id == null)
            result =
                    insert(
                            "insert into " + table + "(name,slug,active) values(?,?,?)",
                            in.name(),
                            in.slug(),
                            in.active());
        else {
            if (db.update(
                            "update " + table + " set name=?,slug=?,active=? where id=?",
                            in.name(),
                            in.slug(),
                            in.active(),
                            id)
                    == 0) throw ApiException.missing();
            result = id;
        }
        audit.log(actor, "CATALOG_SAVE", kind, result, in.name());
        return result;
    }

    public record SeasonInput(
            @NotNull Long movieId,
            @Min(1) @Max(100) int number,
            @NotBlank @Size(max = 200) String title) {}

    public Long season(Long id, SeasonInput in, Long actor) {
        String type =
                db.queryForObject(
                        "select type from movies where id=? and deleted=false",
                        String.class,
                        in.movieId());
        if (!"SERIES".equals(type)) throw ApiException.bad("Mùa chỉ được tạo cho phim bộ.");
        long result =
                id == null
                        ? insert(
                                "insert into seasons(movie_id,number,title) values(?,?,?)",
                                in.movieId(),
                                in.number(),
                                in.title())
                        : id;
        if (id != null
                && db.update(
                                "update seasons set movie_id=?,number=?,title=? where id=?",
                                in.movieId(),
                                in.number(),
                                in.title(),
                                id)
                        == 0) throw ApiException.missing();
        audit.log(actor, "SEASON_SAVE", "SEASON", result, in.title());
        return result;
    }

    public record EpisodeInput(
            @NotNull Long seasonId,
            @Min(1) @Max(10000) int number,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 1000) String description,
            @Min(1) @Max(1000) int duration,
            @Size(max = 200) String videoFile,
            @Size(max = 200) String subtitleFile,
            boolean published,
            boolean vip) {}

    public Long episode(Long id, EpisodeInput in, Long actor) {
        CatalogService.mediaName(in.videoFile(), "mp4");
        CatalogService.mediaName(in.subtitleFile(), "vtt");
        require("seasons", in.seasonId());
        if (id != null) {
            Long old =
                    db.queryForObject("select season_id from episodes where id=?", Long.class, id);
            if (!old.equals(in.seasonId()))
                throw ApiException.bad("Không thể chuyển tập đã tồn tại sang mùa khác.");
        }
        Object[] values = {
            in.seasonId(),
            in.number(),
            in.title(),
            in.description(),
            in.duration(),
            in.videoFile(),
            in.subtitleFile(),
            in.published(),
            in.vip()
        };
        long result;
        if (id == null)
            result =
                    insert(
                            "insert into"
                                + " episodes(season_id,number,title,description,duration,video_file,subtitle_file,published,vip)"
                                + " values(?,?,?,?,?,?,?,?,?)",
                            values);
        else {
            var args = new ArrayList<>(Arrays.asList(values));
            args.add(id);
            if (db.update(
                            "update episodes set"
                                + " season_id=?,number=?,title=?,description=?,duration=?,video_file=?,subtitle_file=?,published=?,vip=?"
                                + " where id=?",
                            args.toArray())
                    == 0) throw ApiException.missing();
            result = id;
        }
        audit.log(actor, "EPISODE_SAVE", "EPISODE", result, in.title());
        return result;
    }

    public record PlanInput(
            @NotBlank @Size(max = 100) String name,
            @Min(1000) @Max(50000000) long price,
            @Min(1) @Max(3650) int durationDays,
            @NotBlank @Size(max = 600) String description,
            boolean active,
            boolean recommended,
            @Min(0) int sortOrder) {}

    public Long plan(Long id, PlanInput in, Long actor) {
        long result;
        if (id == null)
            result =
                    insert(
                            "insert into"
                                + " subscription_plans(name,price,duration_days,description,active,recommended,sort_order)"
                                + " values(?,?,?,?,?,?,?)",
                            in.name(),
                            in.price(),
                            in.durationDays(),
                            in.description(),
                            in.active(),
                            in.recommended(),
                            in.sortOrder());
        else {
            if (db.update(
                            "update subscription_plans set"
                                + " name=?,price=?,duration_days=?,description=?,active=?,recommended=?,sort_order=?"
                                + " where id=?",
                            in.name(),
                            in.price(),
                            in.durationDays(),
                            in.description(),
                            in.active(),
                            in.recommended(),
                            in.sortOrder(),
                            id)
                    == 0) throw ApiException.missing();
            result = id;
        }
        audit.log(actor, "PLAN_SAVE", "PLAN", result, in.name());
        return result;
    }

    public record BannerInput(
            @NotNull Long movieId,
            @NotBlank @Size(max = 100) String eyebrow,
            @Min(0) int sortOrder,
            boolean active) {}

    public Long banner(Long id, BannerInput in, Long actor) {
        require("movies", in.movieId());
        long result;
        if (id == null)
            result =
                    insert(
                            "insert into banners(movie_id,eyebrow,sort_order,active)"
                                    + " values(?,?,?,?)",
                            in.movieId(),
                            in.eyebrow(),
                            in.sortOrder(),
                            in.active());
        else {
            if (db.update(
                            "update banners set movie_id=?,eyebrow=?,sort_order=?,active=? where"
                                    + " id=?",
                            in.movieId(),
                            in.eyebrow(),
                            in.sortOrder(),
                            in.active(),
                            id)
                    == 0) throw ApiException.missing();
            result = id;
        }
        audit.log(actor, "BANNER_SAVE", "BANNER", result, in.eyebrow());
        return result;
    }

    public void delete(String resource, Long id, Long actor) {
        switch (resource) {
            case "genres", "countries" ->
                    db.update("update " + resource + " set active=false where id=?", id);
            case "subscription-plans" ->
                    db.update("update subscription_plans set active=false where id=?", id);
            case "episodes" -> db.update("update episodes set published=false where id=?", id);
            case "seasons" -> db.update("delete from seasons where id=?", id);
            case "banners" -> db.update("delete from banners where id=?", id);
            default -> throw ApiException.forbidden();
        }
        audit.log(actor, "RESOURCE_DELETE", resource, id, "Ẩn hoặc xóa theo ràng buộc dữ liệu");
    }

    public record UserAction(
            @Pattern(regexp = "USER|ADMIN") @NotNull String role,
            boolean locked,
            @NotBlank @Size(max = 500) String reason) {}

    public void user(Long id, UserAction in, Long actor) {
        // Serialize all administrative role changes on the first user to protect the final active
        // administrator.
        Long first = db.queryForObject("select min(id) from users", Long.class);
        users.lockById(first).orElseThrow(ApiException::missing);
        var u = users.lockById(id).orElseThrow(ApiException::missing);
        if (u.role.equals("ADMIN")
                && (in.locked() || !in.role().equals("ADMIN"))
                && users.countByRoleAndLockedFalse("ADMIN") <= 1)
            throw ApiException.bad("Không thể vô hiệu hóa quản trị viên cuối cùng.");
        u.role = in.role();
        u.locked = in.locked();
        u.sessionVersion++;
        audit.log(actor, "USER_ACCESS_CHANGE", "USER", id, in.reason());
    }

    public record Moderation(boolean hidden, @NotBlank @Size(max = 500) String reason) {}

    public void moderate(Long id, Moderation in, Long actor) {
        if (db.update("update reviews set hidden=? where id=?", in.hidden(), id) == 0)
            throw ApiException.missing();
        audit.log(actor, "REVIEW_MODERATE", "REVIEW", id, in.reason());
    }

    public record Announcement(
            Long userId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 1500) String content) {}

    public void announce(Announcement in, Long actor) {
        if (in.userId() == null) {
            db.queryForList("select id from users where locked=false", Long.class)
                    .forEach(
                            id ->
                                    audit.notify(
                                            id,
                                            "ANNOUNCEMENT",
                                            in.title(),
                                            in.content(),
                                            "/thong-bao"));
        } else {
            require("users", in.userId());
            audit.notify(in.userId(), "ANNOUNCEMENT", in.title(), in.content(), "/thong-bao");
        }
        audit.log(actor, "ANNOUNCEMENT", "NOTIFICATION", in.userId(), in.title());
    }

    public void retryEmail(Long id, Long actor) {
        if (db.update(
                        "update email_outbox set"
                            + " status='PENDING',attempts=0,next_attempt=?,last_error=null where"
                            + " id=? and status='FAILED'",
                        Timestamp.from(Instant.now()),
                        id)
                == 0) throw ApiException.bad("Chỉ có thể thử lại email FAILED.");
        audit.log(actor, "EMAIL_RETRY", "EMAIL", id, "Manual retry");
    }

    public void settings(Map<String, String> input, Long actor) {
        Set<String> allowed =
                Set.of("siteName", "description", "contactEmail", "maintenanceMessage");
        input.forEach(
                (k, v) -> {
                    if (!allowed.contains(k) || v == null || v.length() > 1500)
                        throw ApiException.bad("Thiết lập không hợp lệ.");
                    int changed =
                            db.update(
                                    "update site_settings set setting_value=? where setting_key=?",
                                    v,
                                    k);
                    if (changed == 0)
                        db.update(
                                "insert into site_settings(setting_key,setting_value) values(?,?)",
                                k,
                                v);
                });
        audit.log(actor, "SETTINGS_UPDATE", "SETTINGS", "site", String.join(",", input.keySet()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(int days) {
        days = Math.max(1, Math.min(90, days));
        Instant now = Instant.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", count("select count(*) from users"));
        out.put(
                "vipUsers",
                db.queryForObject(
                        "select count(*) from users where vip_until>? and locked=false",
                        Long.class,
                        Timestamp.from(now)));
        out.put(
                "movies",
                count("select count(*) from movies where deleted=false and type='MOVIE'"));
        out.put(
                "series",
                count("select count(*) from movies where deleted=false and type='SERIES'"));
        out.put("episodes", count("select count(*) from episodes where published=true"));
        out.put("views", count("select count(*) from tickets"));
        out.put("ticketsSold", count("select count(*) from tickets"));
        out.put("cinemas", count("select count(*) from cinemas where active=true"));
        out.put(
                "showtimes",
                db.queryForObject(
                        "select count(*) from showtimes where active=true and starts_at>?",
                        Long.class,
                        Timestamp.from(now)));
        out.put(
                "momoRevenue",
                count(
                        "select coalesce(sum(amount),0) from payment_orders where purpose='BOOKING'"
                                + " and status='PAID' and provider='MOMO_SANDBOX'"));
        out.put(
                "simulationRevenue",
                count(
                        "select coalesce(sum(amount),0) from payment_orders where purpose='BOOKING'"
                                + " and status='PAID' and provider='SIMULATION'"));
        out.put(
                "payosRevenue",
                count(
                        "select coalesce(sum(amount),0) from payment_orders where purpose='BOOKING'"
                                + " and status='PAID' and provider='PAYOS'"));
        out.put(
                "sandboxRevenue",
                count(
                        "select coalesce(sum(amount),0) from payment_orders where purpose='BOOKING'"
                                + " and status='PAID' and provider in"
                                + " ('MOMO_SANDBOX','VNPAY_SANDBOX')"));
        out.put(
                "paidOrders",
                count(
                        "select count(*) from payment_orders where purpose='BOOKING' and"
                                + " status='PAID'"));
        out.put(
                "failedOrders",
                count(
                        "select count(*) from payment_orders where purpose='BOOKING' and"
                                + " status='FAILED'"));
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1);
        Timestamp cutoff = Timestamp.from(start.atStartOfDay().toInstant(ZoneOffset.UTC));
        var payments =
                db.queryForList(
                        "select cast(paid_at as date) as calendar_day,provider,sum(amount) as"
                                + " amount from payment_orders where purpose='BOOKING' and"
                                + " status='PAID' and paid_at>=? group by cast(paid_at as"
                                + " date),provider",
                        cutoff);
        var regs =
                db.queryForList(
                        "select cast(created_at as date) as calendar_day,count(*) as total from"
                                + " users where created_at>=? group by cast(created_at as date)",
                        cutoff);
        var views =
                db.queryForList(
                        "select cast(issued_at as date) as calendar_day,count(*) as total from"
                                + " tickets where issued_at>=? group by cast(issued_at as date)",
                        cutoff);
        List<Map<String, Object>> chart = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String day = start.plusDays(i).toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day);
            row.put(
                    "revenue",
                    sum(payments, day, "MOMO_SANDBOX") + sum(payments, day, "VNPAY_SANDBOX"));
            row.put("simulation", sum(payments, day, "SIMULATION"));
            row.put("payos", sum(payments, day, "PAYOS"));
            row.put("registrations", total(regs, day));
            row.put("views", total(views, day));
            chart.add(row);
        }
        out.put("chart", chart);
        out.put(
                "recentOrders",
                db.queryForList(
                        "select id,plan_name,amount,provider,status,created_at from payment_orders"
                                + " where purpose='BOOKING' order by created_at desc limit 6"));
        out.put(
                "topMovies",
                db.queryForList(
                        "select m.id,m.title,count(v.id) as views from movies m left join showtimes"
                                + " s on s.movie_id=m.id left join bookings b on b.showtime_id=s.id"
                                + " left join tickets v on v.booking_id=b.id where m.deleted=false"
                                + " group by m.id,m.title order by views desc limit 6"));
        out.put(
                "paymentStatus",
                db.queryForList(
                        "select status,count(*) as total from payment_orders where"
                                + " purpose='BOOKING' group by status"));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> payment(String id) {
        var rows =
                db.queryForList(
                        "select"
                            + " id,user_id,plan_name,amount,provider,status,request_id,provider_transaction,created_at,paid_at,expires_at,failure_reason"
                            + " from payment_orders where id=?",
                        id);
        if (rows.isEmpty()) throw ApiException.missing();
        return Map.of(
                "order",
                rows.getFirst(),
                "events",
                db.queryForList(
                        "select kind,detail,created_at from payment_events where order_id=? order"
                                + " by id",
                        id));
    }

    public String exportPayments() {
        var rows =
                db.queryForList(
                        "select id,plan_name,amount,provider,status,created_at from payment_orders"
                                + " order by created_at desc limit 10000");
        StringBuilder b =
                new StringBuilder(
                        "\ufeffMã đơn,Gói,Số tiền VND,Nhà cung cấp,Trạng thái,Thời gian UTC\r\n");
        for (var r : rows) {
            b.append(
                            String.join(
                                    ",",
                                    r.values().stream()
                                            .map(
                                                    v -> {
                                                        String s = String.valueOf(v);
                                                        if (s.matches("^[=+@-].*")) s = "'" + s;
                                                        return "\""
                                                                + s.replace("\"", "\"\"")
                                                                + "\"";
                                                    })
                                            .toList()))
                    .append("\r\n");
        }
        return b.toString();
    }

    private long insert(String sql, Object... values) {
        KeyHolder key = new GeneratedKeyHolder();
        db.update(
                c -> {
                    PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
                    return s;
                },
                key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private void require(String table, Long id) {
        if (db.queryForObject("select count(*) from " + table + " where id=?", Long.class, id) == 0)
            throw ApiException.bad("Dữ liệu liên kết không tồn tại.");
    }

    private long count(String sql) {
        return Objects.requireNonNull(db.queryForObject(sql, Long.class));
    }

    private long total(List<Map<String, Object>> rows, String day) {
        return rows.stream()
                .filter(r -> r.get("calendar_day").toString().equals(day))
                .mapToLong(r -> ((Number) r.get("total")).longValue())
                .sum();
    }

    private long sum(List<Map<String, Object>> rows, String day, String provider) {
        return rows.stream()
                .filter(
                        r ->
                                r.get("calendar_day").toString().equals(day)
                                        && provider.equals(r.get("provider")))
                .mapToLong(r -> ((Number) r.get("amount")).longValue())
                .sum();
    }
}
