CREATE TABLE payos_links (
 order_code BIGINT PRIMARY KEY,
 order_id VARCHAR(50) NOT NULL UNIQUE,
 payment_link_id VARCHAR(100) UNIQUE,
 created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES payment_orders(id)
);
