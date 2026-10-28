-- Import into a NEW SEEDED test database named cinema_management_test_* ONLY.
-- Never use --force. Needs CREATE ROUTINE and ALTER ROUTINE on this test DB.
-- Expected-error tests catch exact MySQL error numbers and report PASS;
-- any unexpected result aborts, ROLLBACKs fixtures, and returns a nonzero error.
-- DDL is outside the transaction: helper routines are removed on success.
SET NAMES utf8mb4;
SET SESSION time_zone = '+07:00';

SELECT VERSION() AS mysql_version, DATABASE() AS selected_database, @@port AS port;
SELECT table_name,engine,table_collation FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;
SELECT table_name,constraint_name,constraint_type FROM information_schema.table_constraints
 WHERE table_schema=DATABASE() ORDER BY table_name,constraint_type,constraint_name;
SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
 FROM information_schema.statistics WHERE table_schema=DATABASE()
 GROUP BY table_name,index_name,non_unique ORDER BY table_name,index_name;
SELECT trigger_name,event_manipulation,event_object_table,action_timing
 FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name;

DELIMITER $$
CREATE PROCEDURE cinema_verify_assert(IN test_name VARCHAR(100), IN passed BOOLEAN)
BEGIN
    IF passed IS NULL OR NOT passed THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = test_name;
    END IF;
    SELECT test_name AS test, 'PASS' AS result;
END$$

CREATE PROCEDURE cinema_verify_error(IN test_name VARCHAR(100), IN expected_errno INT, IN statement_text TEXT)
BEGIN
    DECLARE actual_errno INT DEFAULT 0;
    SET @cinema_verify_statement = statement_text;
    PREPARE cinema_verify_stmt FROM @cinema_verify_statement;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
          GET DIAGNOSTICS CONDITION 1 actual_errno = MYSQL_ERRNO;
        EXECUTE cinema_verify_stmt;
    END;
    DEALLOCATE PREPARE cinema_verify_stmt;
    IF actual_errno <> expected_errno THEN
        SELECT test_name AS failed_test, expected_errno, actual_errno;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Constraint test returned unexpected result';
    END IF;
    SELECT test_name AS test, 'PASS' AS result, actual_errno AS rejected_with_errno;
END$$

CREATE PROCEDURE cinema_verify_all()
BEGIN
    DECLARE n INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF DATABASE() IS NULL OR LEFT(DATABASE(),23) <> 'cinema_management_test_' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Use a separate cinema_management_test_* database';
    END IF;
    SELECT COUNT(*) INTO n FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE';
    CALL cinema_verify_assert('01 exactly 11 tables',n=11);
    SELECT COUNT(*) INTO n FROM roles WHERE code IN ('ADMIN','STAFF');
    CALL cinema_verify_assert('02 ADMIN and STAFF seed',n=2);
    SELECT COUNT(*) INTO n FROM seats;
    CALL cinema_verify_assert('03 seed has 16 seats in two rooms',n=16 AND (SELECT COUNT(*) FROM rooms)=2);
    SELECT COUNT(*) INTO n FROM showtimes a JOIN showtimes b ON a.id < b.id AND a.room_id=b.room_id
      AND a.status='SCHEDULED' AND b.status='SCHEDULED'
      AND a.starts_at < DATE_ADD(b.ends_at,INTERVAL 15 MINUTE)
      AND b.starts_at < DATE_ADD(a.ends_at,INTERVAL 15 MINUTE);
    CALL cinema_verify_assert('04 seeded schedule has no overlaps including cleanup',n=0);

    START TRANSACTION;
    -- Disabled technical fixture. No plaintext password and cannot log in.
    INSERT INTO users(id,username,email,display_name,status)
      VALUES(10001,'verify_disabled','verify@example.invalid','Verify disabled','INACTIVE');
    INSERT INTO user_roles(user_id,role_id) VALUES(10001,2);
    CALL cinema_verify_error('05 duplicate email ignores case',1062,
      'INSERT INTO users(username,email,display_name) VALUES (''verify_2'',''VERIFY@example.invalid'',''V'')');
    CALL cinema_verify_error('06 duplicate role code',1062,
      'INSERT INTO roles(code,name) VALUES (''ADMIN'',''Duplicate'')');
    CALL cinema_verify_error('07 duplicate room code ignores case',1062,
      'INSERT INTO rooms(code,name) VALUES (''p01'',''Duplicate'')');
    CALL cinema_verify_error('08 same-room duplicate seat',1062,
      'INSERT INTO seats(room_id,code,row_label,seat_number) VALUES(1,''a1'',''Z'',99)');
    CALL cinema_verify_error('09 zero movie duration',3819,
      'INSERT INTO movies(code,title,duration_minutes) VALUES(''VERIFY-ZERO'',''Bad duration'',0)');
    CALL cinema_verify_error('10 negative showtime price',3819,
      'UPDATE showtimes SET standard_price=-1 WHERE id=1');
    CALL cinema_verify_error('11 invalid showtime end',3819,
      'UPDATE showtimes SET ends_at=starts_at WHERE id=1');
    CALL cinema_verify_error('12 inconsistent duration snapshot',3819,
      'UPDATE showtimes SET ends_at=DATE_ADD(starts_at,INTERVAL 1 MINUTE) WHERE id=1');
    CALL cinema_verify_error('13 duplicate movie genre',1062,
      'INSERT INTO movie_genres(movie_id,genre_id) VALUES(1,1)');
    CALL cinema_verify_error('14 nonexistent genre',1452,
      'INSERT INTO movie_genres(movie_id,genre_id) VALUES(1,999999)');
    CALL cinema_verify_error('15 room with linked data cannot be deleted',1451,
      'DELETE FROM rooms WHERE id=1');
    CALL cinema_verify_error('16 wrong booking room for showtime',1452,
      'INSERT INTO bookings(code,showtime_id,room_id,created_by,created_by_name_snapshot,movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount) VALUES(''VERIFY-WRONG-ROOM'',1,2,10001,''V'',''Film'',''P02'',0,80000)');

    INSERT INTO bookings(id,code,showtime_id,room_id,created_by,created_by_name_snapshot,movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount,age_checked)
      VALUES(10001,'VERIFY-B1',1,1,10001,'V','Historical film','P01',0,80000,1),
            (10002,'VERIFY-B2',1,1,10001,'V','Historical film','P01',0,80000,1),
            (10003,'VERIFY-B3',2,1,10001,'V','Historical film','P01',0,70000,1);
    CALL cinema_verify_error('17 wrong seat room',1452,
      'INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(10001,1,1,9,''A1'',''STANDARD'',80000)');
    CALL cinema_verify_error('18 child wrong showtime for booking',1452,
      'INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(10001,2,1,1,''A1'',''STANDARD'',80000)');
    CALL cinema_verify_error('19 negative unit price',3819,
      'INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(10001,1,1,1,''A1'',''STANDARD'',-1)');
    INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price,booking_status)
      VALUES(10001,1,1,1,'A1','STANDARD',80000,'CANCELLED');
    CALL cinema_verify_assert('20 INSERT cannot forge inactive detail',
      (SELECT booking_status FROM booking_seats WHERE booking_id=10001)='CONFIRMED');
    CALL cinema_verify_error('21 duplicate active seat across different bookings',1062,
      'INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(10002,1,1,1,''A1'',''STANDARD'',80000)');
    CALL cinema_verify_error('22 direct child cancel cannot free parent active seat',1644,
      'UPDATE booking_seats SET booking_status=''CANCELLED'' WHERE booking_id=10001');
    CALL cinema_verify_error('23 unit price history immutable',1644,
      'UPDATE booking_seats SET unit_price=1 WHERE booking_id=10001');
    CALL cinema_verify_error('24 detail history cannot be deleted',1644,
      'DELETE FROM booking_seats WHERE booking_id=10001');
    CALL cinema_verify_error('25 booking history cannot be deleted',1644,
      'DELETE FROM bookings WHERE id=10001');

    UPDATE bookings SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP(6),cancelled_by=10001,cancel_reason='Verification cancellation'
      WHERE id=10001;
    CALL cinema_verify_assert('26 parent cancellation synchronizes child and releases active key',
      (SELECT COUNT(*) FROM booking_seats WHERE booking_id=10001 AND booking_status='CANCELLED' AND active_seat_id IS NULL)=1);
    INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price)
      VALUES(10002,1,1,1,'A1','STANDARD',80000), (10003,2,1,1,'A1','STANDARD',70000);
    CALL cinema_verify_assert('27 rebooking succeeds and cancelled history remains',
      (SELECT COUNT(*) FROM booking_seats WHERE seat_id=1 AND showtime_id=1)=2);
    CALL cinema_verify_assert('28 same physical seat allowed in different showtime',
      (SELECT COUNT(*) FROM booking_seats WHERE seat_id=1 AND booking_status='CONFIRMED')=2);
    UPDATE bookings SET status='CANCELLED',cancelled_at=cancelled_at WHERE id=10001;
    CALL cinema_verify_assert('29 repeated cancel does not release new booking',
      (SELECT active_seat_id FROM booking_seats WHERE booking_id=10002)=1);
    CALL cinema_verify_error('30 cancelled booking cannot reactivate',1644,
      'UPDATE bookings SET status=''CONFIRMED'',cancelled_at=NULL,cancelled_by=NULL,cancel_reason=NULL WHERE id=10001');
    CALL cinema_verify_error('31 cannot append to cancelled booking',1644,
      'INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(10001,1,1,2,''A2'',''STANDARD'',80000)');
    CALL cinema_verify_error('32 child history cannot reactivate',1644,
      'UPDATE booking_seats SET booking_status=''CONFIRMED'' WHERE booking_id=10001');
    CALL cinema_verify_error('33 confirmed total immutable',1644,
      'UPDATE bookings SET total_amount=1 WHERE id=10002');
    UPDATE showtimes SET standard_price=90000, vip_price=110000,price_version=price_version+1 WHERE id=1;
    CALL cinema_verify_assert('34 changing current price preserves old unit and total',
      (SELECT unit_price FROM booking_seats WHERE booking_id=10002)=80000
      AND (SELECT total_amount FROM bookings WHERE id=10002)=80000);
    ROLLBACK;
    CALL cinema_verify_assert('35 test fixtures rolled back',
      (SELECT COUNT(*) FROM users WHERE id=10001)=0 AND (SELECT COUNT(*) FROM bookings)=0);
END$$
DELIMITER ;
CALL cinema_verify_all();
DROP PROCEDURE cinema_verify_all;
DROP PROCEDURE cinema_verify_error;
DROP PROCEDURE cinema_verify_assert;

-- TWO-CONNECTION TEST (manual, TEST DB ONLY; full reproducible runner in
-- database/test_database.py). Use precreated CONFIRMED booking A and B for the
-- same showtime and distinct booking IDs, plus a free seat from that room.
-- A: START TRANSACTION; INSERT booking_seats(...same showtime/seat...,booking A);
-- B: START TRANSACTION; INSERT booking_seats(...same showtime/seat...,booking B);
-- B waits for A's unique-index lock. A: COMMIT. B must fail 1062, then ROLLBACK.
-- Repeat with A: ROLLBACK instead: B may succeed. Keep cancelled history.
-- Do not run placeholder INSERTs literally; runner supplies fixture IDs.
-- Schedule overlap is a SERVICE invariant, not covered by a UNIQUE constraint.
-- Query 04 detects bad seed; it does not prevent concurrent schedule insertion.
