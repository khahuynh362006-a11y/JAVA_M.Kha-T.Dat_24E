-- DEMO ONLY, not real cinema/customer data. Run ONCE after schema in an empty DB.
-- No users/passwords, bookings or revenue records are seeded.
-- Run without --force: on failure, disconnect/ROLLBACK, inspect, do not rerun
-- against partial data. IDs deliberately fixed for readable verification.
SET NAMES utf8mb4;
SET SESSION time_zone = '+07:00';
START TRANSACTION;
INSERT INTO roles (id,code,name) VALUES (1,'ADMIN','Quản trị viên'),(2,'STAFF','Nhân viên');
INSERT INTO genres (id,name,description) VALUES
 (1,'Hành động','Thể loại minh họa'),(2,'Hoạt hình','Thể loại minh họa'),(3,'Phiêu lưu','Thể loại minh họa');
INSERT INTO movies (id,code,title,description,duration_minutes,minimum_age) VALUES
 (1,'DEMO-M01','Hành trình ánh sáng','Phim hư cấu dùng kiểm thử',120,13),
 (2,'DEMO-M02','Thành phố mây','Phim hư cấu dùng kiểm thử',90,0),
 (3,'DEMO-M03','Chuyến tàu cuối','Phim hư cấu dùng kiểm thử',105,16);
INSERT INTO movie_genres (movie_id,genre_id) VALUES (1,1),(1,3),(2,2),(2,3),(3,1);
INSERT INTO rooms (id,code,name) VALUES (1,'P01','Phòng minh họa 1'),(2,'P02','Phòng minh họa 2');
INSERT INTO seats (id,room_id,code,row_label,seat_number,seat_type,status) VALUES
 (1,1,'A1','A',1,'STANDARD','ACTIVE'),(2,1,'A2','A',2,'STANDARD','ACTIVE'),
 (3,1,'A3','A',3,'STANDARD','ACTIVE'),(4,1,'A4','A',4,'STANDARD','INACTIVE'),
 (5,1,'B1','B',1,'VIP','ACTIVE'),(6,1,'B2','B',2,'VIP','ACTIVE'),
 (7,1,'B3','B',3,'VIP','ACTIVE'),(8,1,'B4','B',4,'VIP','ACTIVE'),
 (9,2,'A1','A',1,'STANDARD','ACTIVE'),(10,2,'A2','A',2,'STANDARD','ACTIVE'),
 (11,2,'A3','A',3,'STANDARD','ACTIVE'),(12,2,'A4','A',4,'STANDARD','ACTIVE'),
 (13,2,'B1','B',1,'VIP','ACTIVE'),(14,2,'B2','B',2,'VIP','ACTIVE'),
 (15,2,'B3','B',3,'VIP','ACTIVE'),(16,2,'B4','B',4,'VIP','INACTIVE');
-- Relative future date; next same-room show begins exactly after 15-minute gap.
SET @demo_day = DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY);
INSERT INTO showtimes
 (id,movie_id,room_id,duration_minutes_snapshot,minimum_age_snapshot,starts_at,ends_at,standard_price,vip_price)
VALUES
 (1,1,1,120,13,TIMESTAMP(@demo_day,'18:00:00'),TIMESTAMP(@demo_day,'20:00:00'),80000,100000),
 (2,2,1,90,0,TIMESTAMP(@demo_day,'20:15:00'),TIMESTAMP(@demo_day,'21:45:00'),70000,90000),
 (3,3,2,105,16,TIMESTAMP(@demo_day,'18:00:00'),TIMESTAMP(@demo_day,'19:45:00'),80000,110000),
 (4,2,2,90,0,TIMESTAMP(@demo_day,'20:00:00'),TIMESTAMP(@demo_day,'21:30:00'),70000,95000);
COMMIT;
-- Create ADMIN/STAFF via the future backend provisioning flow using BCrypt.
-- Until then there is intentionally no login account. Do not use plaintext SQL.
