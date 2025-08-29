# --- First database schema

# --- !Ups

CREATE TABLE urls(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(15) NOT NULL UNIQUE,
    long_url VARCHAR(2048) NOT NULL,
    clicks INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_short_code_urls ON urls(short_code);

CREATE TABLE notifications(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(10) NOT NULL,
    notificationType ENUM ('NEWURL', 'TRESHOLD'),
    message VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

# --- !Downs
DROP INDEX idx_short_code_urls ON urls;
DROP TABLE IF EXISTS urls;

DROP TABLE IF EXISTS notifications;
