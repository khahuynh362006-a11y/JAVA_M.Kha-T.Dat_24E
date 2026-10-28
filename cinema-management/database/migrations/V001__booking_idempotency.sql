-- Apply ONCE after baseline schema.sql, including on existing databases.
-- No backfill or change to historical bookings. NULL identifies legacy records.
ALTER TABLE bookings
    ADD COLUMN idempotency_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN request_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uq_bookings_request (created_by,idempotency_key),
    ADD CONSTRAINT ck_bookings_request CHECK (
      (idempotency_key IS NULL AND request_hash IS NULL) OR
      (idempotency_key IS NOT NULL AND CHAR_LENGTH(idempotency_key)=36
       AND request_hash IS NOT NULL AND CHAR_LENGTH(request_hash)=64));

DELIMITER $$
CREATE TRIGGER bu_bookings_request BEFORE UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF NOT (BINARY NEW.idempotency_key <=> BINARY OLD.idempotency_key)
       OR NOT (BINARY NEW.request_hash <=> BINARY OLD.request_hash) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Booking request identity is immutable';
    END IF;
END$$
DELIMITER ;
