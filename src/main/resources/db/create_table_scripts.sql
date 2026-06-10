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
add column if not exists telegram_chat_id text