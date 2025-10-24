# --- First database schema

# --- !Ups

CREATE TABLE users(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(25) UNIQUE NOT NULL,
    email VARCHAR(128) UNIQUE NOT NULL,
    password VARCHAR(1024),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    google_id VARCHAR(1024),
    auth_provider VARCHAR(16) DEFAULT 'LOCAL',
    refresh_token VARCHAR(1024),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_username_users ON users(username);
CREATE UNIQUE INDEX idx_email_users ON users(email);

CREATE TABLE urls(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    short_code VARCHAR(15) NOT NULL,
    long_url VARCHAR(2048) NOT NULL,
    clicks INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT fk_url_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_short_code_urls ON urls(short_code);
CREATE INDEX idx_user_id_urls ON urls(user_id);

CREATE TABLE notification_types(
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO notification_types (name) VALUES ('NEWURL'), ('TRESHOLD'), ('NEWUSER');

CREATE TABLE notification_statuses(
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO notification_statuses (name) VALUES ('SUCCESS'), ('FAILURE'), ('PENDING');

CREATE TABLE notifications(
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(10),
    user_id BIGINT,
    notification_type_id INT NOT NULL,
    notification_status_id INT NOT NULL,
    message VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_status
        FOREIGN KEY (notification_status_id) REFERENCES notification_statuses(id),
    CONSTRAINT fk_notification_type
        FOREIGN KEY (notification_type_id) REFERENCES notification_types(id),
    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

# --- !Downs
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS notification_statuses;
DROP TABLE IF EXISTS notification_types;
DROP INDEX idx_short_code_urls ON urls;
DROP INDEX idx_user_id_urls ON urls;
DROP TABLE IF EXISTS urls;
DROP INDEX idx_username_users ON users;
DROP INDEX idx_email_users ON users;
DROP TABLE IF EXISTS users;
