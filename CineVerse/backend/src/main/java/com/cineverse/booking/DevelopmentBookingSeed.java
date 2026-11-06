package com.cineverse.booking;

import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Component
@Profile({"dev", "test"})
@Order(10)
public class DevelopmentBookingSeed implements ApplicationRunner {
    private final JdbcTemplate db;

    public DevelopmentBookingSeed(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional
    public void run(ApplicationArguments args) {
        db.update(
                "update movies set synopsis=concat(short_description,' Bộ phim hư cấu trong bộ dữ"
                    + " liệu đồ án bán vé CineVerse. Chọn rạp, suất chiếu và ghế để trải nghiệm"
                    + " luồng đặt vé. Trailer minh họa sử dụng media nguồn mở, không phải nội dung"
                    + " phim được mô tả.') where tags like '%demo%' and synopsis like '%Video phát"
                    + " trong bản demo%'");
        if (db.queryForObject("select count(*) from cinemas", Long.class) > 0) return;
        // Existing development catalog is retained; all titles are now cinema films.
        db.update(
                "update movies set type='MOVIE',vip=false,video_file=case when"
                    + " video_file='steel.mp4' then 'bunny-full.mp4' else video_file end where tags"
                    + " like '%demo%'");
        db.update(
                "insert into cinemas(name,city,address,active) values('CineVerse Landmark','TP. Hồ"
                        + " Chí Minh','Tầng 5, 208 Nguyễn Hữu Cảnh, Bình Thạnh — địa điểm demo hư"
                        + " cấu',true)");
        db.update(
                "insert into cinemas(name,city,address,active) values('CineVerse Riverside','TP. Hồ"
                    + " Chí Minh','Tầng 3, 86 Bến Vân Đồn, Quận 4 — địa điểm demo hư cấu',true)");
        var cinemaIds = db.queryForList("select id from cinemas order by id", Long.class);
        for (Long cinema : cinemaIds)
            for (int room = 1; room <= 2; room++) {
                db.update(
                        "insert into auditoriums(cinema_id,name,format,active) values(?,?,?,true)",
                        cinema,
                        "Phòng " + String.format("%02d", room),
                        room == 1 ? "2D" : "DOLBY 2D");
                Long auditorium =
                        db.queryForObject(
                                "select id from auditoriums where cinema_id=? and name=?",
                                Long.class,
                                cinema,
                                "Phòng " + String.format("%02d", room));
                for (char row = 'A'; row <= 'H'; row++)
                    for (int seat = 1; seat <= 10; seat++)
                        db.update(
                                "insert into seats(auditorium_id,row_label,seat_number,kind,active)"
                                        + " values(?,?,?,?,true)",
                                auditorium,
                                String.valueOf(row),
                                seat,
                                row >= 'D' && row <= 'F' ? "VIP" : "STANDARD");
            }
        var movies =
                db.queryForList(
                        "select id from movies where deleted=false order by id limit 32",
                        Long.class);
        if (movies.isEmpty()) throw new IllegalStateException("Catalog must seed before showtimes");
        var rooms = db.queryForList("select id from auditoriums order by id", Long.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (int day = 0; day < 7; day++)
            for (int room = 0; room < rooms.size(); room++)
                for (int slot = 0; slot < 5; slot++) {
                    Long movie = movies.get((day * 20 + room * 5 + slot) % movies.size());
                    if ((room == 0 && slot == 3) || (room == 2 && slot == 4))
                        movie = movies.getFirst();
                    int duration =
                            db.queryForObject(
                                    "select duration from movies where id=?", Integer.class, movie);
                    Instant start =
                            today.plusDays(day)
                                    .atTime(10 + slot * 3, 0)
                                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                                    .toInstant();
                    db.update(
                            "insert into"
                                + " showtimes(movie_id,auditorium_id,starts_at,ends_at,base_price,active)"
                                + " values(?,?,?,?,?,true)",
                            movie,
                            rooms.get(room),
                            Timestamp.from(start),
                            Timestamp.from(start.plusSeconds((duration + 15L) * 60)),
                            slot >= 3 ? 95000 : 75000);
                }
        db.update(
                "update site_settings set setting_value='Đặt vé. Chạm cảm xúc.' where"
                        + " setting_key='description'");
    }
}
