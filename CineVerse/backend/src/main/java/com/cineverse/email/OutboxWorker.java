package com.cineverse.email;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
public class OutboxWorker {
    private final JdbcTemplate db;
    private final JavaMailSender mail;
    private final String from;

    public OutboxWorker(
            JdbcTemplate db, JavaMailSender mail, @Value("${app.mail-from}") String from) {
        this.db = db;
        this.mail = mail;
        this.from = from;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox-delay:5000}",
            initialDelayString = "${app.outbox-initial-delay:5000}")
    public void deliver() {
        Timestamp now = Timestamp.from(Instant.now());
        db.update(
                "update email_outbox set status='PENDING' where status='PROCESSING' and"
                        + " next_attempt<?",
                now);
        var rows =
                db.queryForList(
                        "select * from email_outbox where status='PENDING' and next_attempt<=?"
                                + " order by id limit 20",
                        now);
        for (var r : rows) {
            long id = ((Number) r.get("id")).longValue();
            int attempts = ((Number) r.get("attempts")).intValue() + 1;
            if (db.update(
                            "update email_outbox set"
                                + " status='PROCESSING',attempts=attempts+1,next_attempt=? where"
                                + " id=? and status='PENDING'",
                            Timestamp.from(Instant.now().plusSeconds(300)),
                            id)
                    != 1) continue;
            try {
                var message = mail.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from, "CineVerse");
                helper.setTo((String) r.get("recipient"));
                helper.setSubject((String) r.get("subject"));
                helper.setText((String) r.get("text_body"), (String) r.get("html_body"));
                // Stable Message-ID helps SMTP clients deduplicate retries after a crash.
                message.setHeader("X-CineVerse-Outbox", String.valueOf(id));
                mail.send(message);
                db.update(
                        "update email_outbox set status='SENT',sent_at=?,last_error=null where"
                                + " id=?",
                        Timestamp.from(Instant.now()),
                        id);
            } catch (Exception e) {
                String detail = "SMTP delivery failed (" + e.getClass().getSimpleName() + ")";
                db.update(
                        "update email_outbox set status=?,next_attempt=?,last_error=? where id=?",
                        attempts >= 5 ? "FAILED" : "PENDING",
                        Timestamp.from(
                                Instant.now().plusSeconds(Math.min(3600, 30L * (1L << attempts)))),
                        detail,
                        id);
                LoggerFactory.getLogger(getClass())
                        .warn(
                                "Outbox {} delivery attempt {} failed: {}",
                                id,
                                attempts,
                                e.getClass().getSimpleName());
            }
        }
    }
}
