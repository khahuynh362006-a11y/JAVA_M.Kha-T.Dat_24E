CREATE TABLE cinemas (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(150) NOT NULL,
 city VARCHAR(100) NOT NULL, address VARCHAR(500) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE auditoriums (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, cinema_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL, format VARCHAR(30) NOT NULL DEFAULT '2D', active BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(cinema_id,name), FOREIGN KEY(cinema_id) REFERENCES cinemas(id)
);
CREATE TABLE seats (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, auditorium_id BIGINT NOT NULL,
 row_label VARCHAR(5) NOT NULL, seat_number INT NOT NULL, kind VARCHAR(20) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(auditorium_id,row_label,seat_number),
 FOREIGN KEY(auditorium_id) REFERENCES auditoriums(id), CHECK(seat_number>0)
);
CREATE TABLE showtimes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, movie_id BIGINT NOT NULL, auditorium_id BIGINT NOT NULL,
 starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL, base_price BIGINT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, FOREIGN KEY(movie_id) REFERENCES movies(id),
 FOREIGN KEY(auditorium_id) REFERENCES auditoriums(id), CHECK(base_price>0), CHECK(ends_at>starts_at)
);
CREATE INDEX idx_showtime_movie_date ON showtimes(movie_id,starts_at,active);
CREATE INDEX idx_showtime_room_date ON showtimes(auditorium_id,starts_at,ends_at);
CREATE TABLE bookings (
 id VARCHAR(50) PRIMARY KEY, user_id BIGINT NOT NULL, showtime_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL, total_amount BIGINT NOT NULL,
 hold_until DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL, confirmed_at DATETIME(6),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(showtime_id) REFERENCES showtimes(id), CHECK(total_amount>0)
);
CREATE INDEX idx_booking_user_date ON bookings(user_id,created_at);
CREATE INDEX idx_booking_hold ON bookings(status,hold_until);
CREATE TABLE booking_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, booking_id VARCHAR(50) NOT NULL, seat_id BIGINT NOT NULL,
 seat_label VARCHAR(15) NOT NULL, seat_kind VARCHAR(20) NOT NULL, price BIGINT NOT NULL,
 UNIQUE(booking_id,seat_id), FOREIGN KEY(booking_id) REFERENCES bookings(id), FOREIGN KEY(seat_id) REFERENCES seats(id), CHECK(price>0)
);
CREATE TABLE seat_reservations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, showtime_id BIGINT NOT NULL, seat_id BIGINT NOT NULL,
 booking_id VARCHAR(50) NOT NULL, status VARCHAR(20) NOT NULL,
 UNIQUE(showtime_id,seat_id), FOREIGN KEY(showtime_id) REFERENCES showtimes(id),
 FOREIGN KEY(seat_id) REFERENCES seats(id), FOREIGN KEY(booking_id) REFERENCES bookings(id)
);
CREATE TABLE tickets (
 id VARCHAR(50) PRIMARY KEY, booking_id VARCHAR(50) NOT NULL, seat_id BIGINT NOT NULL,
 code VARCHAR(100) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, issued_at DATETIME(6) NOT NULL, used_at DATETIME(6),
 UNIQUE(booking_id,seat_id), FOREIGN KEY(booking_id) REFERENCES bookings(id), FOREIGN KEY(seat_id) REFERENCES seats(id)
);
ALTER TABLE payment_orders MODIFY COLUMN plan_id BIGINT NULL;
ALTER TABLE payment_orders ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'MEMBERSHIP_LEGACY';
ALTER TABLE payment_orders ADD COLUMN booking_id VARCHAR(50) UNIQUE;
ALTER TABLE payment_orders ADD CONSTRAINT fk_payment_booking FOREIGN KEY(booking_id) REFERENCES bookings(id);
