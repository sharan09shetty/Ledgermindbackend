CREATE TABLE users (
    id UUID PRIMARY KEY,
    name TEXT,
    email VARCHAR(255)
);

CREATE TABLE raw_emails (
    id UUID PRIMARY KEY,
    gmail_message_id VARCHAR(255) UNIQUE,
    subject TEXT,
    sender TEXT,
    body TEXT,
    received_at TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    raw_email_id UUID NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    counterparty VARCHAR(255),
    reference_number VARCHAR(255),
    transaction_time TIMESTAMP NOT NULL,
	created TIMESTAMP
);

ALTER TABLE transactions
    ADD CONSTRAINT uk_transactions_raw_email
        UNIQUE (raw_email_id);

ALTER TABLE transactions
    ADD COLUMN category VARCHAR(50);

CREATE TABLE merchant_category_mapping (
    merchant VARCHAR(255) PRIMARY KEY,
    category VARCHAR(50) NOT NULL
);

alter table public.users
    add column if not exists telegram_chat_id text;

CREATE TABLE bank (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    sender_email VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE users
    ADD COLUMN bank_code VARCHAR(20);

ALTER TABLE users
    ADD CONSTRAINT fk_user_bank
        FOREIGN KEY (bank_code)
            REFERENCES bank(code);

INSERT INTO bank(code, name, sender_email, active)
VALUES
    ('HDFC', 'HDFC Bank', 'alerts@hdfcbank.bank.in', true),
    ('ICICI', 'ICICI Bank', 'alerts@icicibank.com', true),
    ('SBI', 'State Bank of India', 'donotreply@sbibank.co.in', true);

-- users: support multi-user onboarding
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS gmail_refresh_token TEXT;

-- telegram_chat_id is set during onboarding (after Gmail), so it can't be NOT NULL anymore
ALTER TABLE users
    ALTER COLUMN telegram_chat_id DROP NOT NULL;

-- Add telegram_message_id to transactions (needed for the cross-user fix)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS telegram_message_id BIGINT;

-- Index for the analytics queries - makes date-range scans fast
CREATE INDEX IF NOT EXISTS idx_transactions_user_time
    ON transactions(user_id, transaction_time);

CREATE INDEX IF NOT EXISTS idx_transactions_user_category
    ON transactions(user_id, category);

ALTER TABLE transactions ALTER COLUMN raw_email_id DROP NOT NULL;


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

-- Backfill: every user that already has a linked Telegram chat gets a row
-- carrying that same chat_id over, so existing links keep working.
INSERT INTO telegram_links (id, user_id, chat_id, linked_at, created)
SELECT gen_random_uuid(), id, telegram_chat_id, now(), now()
FROM users
WHERE telegram_chat_id IS NOT NULL;

-- users.telegram_chat_id is no longer read or written by the app.
ALTER TABLE users DROP COLUMN telegram_chat_id;
