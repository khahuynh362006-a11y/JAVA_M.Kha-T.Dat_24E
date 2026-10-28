-- Cinema MVP baseline, MySQL >= 8.0.16 (tested target: 8.4).
-- Select an EMPTY database explicitly before importing. No CREATE/DROP DATABASE,
-- no USE, no destructive reset, no IF NOT EXISTS masking an incompatible schema.
-- Proposed policies/states: see docs/database.md; not approval of D-01..D-12.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET SESSION time_zone = '+07:00';
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NULL COMMENT 'BCrypt; NULL allowed only for inactive/no-login account',
    status VARCHAR(16) NOT NULL DEFAULT 'INACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email),
    CONSTRAINT ck_users_identity CHECK (CHAR_LENGTH(TRIM(username)) > 0 AND CHAR_LENGTH(TRIM(email)) > 0 AND CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_users_password CHECK (status = 'INACTIVE' OR (password_hash IS NOT NULL AND CHAR_LENGTH(password_hash) >= 60))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_code (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('ADMIN','STAFF')),
    CONSTRAINT ck_roles_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY ix_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE genres (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_genres_name (name),
    CONSTRAINT ck_genres_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movies (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    duration_minutes SMALLINT UNSIGNED NOT NULL,
    minimum_age TINYINT UNSIGNED NOT NULL DEFAULT 0,
    poster_url VARCHAR(2048) NULL,
    trailer_url VARCHAR(2048) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_movies_code (code),
    KEY ix_movies_status_title (status,title),
    KEY ix_movies_title (title),
    CONSTRAINT ck_movies_identity CHECK (CHAR_LENGTH(TRIM(code)) > 0 AND CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_movies_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_movies_age CHECK (minimum_age BETWEEN 0 AND 18),
    CONSTRAINT ck_movies_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movie_genres (
    movie_id BIGINT UNSIGNED NOT NULL,
    genre_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (movie_id,genre_id),
    KEY ix_movie_genres_genre (genre_id,movie_id),
    CONSTRAINT fk_movie_genres_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_movie_genres_genre FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rooms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_rooms_code (code),
    CONSTRAINT ck_rooms_identity CHECK (CHAR_LENGTH(TRIM(code)) > 0 AND CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_rooms_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seats (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    row_label VARCHAR(4) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    seat_number SMALLINT UNSIGNED NOT NULL,
    seat_type VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uq_seats_room_code (room_id,code),
    UNIQUE KEY uq_seats_position (room_id,row_label,seat_number),
    UNIQUE KEY uq_seats_id_room (id,room_id),
    CONSTRAINT fk_seats_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_seats_identity CHECK (CHAR_LENGTH(TRIM(code)) > 0 AND CHAR_LENGTH(TRIM(row_label)) > 0 AND seat_number > 0),
    CONSTRAINT ck_seats_type CHECK (seat_type IN ('STANDARD','VIP')),
    CONSTRAINT ck_seats_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE showtimes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movie_id BIGINT UNSIGNED NOT NULL,
    room_id BIGINT UNSIGNED NOT NULL,
    duration_minutes_snapshot SMALLINT UNSIGNED NOT NULL,
    minimum_age_snapshot TINYINT UNSIGNED NOT NULL,
    starts_at DATETIME NOT NULL COMMENT 'Vietnam local wall clock, Asia/Ho_Chi_Minh',
    ends_at DATETIME NOT NULL COMMENT 'Film end, excludes 15-minute cleanup',
    standard_price DECIMAL(12,0) NOT NULL,
    vip_price DECIMAL(12,0) NOT NULL,
    price_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Service increments on price change',
    capacity_snapshot SMALLINT UNSIGNED NULL COMMENT 'Service freezes active seat count at start; see database.md',
    status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    cancelled_at DATETIME(6) NULL,
    cancelled_by BIGINT UNSIGNED NULL,
    cancel_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_showtimes_id_room (id,room_id),
    KEY ix_showtimes_room_schedule (room_id,status,starts_at,ends_at),
    KEY ix_showtimes_day (starts_at,id),
    KEY ix_showtimes_movie_day (movie_id,starts_at),
    KEY ix_showtimes_cancel_user (cancelled_by),
    CONSTRAINT fk_showtimes_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_showtimes_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_showtimes_cancel_user FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_showtimes_duration CHECK (duration_minutes_snapshot > 0),
    CONSTRAINT ck_showtimes_age CHECK (minimum_age_snapshot BETWEEN 0 AND 18),
    CONSTRAINT ck_showtimes_time CHECK (ends_at > starts_at AND TIMESTAMPDIFF(SECOND,starts_at,ends_at) = duration_minutes_snapshot * 60 AND SECOND(starts_at) = 0 AND SECOND(ends_at) = 0),
    CONSTRAINT ck_showtimes_prices CHECK (standard_price >= 0 AND vip_price >= standard_price),
    CONSTRAINT ck_showtimes_capacity CHECK (capacity_snapshot IS NULL OR capacity_snapshot > 0),
    CONSTRAINT ck_showtimes_status CHECK (status IN ('SCHEDULED','CANCELLED')),
    CONSTRAINT ck_showtimes_cancel CHECK (
      (status = 'SCHEDULED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)
      OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancel_reason IS NOT NULL AND CHAR_LENGTH(TRIM(cancel_reason)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bookings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    showtime_id BIGINT UNSIGNED NOT NULL,
    room_id BIGINT UNSIGNED NOT NULL COMMENT 'Technical duplicate for composite FK',
    created_by BIGINT UNSIGNED NOT NULL,
    created_by_name_snapshot VARCHAR(100) NOT NULL,
    movie_title_snapshot VARCHAR(200) NOT NULL,
    room_code_snapshot VARCHAR(32) NOT NULL,
    price_version_snapshot BIGINT UNSIGNED NOT NULL,
    total_amount DECIMAL(14,0) NOT NULL,
    age_checked BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cancelled_at DATETIME(6) NULL,
    cancelled_by BIGINT UNSIGNED NULL,
    cancel_reason VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_bookings_code (code),
    UNIQUE KEY uq_bookings_context (id,showtime_id,room_id),
    KEY ix_bookings_showtime (showtime_id,room_id,status),
    KEY ix_bookings_creator (created_by,created_at,id),
    KEY ix_bookings_cancel_user (cancelled_by),
    CONSTRAINT fk_bookings_showtime_room FOREIGN KEY (showtime_id,room_id) REFERENCES showtimes(id,room_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bookings_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bookings_cancel_user FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_bookings_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_bookings_identity CHECK (CHAR_LENGTH(TRIM(code)) > 0 AND CHAR_LENGTH(TRIM(created_by_name_snapshot)) > 0 AND CHAR_LENGTH(TRIM(movie_title_snapshot)) > 0 AND CHAR_LENGTH(TRIM(room_code_snapshot)) > 0),
    CONSTRAINT ck_bookings_age_checked CHECK (age_checked IN (0,1)),
    CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMED','CANCELLED')),
    CONSTRAINT ck_bookings_cancel CHECK (
      (status = 'CONFIRMED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)
      OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancel_reason IS NOT NULL AND CHAR_LENGTH(TRIM(cancel_reason)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE booking_seats (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id BIGINT UNSIGNED NOT NULL,
    showtime_id BIGINT UNSIGNED NOT NULL,
    room_id BIGINT UNSIGNED NOT NULL COMMENT 'Links both booking and seat to same room',
    seat_id BIGINT UNSIGNED NOT NULL,
    seat_code_snapshot VARCHAR(16) NOT NULL,
    seat_type_snapshot VARCHAR(16) NOT NULL,
    unit_price DECIMAL(12,0) NOT NULL,
    booking_status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'DB-maintained copy, not editable by API',
    active_seat_id BIGINT UNSIGNED GENERATED ALWAYS AS
      (CASE WHEN booking_status = 'CONFIRMED' THEN seat_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_booking_seats_once (booking_id,seat_id),
    UNIQUE KEY uq_booking_seats_active (showtime_id,active_seat_id),
    KEY ix_booking_seats_context (booking_id,showtime_id,room_id),
    KEY ix_booking_seats_seat_room (seat_id,room_id),
    CONSTRAINT fk_booking_seats_booking FOREIGN KEY (booking_id,showtime_id,room_id) REFERENCES bookings(id,showtime_id,room_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_booking_seats_seat FOREIGN KEY (seat_id,room_id) REFERENCES seats(id,room_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_booking_seats_price CHECK (unit_price >= 0),
    CONSTRAINT ck_booking_seats_code CHECK (CHAR_LENGTH(TRIM(seat_code_snapshot)) > 0),
    CONSTRAINT ck_booking_seats_type CHECK (seat_type_snapshot IN ('STANDARD','VIP')),
    CONSTRAINT ck_booking_seats_status CHECK (booking_status IN ('CONFIRMED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Keep parent and child status consistent, including concurrent INSERT/cancel.
-- RESTRICT on all FKs: no cascading updates of a generated column's base column.
DELIMITER $$
CREATE TRIGGER bi_booking_seats_status BEFORE INSERT ON booking_seats
FOR EACH ROW
BEGIN
    DECLARE parent_status VARCHAR(16) DEFAULT NULL;
    SELECT status INTO parent_status FROM bookings WHERE id = NEW.booking_id FOR UPDATE;
    IF parent_status IS NULL OR parent_status <> 'CONFIRMED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot add seats to missing/cancelled booking';
    END IF;
    SET NEW.booking_status = parent_status;
END$$

CREATE TRIGGER bu_booking_seats_history BEFORE UPDATE ON booking_seats
FOR EACH ROW
BEGIN
    DECLARE parent_status VARCHAR(16);
    -- Nonlocking read: invoked by the parent's AFTER UPDATE trigger, whose row
    -- is already locked in this transaction. INSERT also locks that parent.
    SELECT status INTO parent_status FROM bookings WHERE id = OLD.booking_id;
    IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.booking_id <=> OLD.booking_id)
       OR NOT (NEW.showtime_id <=> OLD.showtime_id) OR NOT (NEW.room_id <=> OLD.room_id)
       OR NOT (NEW.seat_id <=> OLD.seat_id) OR NOT (NEW.unit_price <=> OLD.unit_price)
       OR NOT (BINARY NEW.seat_code_snapshot <=> BINARY OLD.seat_code_snapshot)
       OR NOT (NEW.seat_type_snapshot <=> OLD.seat_type_snapshot) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Booking seat history is immutable';
    END IF;
    IF NOT (NEW.booking_status <=> parent_status) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Seat status must match booking status';
    END IF;
    IF OLD.booking_status = 'CANCELLED' AND NEW.booking_status <> 'CANCELLED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cancelled seat history cannot be reactivated';
    END IF;
END$$

CREATE TRIGGER bd_booking_seats_history BEFORE DELETE ON booking_seats
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Keep booking seat history; cancel the booking instead';
END$$

CREATE TRIGGER bi_bookings_state BEFORE INSERT ON bookings
FOR EACH ROW
BEGIN
    IF NEW.status <> 'CONFIRMED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'New booking must be CONFIRMED';
    END IF;
END$$

CREATE TRIGGER bu_bookings_history BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF NOT (NEW.id <=> OLD.id) OR NOT (BINARY NEW.code <=> BINARY OLD.code)
       OR NOT (NEW.showtime_id <=> OLD.showtime_id) OR NOT (NEW.room_id <=> OLD.room_id)
       OR NOT (NEW.created_by <=> OLD.created_by) OR NOT (NEW.created_at <=> OLD.created_at)
       OR NOT (BINARY NEW.created_by_name_snapshot <=> BINARY OLD.created_by_name_snapshot)
       OR NOT (BINARY NEW.movie_title_snapshot <=> BINARY OLD.movie_title_snapshot)
       OR NOT (BINARY NEW.room_code_snapshot <=> BINARY OLD.room_code_snapshot)
       OR NOT (NEW.price_version_snapshot <=> OLD.price_version_snapshot)
       OR NOT (NEW.total_amount <=> OLD.total_amount) OR NOT (NEW.age_checked <=> OLD.age_checked) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Booking snapshot is immutable';
    END IF;
    IF OLD.status = 'CANCELLED' AND
       (NEW.status <> 'CANCELLED' OR NOT (NEW.cancelled_at <=> OLD.cancelled_at)
        OR NOT (NEW.cancelled_by <=> OLD.cancelled_by)
        OR NOT (BINARY NEW.cancel_reason <=> BINARY OLD.cancel_reason)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cancelled booking cannot be restored or rewritten';
    END IF;
END$$

CREATE TRIGGER au_bookings_cancel AFTER UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF OLD.status = 'CONFIRMED' AND NEW.status = 'CANCELLED' THEN
        UPDATE booking_seats SET booking_status = 'CANCELLED' WHERE booking_id = NEW.id;
    END IF;
END$$

CREATE TRIGGER bd_bookings_history BEFORE DELETE ON bookings
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Keep booking history; use cancellation';
END$$
DELIMITER ;
