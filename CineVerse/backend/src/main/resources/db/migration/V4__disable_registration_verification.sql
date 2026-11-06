-- Retire queued signup verification messages without claiming email ownership.
-- Existing users remain usable even with verified=false; services enforce account lock instead.
UPDATE email_outbox
SET status='FAILED', last_error='Registration email verification no longer required'
WHERE template='VERIFY' AND status IN ('PENDING','PROCESSING');
