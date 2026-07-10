-- Consolidated baseline schema, equivalent to the state previously built up
-- by hand via db/create_table_scripts.sql. Existing databases are baselined
-- at version 1 (spring.flyway.baseline-on-migrate=true), so this script only
-- runs on fresh databases.

CREATE TABLE bank (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    sender_email VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO bank(code, name, sender_email, active)
VALUES
    ('HDFC', 'HDFC Bank', 'alerts@hdfcbank.bank.in', true),
    ('ICICI', 'ICICI Bank', 'alerts@icicibank.com', true),
    ('SBI', 'State Bank of India', 'donotreply@sbibank.co.in', true);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    name TEXT,
    email VARCHAR(255) NOT NULL UNIQUE,
    last_email_sync_time TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    gmail_refresh_token TEXT,
    bank_code VARCHAR(20) REFERENCES bank(code)
);

CREATE TABLE raw_emails (
    id UUID PRIMARY KEY,
    user_id UUID,
    gmail_message_id VARCHAR(255) NOT NULL UNIQUE,
    subject TEXT,
    sender TEXT,
    body TEXT,
    received_at TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    raw_email_id UUID,
    amount NUMERIC(12,2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    category VARCHAR(50),
    counterparty VARCHAR(255),
    reference_number VARCHAR(255),
    telegram_message_id BIGINT,
    transaction_time TIMESTAMP NOT NULL,
    created TIMESTAMP,
    CONSTRAINT uk_transactions_raw_email UNIQUE (raw_email_id)
);

CREATE INDEX idx_transactions_user_time ON transactions(user_id, transaction_time);
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category);

-- Old (global) shape - migrated to per-user in V2. Kept here so V2 applies
-- identically to fresh and baselined databases.
CREATE TABLE merchant_category_mapping (
    merchant VARCHAR(255) PRIMARY KEY,
    category VARCHAR(50) NOT NULL
);

CREATE TABLE telegram_links (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    chat_id VARCHAR(64),
    link_token VARCHAR(64),
    link_token_expires_at TIMESTAMP,
    linked_at TIMESTAMP,
    created TIMESTAMP NOT NULL DEFAULT now()
);

-- Partial unique indexes so multiple NULLs are allowed (most rows will have
-- no active chat_id and/or no pending link_token at any given time).
CREATE UNIQUE INDEX uk_telegram_links_chat_id ON telegram_links(chat_id) WHERE chat_id IS NOT NULL;
CREATE INDEX idx_telegram_links_link_token ON telegram_links(link_token) WHERE link_token IS NOT NULL;
