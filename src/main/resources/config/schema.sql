-- ============================================================
--  Schema cho hệ thống đấu giá trực tuyến
--  Chú ý: frozen_amount lưu số tiền đang bị tạm khóa của Bidder
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(50)  PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password      VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL,
    balance       DOUBLE       DEFAULT 0,
    frozen_amount DOUBLE       DEFAULT 0
);

CREATE TABLE IF NOT EXISTS items (
    id          VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    init_price  DOUBLE       NOT NULL,
    category    VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS auction_sessions (
    auction_id          VARCHAR(50) PRIMARY KEY,
    item_id             VARCHAR(50) NOT NULL,
    seller_id           VARCHAR(50) NOT NULL,
    start_time          DATETIME    NOT NULL,
    end_time            DATETIME    NOT NULL,
    status              VARCHAR(20) NOT NULL,
    winner_id           VARCHAR(50),
    current_highest_bid DOUBLE      DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bid_transactions (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    auction_id  VARCHAR(50) NOT NULL,
    bidder_id   VARCHAR(50) NOT NULL,
    bid_amount  DOUBLE      NOT NULL,
    bid_time    DATETIME    NOT NULL
);
