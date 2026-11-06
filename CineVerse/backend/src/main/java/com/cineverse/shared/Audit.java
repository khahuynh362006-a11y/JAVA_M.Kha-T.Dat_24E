package com.cineverse.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class Audit {
    private final JdbcTemplate db;

    public Audit(JdbcTemplate db) {
        this.db = db;
    }

    public void log(Long actor, String action, String type, Object id, String detail) {
        db.update(
                "insert into audit_logs(actor_id,action,entity_type,entity_id,detail,created_at)"
                        + " values(?,?,?,?,?,?)",
                actor,
                action,
                type,
                String.valueOf(id),
                detail,
                Timestamp.from(Instant.now()));
    }

    public void notify(Long user, String type, String title, String content, String link) {
        db.update(
                "insert into notifications(user_id,type,title,content,link,created_at)"
                        + " values(?,?,?,?,?,?)",
                user,
                type,
                title,
                content,
                link,
                Timestamp.from(Instant.now()));
    }
}
