# AI Personal Finance Intelligence Platform — MVP Blueprint

## 1. MVP Goal

Build an autonomous AI-powered finance assistant that:

1. Reads bank transaction emails automatically
2. Extracts transaction details
3. Categorizes expenses intelligently
4. Asks user for clarification when uncertain
5. Learns from user corrections
6. Displays insights on a dashboard

The MVP should prove:

> The system can automatically understand and organize a user's financial activity with minimal manual input.

---

# 2. MVP Scope

## Included in MVP

### Email Ingestion

* Connect Gmail account using OAuth2
* Read unread bank transaction emails every few hours
* Store raw email data

### Transaction Parsing

* Extract:

    * amount
    * merchant
    * payment mode
    * timestamp
    * bank
* Support:

    * UPI
    * Credit Card
    * Debit Card

### Categorization Engine

* Rule-based categorization
* LLM fallback when confidence is low
* Categories:

    * Food
    * Travel
    * Shopping
    * Bills
    * Entertainment
    * Subscription
    * Groceries
    * Salary
    * Transfer
    * Miscellaneous

### Telegram Clarification Agent

* Ask user when category confidence is low
* Save user feedback
* Learn merchant-category mappings

### Dashboard

* Login/signup
* Monthly spending overview
* Category-wise spending
* Recent transactions
* Spending trends

### AI Insights

Examples:

* "You spent 25% more on food this month"
* "Your subscriptions increased by ₹500"
* "You spent the most on Swiggy this month"

### Monthly Report Email

* Send summary email at month-end

---

## Excluded from MVP

Build later:

* Voice assistant
* OCR receipts
* Multi-bank integrations beyond email parsing
* Investment tracking
* Budget optimization AI
* Fraud detection
* Shared family accounts
* Advanced forecasting
* Mobile app

---

# 3. Recommended Architecture

## Architecture Style

### Modular Monolith

Why:

* Faster development
* Easier debugging
* Cleaner MVP delivery
* Simpler deployment
* Easier AI experimentation

---

## Backend Modules

```text
backend/
 ├── auth/
 ├── email/
 ├── transaction/
 ├── categorization/
 ├── telegram/
 ├── analytics/
 ├── reports/
 ├── ai/
 └── common/
```

---

# 4. Recommended Tech Stack

## Frontend

### Next.js

Reason:

* Fast dashboard development
* Modern UI ecosystem
* Excellent React support

### UI Stack

* TailwindCSS
* shadcn/ui
* Recharts

---

## Backend

### Spring Boot

Reason:

* Production-grade backend
* Strong async support
* Excellent architecture for modular systems

### Spring AI

Use for:

* LLM integration
* AI categorization
* prompt management

---

## Database

### PostgreSQL

Store:

* users
* transactions
* merchant learning
* reports
* reminders

### Redis

Use for:

* caching
* session storage
* rate limiting

---

## Messaging/Event System

### Kafka

Use for async processing:

* EMAIL_RECEIVED
* TRANSACTION_PARSED
* CATEGORY_ASSIGNED
* USER_FEEDBACK_RECEIVED

---

## AI

### Gemini or OpenAI

Use for:

* fallback categorization
* AI insights
* chat interface later

---

## Notifications

### Telegram Bot API

Use for:

* clarification questions
* reminders
* alerts

---

# 5. Database Design

## users

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    telegram_id VARCHAR(255),
    created_at TIMESTAMP
);
```

---

## raw_emails

```sql
CREATE TABLE raw_emails (
    id UUID PRIMARY KEY,
    user_id UUID,
    subject TEXT,
    sender TEXT,
    body TEXT,
    received_at TIMESTAMP,
    processed BOOLEAN
);
```

---

## transactions

```sql
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID,
    amount DECIMAL,
    currency VARCHAR(10),
    merchant VARCHAR(255),
    normalized_merchant VARCHAR(255),
    category VARCHAR(100),
    payment_mode VARCHAR(100),
    bank VARCHAR(100),
    confidence_score DECIMAL,
    transaction_time TIMESTAMP,
    needs_clarification BOOLEAN,
    raw_email_id UUID
);
```

---

## merchant_learning

```sql
CREATE TABLE merchant_learning (
    id UUID PRIMARY KEY,
    merchant_pattern VARCHAR(255),
    normalized_name VARCHAR(255),
    category VARCHAR(100),
    times_confirmed INT,
    updated_at TIMESTAMP
);
```

---

## user_feedback

```sql
CREATE TABLE user_feedback (
    id UUID PRIMARY KEY,
    transaction_id UUID,
    user_response TEXT,
    corrected_category VARCHAR(100),
    created_at TIMESTAMP
);
```

---

# 6. Core Event Flow

```text
Gmail Poller
    ↓
EMAIL_RECEIVED
    ↓
Transaction Parser
    ↓
TRANSACTION_PARSED
    ↓
Categorization Engine
    ↓
Confidence High?
    ├── YES → Save Transaction
    └── NO
           ↓
Telegram Clarification Agent
           ↓
User Response
           ↓
Learning Engine Updates Mapping
```

---

# 7. MVP API Design

## Auth APIs

### POST /auth/google

Login using Google OAuth

---

## Transaction APIs

### GET /transactions

Fetch transactions

### GET /transactions/summary

Fetch analytics summary

### GET /transactions/categories

Category-wise spending

---

## Analytics APIs

### GET /analytics/monthly

Monthly insights

### GET /analytics/trends

Spending trends

---

## Telegram APIs

### POST /telegram/webhook

Receive Telegram responses

---

# 8. Categorization Strategy

## Step 1 — Rule Engine

Example:

```text
SWIGGY → Food
UBER → Travel
NETFLIX → Subscription
```

If confidence > 90%:

* auto categorize

---

## Step 2 — LLM Fallback

Prompt:

```text
Merchant: TXN KFCPHASE2BLR
Amount: 450
Previous transactions: [...]

Predict the most likely category.
```

LLM Response:

```json
{
  "category": "Food",
  "confidence": 0.78
}
```

---

## Step 3 — Human Clarification

Telegram Message:

```text
You spent ₹450 at TXN KFCPHASE2BLR.
What was this expense for?
```

Store response and update merchant learning.

---

# 9. Dashboard Pages

## 1. Overview

Widgets:

* total spending
* top category
* top merchant
* monthly trend chart
* recent transactions

---

## 2. Transactions

Features:

* searchable table
* filters
* category badges
* payment mode tags

---

## 3. Insights

Examples:

* spending increases
* recurring payments
* unusual expenses
* subscription changes

---

# 10. Suggested MVP UI Style

Build something modern.

Inspired by:

* Monarch Money
* Copilot Money
* Stripe Dashboard

Use:

* dark mode
* cards
* clean charts
* rounded layouts
* soft shadows

Avoid:

* admin-template look
* Bootstrap dashboards

---

# 11. Recommended Build Order

## Week 1

* Spring Boot setup
* PostgreSQL
* Gmail OAuth
* Email ingestion

---

## Week 2

* Transaction parsers
* Merchant normalization
* Save transactions

---

## Week 3

* Categorization engine
* Telegram bot
* Learning system

---

## Week 4

* Dashboard UI
* Analytics APIs
* Charts

---

## Week 5

* AI insights
* Monthly reports
* Docker deployment

---

# 12. Resume-Worthy Engineering Decisions

## Use:

* modular architecture
* event-driven flows
* Kafka consumers
* AI fallback pipelines
* human-in-the-loop learning
* asynchronous processing

---

# 13. Future Enhancements

## Phase 2 Features

### AI Chat

Questions like:

* "How much did I spend on food?"
* "What are my top merchants?"

---

### Predictive Analytics

Examples:

* spending forecasts
* salary runway prediction
* budget risk alerts

---

### Fraud Detection

Detect unusual spending behavior.

---

### Subscription Detection

Automatically detect recurring subscriptions.

---

### Financial Health Score

Generate:

* savings score
* spending discipline score
* recurring expense burden

---

# 14. Key MVP Differentiator

The main differentiator is NOT the dashboard.

The differentiator is:

> Autonomous transaction understanding with human-in-the-loop learning.

That is what makes this feel like a real AI product instead of a generic expense tracker.
