-- ============================================================================
-- NetBank Relational Database Schema (MySQL 8.0+)

-- ============================================================================

CREATE DATABASE IF NOT EXISTS netbank
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE netbank;

-- 1. users table
CREATE TABLE IF NOT EXISTS users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    full_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('CUSTOMER', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    status        ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. accounts table
CREATE TABLE IF NOT EXISTS accounts (
    account_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    balance        DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    account_type   ENUM('SAVINGS', 'CURRENT') NOT NULL DEFAULT 'SAVINGS',
    status         ENUM('ACTIVE', 'FROZEN', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_accounts_balance_non_negative
        CHECK (balance >= 0.00)
) ENGINE=InnoDB;

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

-- 3. transactions table
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id   VARCHAR(32) PRIMARY KEY,
    from_account     VARCHAR(20) NULL,
    to_account       VARCHAR(20) NULL,
    transaction_type ENUM('DEPOSIT', 'WITHDRAW', 'TRANSFER') NOT NULL,
    amount           DECIMAL(15, 2) NOT NULL,
    timestamp        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status           ENUM('SUCCESS', 'FAILED') NOT NULL DEFAULT 'SUCCESS',
    description      VARCHAR(255) NULL,
    CONSTRAINT fk_tx_from_account
        FOREIGN KEY (from_account) REFERENCES accounts(account_number)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_tx_to_account
        FOREIGN KEY (to_account) REFERENCES accounts(account_number)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_tx_positive_amount
        CHECK (amount > 0.00)
) ENGINE=InnoDB;

CREATE INDEX idx_tx_from_account ON transactions(from_account);
CREATE INDEX idx_tx_to_account ON transactions(to_account);
CREATE INDEX idx_tx_timestamp ON transactions(timestamp);

-- 4. login_logs table
CREATE TABLE IF NOT EXISTS login_logs (
    log_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    login_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMP NULL DEFAULT NULL,
    ip_address  VARCHAR(64) NOT NULL,
    CONSTRAINT fk_login_logs_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_login_logs_user_id ON login_logs(user_id);

