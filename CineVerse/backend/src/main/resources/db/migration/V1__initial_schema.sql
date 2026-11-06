CREATE TABLE users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL DEFAULT 'USER', verified BOOLEAN NOT NULL DEFAULT FALSE,
 locked BOOLEAN NOT NULL DEFAULT FALSE, avatar VARCHAR(500), marketing BOOLEAN NOT NULL DEFAULT FALSE,
 vip_until DATETIME(6), session_version INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, last_login DATETIME(6), version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE auth_tokens (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 token_hash VARCHAR(64) NOT NULL UNIQUE, kind VARCHAR(20) NOT NULL,
 expires_at DATETIME(6) NOT NULL, consumed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL, family VARCHAR(36), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX idx_tokens_user ON auth_tokens(user_id,kind);
CREATE TABLE genres (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL, slug VARCHAR(100) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE countries (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL, slug VARCHAR(100) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE movies (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, original_title VARCHAR(200) NOT NULL,
 slug VARCHAR(220) NOT NULL UNIQUE, synopsis TEXT NOT NULL, short_description VARCHAR(600) NOT NULL,
 type VARCHAR(20) NOT NULL, country_id BIGINT NOT NULL, release_year INT NOT NULL,
 duration INT NOT NULL, age_rating VARCHAR(10) NOT NULL, language VARCHAR(80) NOT NULL,
 director VARCHAR(160) NOT NULL, cast_names VARCHAR(600) NOT NULL, tags VARCHAR(400) NOT NULL,
 poster VARCHAR(500) NOT NULL, backdrop VARCHAR(500) NOT NULL, video_file VARCHAR(200), trailer_file VARCHAR(200),
 subtitle_file VARCHAR(200), status VARCHAR(20) NOT NULL, vip BOOLEAN NOT NULL DEFAULT FALSE,
 featured BOOLEAN NOT NULL DEFAULT FALSE, priority INT NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, search_text TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, FOREIGN KEY(country_id) REFERENCES countries(id),
 CONSTRAINT chk_movie_duration CHECK(duration>0), CONSTRAINT chk_movie_year CHECK(release_year>=1888)
);
CREATE INDEX idx_movie_catalog ON movies(deleted,status,type,vip,release_year);
CREATE TABLE movie_genres (movie_id BIGINT NOT NULL, genre_id BIGINT NOT NULL, PRIMARY KEY(movie_id,genre_id), FOREIGN KEY(movie_id) REFERENCES movies(id), FOREIGN KEY(genre_id) REFERENCES genres(id));
CREATE TABLE seasons (id BIGINT AUTO_INCREMENT PRIMARY KEY, movie_id BIGINT NOT NULL, number INT NOT NULL, title VARCHAR(200) NOT NULL, UNIQUE(movie_id,number), FOREIGN KEY(movie_id) REFERENCES movies(id), CHECK(number>0));
CREATE TABLE episodes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, season_id BIGINT NOT NULL, number INT NOT NULL,
 title VARCHAR(200) NOT NULL, description VARCHAR(1000) NOT NULL, duration INT NOT NULL,
 video_file VARCHAR(200), subtitle_file VARCHAR(200), published BOOLEAN NOT NULL DEFAULT TRUE,
 vip BOOLEAN NOT NULL DEFAULT FALSE, UNIQUE(season_id,number), FOREIGN KEY(season_id) REFERENCES seasons(id), CHECK(number>0), CHECK(duration>0)
);
CREATE TABLE favorites (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, movie_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, UNIQUE(user_id,movie_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(movie_id) REFERENCES movies(id));
CREATE TABLE watch_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, movie_id BIGINT NOT NULL,
 episode_id BIGINT, position_seconds DOUBLE NOT NULL, duration_seconds DOUBLE NOT NULL,
 completed BOOLEAN NOT NULL DEFAULT FALSE, updated_at DATETIME(6) NOT NULL,
 UNIQUE(user_id,movie_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(movie_id) REFERENCES movies(id), FOREIGN KEY(episode_id) REFERENCES episodes(id), CHECK(position_seconds>=0), CHECK(duration_seconds>0)
);
CREATE TABLE reviews (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, movie_id BIGINT NOT NULL,
 stars INT NOT NULL, content VARCHAR(2000) NOT NULL, hidden BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE(user_id,movie_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(movie_id) REFERENCES movies(id), CHECK(stars BETWEEN 1 AND 5)
);
CREATE TABLE comment_reports (id BIGINT AUTO_INCREMENT PRIMARY KEY, review_id BIGINT NOT NULL, user_id BIGINT NOT NULL, reason VARCHAR(500) NOT NULL, created_at DATETIME(6) NOT NULL, UNIQUE(review_id,user_id), FOREIGN KEY(review_id) REFERENCES reviews(id), FOREIGN KEY(user_id) REFERENCES users(id));
CREATE TABLE subscription_plans (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, price BIGINT NOT NULL, duration_days INT NOT NULL, description VARCHAR(600) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, recommended BOOLEAN NOT NULL DEFAULT FALSE, sort_order INT NOT NULL DEFAULT 0, CHECK(price>0), CHECK(duration_days>0));
CREATE TABLE payment_orders (
 id VARCHAR(50) PRIMARY KEY, user_id BIGINT NOT NULL, plan_id BIGINT NOT NULL,
 plan_name VARCHAR(100) NOT NULL, duration_days INT NOT NULL, amount BIGINT NOT NULL,
 provider VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, request_id VARCHAR(50) NOT NULL UNIQUE,
 provider_transaction VARCHAR(100) UNIQUE, pay_url VARCHAR(1500),
 created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL, paid_at DATETIME(6),
 failure_reason VARCHAR(600), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(plan_id) REFERENCES subscription_plans(id), CHECK(amount>0)
);
CREATE INDEX idx_order_user_date ON payment_orders(user_id,created_at);
CREATE TABLE subscription_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, order_id VARCHAR(50) NOT NULL UNIQUE, start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL, FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(order_id) REFERENCES payment_orders(id));
CREATE TABLE payment_events (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(50) NOT NULL, kind VARCHAR(40) NOT NULL, detail VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL, FOREIGN KEY(order_id) REFERENCES payment_orders(id));
CREATE TABLE notifications (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, type VARCHAR(40) NOT NULL, title VARCHAR(200) NOT NULL, content VARCHAR(1500) NOT NULL, link VARCHAR(400) NOT NULL, created_at DATETIME(6) NOT NULL, read_at DATETIME(6), FOREIGN KEY(user_id) REFERENCES users(id));
CREATE INDEX idx_notifications_recipient ON notifications(user_id,created_at);
CREATE TABLE banners (id BIGINT AUTO_INCREMENT PRIMARY KEY, movie_id BIGINT NOT NULL, eyebrow VARCHAR(100) NOT NULL, sort_order INT NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE, FOREIGN KEY(movie_id) REFERENCES movies(id));
CREATE TABLE movie_views (id BIGINT AUTO_INCREMENT PRIMARY KEY, movie_id BIGINT NOT NULL, user_id BIGINT NOT NULL, viewed_at DATETIME(6) NOT NULL, FOREIGN KEY(movie_id) REFERENCES movies(id), FOREIGN KEY(user_id) REFERENCES users(id));
CREATE INDEX idx_views_movie_date ON movie_views(movie_id,viewed_at);
CREATE TABLE email_outbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, dedup_key VARCHAR(200) NOT NULL UNIQUE,
 recipient VARCHAR(254) NOT NULL, subject VARCHAR(250) NOT NULL, template VARCHAR(40) NOT NULL,
 html_body TEXT NOT NULL, text_body TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, next_attempt DATETIME(6) NOT NULL,
 sent_at DATETIME(6), last_error VARCHAR(600)
);
CREATE INDEX idx_outbox_pending ON email_outbox(status,next_attempt);
CREATE TABLE audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT, action VARCHAR(100) NOT NULL, entity_type VARCHAR(80) NOT NULL, entity_id VARCHAR(100) NOT NULL, detail VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL, FOREIGN KEY(actor_id) REFERENCES users(id));
CREATE TABLE site_settings (setting_key VARCHAR(100) PRIMARY KEY, setting_value VARCHAR(1500) NOT NULL);
