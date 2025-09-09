# --- First database schema

# --- !Ups

CREATE TABLE urls(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(15) NOT NULL UNIQUE,
    long_url VARCHAR(2048) NOT NULL,
    clicks INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_short_code_urls ON urls(short_code);

CREATE TABLE notification_types(
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO notification_types (name) VALUES ('NEWURL'), ('TRESHOLD');

CREATE TABLE notifications(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(10) NOT NULL,
    notification_type_id INT NOT NULL,
    message VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_type
    FOREIGN KEY (notification_type_id) REFERENCES notification_types(id)
);

# --- !Downs
DROP INDEX idx_short_code_urls ON urls;
DROP TABLE IF EXISTS urls;

DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS notification_types;