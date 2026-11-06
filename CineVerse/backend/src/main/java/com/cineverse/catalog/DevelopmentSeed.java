package com.cineverse.catalog;

import com.cineverse.community.*;
import com.cineverse.payments.*;
import com.cineverse.shared.*;
import com.cineverse.users.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Component
@org.springframework.core.annotation.Order(0)
@Profile({"dev", "test"})
public class DevelopmentSeed implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final CountryRepository countries;
    private final SeasonRepository seasons;
    private final EpisodeRepository episodes;
    private final SubscriptionPlanRepository plans;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String password;

    public DevelopmentSeed(
            UserRepository users,
            PasswordEncoder passwords,
            MovieRepository movies,
            GenreRepository genres,
            CountryRepository countries,
            SeasonRepository seasons,
            EpisodeRepository episodes,
            SubscriptionPlanRepository plans,
            JdbcTemplate db,
            ObjectMapper json,
            @Value("${app.demo-password}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.movies = movies;
        this.genres = genres;
        this.countries = countries;
        this.seasons = seasons;
        this.episodes = episodes;
        this.plans = plans;
        this.db = db;
        this.json = json;
        this.password = password;
    }

    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (movies.count() > 0) return;
        if (password.length() < 10) {
            LoggerFactory.getLogger(getClass())
                    .warn(
                            "DEMO_PASSWORD missing: demo accounts will not be created. Set it and"
                                    + " restart before first seed.");
        }
        List<String> names =
                List.of(
                        "Hành động",
                        "Tình cảm",
                        "Khoa học viễn tưởng",
                        "Phiêu lưu",
                        "Bí ẩn",
                        "Kỳ ảo",
                        "Hài hước",
                        "Hoạt hình",
                        "Tài liệu",
                        "Kinh dị",
                        "Chính kịch",
                        "Gia đình");
        Map<String, Genre> gs = new LinkedHashMap<>();
        for (String name : names) {
            Genre g = new Genre();
            g.name = name;
            g.slug = Text.normalize(name).replace(' ', '-');
            genres.save(g);
            gs.put(name, g);
        }
        List<Country> cs = new ArrayList<>();
        for (String name : List.of("Việt Nam", "Hoa Kỳ", "Hàn Quốc", "Nhật Bản", "Anh", "Pháp")) {
            Country c = new Country();
            c.name = name;
            c.slug = Text.normalize(name).replace(' ', '-');
            countries.save(c);
            cs.add(c);
        }
        var seed =
                json.readValue(
                        new ClassPathResource("seed/catalog.json").getInputStream(),
                        new TypeReference<List<Map<String, Object>>>() {});
        int i = 0;
        for (var x : seed) {
            Movie m = new Movie();
            m.title = (String) x.get("title");
            m.originalTitle = (String) x.get("originalTitle");
            m.slug = (String) x.get("slug");
            m.shortDescription = (String) x.get("description");
            m.synopsis =
                    m.shortDescription
                            + " Một câu chuyện hư cấu thuộc bộ sưu tập demo CineVerse, nơi mỗi"
                            + " khung hình mở ra một góc nhìn mới về tình bạn, lòng can đảm và"
                            + " những lựa chọn trong cuộc sống. Video phát trong bản demo là phim"
                            + " ngắn hoặc trailer nguồn mở của Blender Foundation, dùng để minh họa"
                            + " chức năng streaming, không phải bộ phim được mô tả.";
            m.type = (String) x.get("type");
            m.countryId = cs.get(i % cs.size()).id;
            m.releaseYear = ((Number) x.get("year")).intValue();
            m.duration = 90 + i % 48;
            m.ageRating = i % 3 == 0 ? "T16" : "T13";
            m.language = i % 3 == 0 ? "Tiếng Việt" : "Phụ đề Việt";
            m.director =
                    List.of(
                                    "Nguyễn Minh An",
                                    "Lê Hoàng Nam",
                                    "Trần Gia Linh",
                                    "Park Ji Woo",
                                    "Alex Rivera",
                                    "Mori Haru")
                            .get(i % 6);
            m.castNames =
                    List.of(
                                    "Minh Khang, Ngọc Mai, Hải Phong",
                                    "Hà Linh, Bảo Nam, Thanh Vy",
                                    "Liam Park, Hana Kim, Mai Anh")
                            .get(i % 3);
            m.tags = "demo, hư cấu, điện ảnh";
            m.poster = (String) x.get("poster");
            m.backdrop = (String) x.get("backdrop");
            m.videoFile = List.of("sintel.mp4", "bunny.mp4", "bunny-full.mp4").get(i % 3);
            m.trailerFile = "sintel.mp4";
            m.subtitleFile = "demo-vi.vtt";
            m.status = i % 2 == 0 ? "COMPLETED" : "PUBLISHED";
            m.vip = (Boolean) x.get("vip");
            m.featured = i < 5;
            m.priority = 100 - i;
            m.createdAt = Instant.now().minus(Duration.ofHours(i));
            m.updatedAt = m.createdAt;
            m.genres.add(gs.get(x.get("genre")));
            if (i % 4 == 0) m.genres.add(gs.get("Phiêu lưu"));
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
                                    + x.get("genre"));
            movies.saveAndFlush(m);
            if (m.type.equals("SERIES")) {
                int seasonCount = i % 2 == 0 ? 2 : 1;
                for (int s = 1; s <= seasonCount; s++) {
                    Season season = new Season();
                    season.movieId = m.id;
                    season.number = s;
                    season.title = "Mùa " + s;
                    seasons.saveAndFlush(season);
                    for (int ep = 1; ep <= 4; ep++) {
                        Episode e = new Episode();
                        e.seasonId = season.id;
                        e.number = ep;
                        e.title =
                                List.of("Khởi đầu", "Dấu vết", "Bước ngoặt", "Chân trời mới")
                                        .get(ep - 1);
                        e.description =
                                "Tập "
                                        + ep
                                        + " mở ra một phần mới của câu chuyện. Nội dung demo hư"
                                        + " cấu.";
                        e.duration = 25 + ep;
                        e.videoFile =
                                List.of("sintel.mp4", "bunny.mp4", "bunny-full.mp4")
                                        .get((ep - 1) % 3);
                        e.subtitleFile = "demo-vi.vtt";
                        e.vip = m.vip || ep > 2;
                        episodes.save(e);
                    }
                }
            }
            if (i < 5)
                db.update(
                        "insert into banners(movie_id,eyebrow,sort_order,active)"
                                + " values(?,?,?,true)",
                        m.id,
                        i == 0 ? "CINEVERSE SPOTLIGHT" : "BỘ SƯU TẬP NỔI BẬT",
                        i);
            i++;
        }
        for (int p = 0; p < 3; p++) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.name = List.of("VIP Tháng", "VIP Quý", "VIP Năm").get(p);
            plan.price = List.of(49000L, 129000L, 399000L).get(p);
            plan.durationDays = List.of(30, 90, 365).get(p);
            plan.description =
                    "Mở toàn bộ thư viện VIP · Xem không gián đoạn · Phụ đề Việt · Gia hạn chủ"
                            + " động";
            plan.recommended = p == 1;
            plan.sortOrder = p;
            plans.save(plan);
        }
        if (password.length() >= 10) {
            String hash = passwords.encode(password);
            for (String role : List.of("admin", "free", "vip")) {
                User u = new User();
                u.email = role + "@cineverse.local";
                u.name =
                        switch (role) {
                            case "admin" -> "Quản trị CineVerse";
                            case "vip" -> "Khán giả VIP";
                            default -> "Khán giả CineVerse";
                        };
                u.passwordHash = hash;
                u.role = role.equals("admin") ? "ADMIN" : "USER";
                u.verified = true;
                if (role.equals("vip")) u.vipUntil = Instant.now().plus(Duration.ofDays(30));
                users.saveAndFlush(u);
                db.update(
                        "insert into notifications(user_id,type,title,content,link,created_at)"
                            + " values(?,'WELCOME','Chào mừng đến CineVerse','Đây là tài khoản demo"
                            + " local. Cùng khám phá vũ trụ điện ảnh.','/kham-pha',?)",
                        u.id,
                        java.sql.Timestamp.from(Instant.now()));
                for (long movie = 1; movie <= 8; movie++) {
                    db.update(
                            "insert into"
                                + " reviews(user_id,movie_id,stars,content,created_at,updated_at)"
                                + " values(?,?,?,?,?,?)",
                            u.id,
                            movie,
                            4 + (int) ((movie + u.id) % 2),
                            "Đánh giá mẫu cho bộ dữ liệu demo. Artwork đẹp và câu chuyện gợi nhiều"
                                    + " cảm hứng.",
                            java.sql.Timestamp.from(Instant.now()),
                            java.sql.Timestamp.from(Instant.now()));
                }
                if (!role.equals("admin"))
                    db.update(
                            "insert into favorites(user_id,movie_id,created_at) values(?,1,?)",
                            u.id,
                            java.sql.Timestamp.from(Instant.now()));
            }
        }
        Map.of(
                        "siteName",
                        "CineVerse",
                        "description",
                        "Your Universe of Cinema",
                        "contactEmail",
                        "hello@cineverse.local",
                        "maintenanceMessage",
                        "")
                .forEach(
                        (k, v) ->
                                db.update(
                                        "insert into site_settings(setting_key,setting_value)"
                                                + " values(?,?)",
                                        k,
                                        v));
        LoggerFactory.getLogger(getClass())
                .info(
                        "Development seed: {} fictional movies, {} series, {} accounts; no"
                                + " fabricated revenue or views",
                        movies.count(),
                        8,
                        users.count());
    }
}
