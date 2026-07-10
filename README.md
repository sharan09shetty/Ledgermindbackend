# LedgerMind Backend

AI-powered personal-finance assistant for Indian users. Scans Gmail for bank
transaction alerts, parses them into transactions, categorizes them (learned
merchant mappings first, LLM fallback), notifies the user on Telegram, and
serves analytics to the [LedgerMind frontend](../ledgermind-frontend).

## How it works

```
Gmail (poll every 15 min)
   ↓  RawEmail saved
SNS topic → SQS queue (DLQ after 3 failures)
   ↓
Parser (per bank) → Categorizer (per-user merchant mapping → LLM fallback)
   ↓
Transaction saved → Telegram notification
                      ↺ reply corrects category → mapping learned
```

Telegram also hosts an LLM "financial advisor" chat (Spring AI tool-calling
over the analytics service, Redis-backed conversation memory) plus `/log`
for natural-language cash transactions.

## Modules

| Package     | Responsibility |
|-------------|----------------|
| `security`  | Google OAuth login → own JWT (stateless), request auth filter |
| `email`     | Gmail polling, bank email parsers, transaction processing |
| `queue`     | SNS publisher / SQS consumer between ingestion and processing |
| `ai`        | Spring AI `ChatClient` (Gemini or Ollama), categorization, cash parsing |
| `telegram`  | Webhook bot, account linking, advisor chat, notifications |
| `analytics` | Summaries, breakdowns, paginated transaction queries |
| `user`      | User status/onboarding, scan + daily-insight schedulers |

## Running locally

Prerequisites: JDK 21, Docker, a PostgreSQL database, and (optionally) a
local [Ollama](https://ollama.com) if you don't want to use Gemini.

1. Start LocalStack (SNS/SQS with DLQ) and Redis:

   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```

2. Create `src/main/resources/application-local.properties` (gitignored —
   never commit it) with your values for every `${...}` placeholder in
   `application.properties`: database credentials, Google OAuth client,
   JWT secret, Telegram bot token, AWS/LocalStack settings, and your AI
   provider config (see below).

3. Run with the `local` profile (dev/mock endpoints are only registered
   under this profile):

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

Database schema is managed by Flyway (`src/main/resources/db/migration`).
Migrations run automatically at startup; a pre-Flyway database is baselined
at V1 automatically on first run.

## AI providers & models

Two independent LLM roles, each configurable to its own provider and model —
so you can, e.g., run cheap categorization on a small model and the chat
advisor on a stronger one:

| Role             | What it does                                | Provider prop                | Model prop                |
|------------------|---------------------------------------------|------------------------------|---------------------------|
| Categorization   | Email/cash transaction parsing & tagging    | `ai.categorization.provider` | `ai.categorization.model` |
| Chat             | Telegram financial advisor (tool-calling)   | `ai.chat.provider`           | `ai.chat.model`           |

Provider is one of `openai`, `gemini`, `ollama`. Both providers default to
`AI_PROVIDER` (fallback `gemini`). Leave a model blank to use that provider's
default (`spring.ai.<provider>.chat.options.model`). Only configured providers
get a client — selecting a provider without its API key fails fast at startup.

Example (categorize cheaply on OpenAI mini, chat on the full model):

```properties
AI_CATEGORIZATION_PROVIDER=openai
AI_CATEGORIZATION_MODEL=gpt-4o-mini
AI_CHAT_PROVIDER=openai
AI_CHAT_MODEL=gpt-4o
OPENAI_API_KEY=sk-...
```

## Rate limiting

The user-facing LLM paths (Telegram chat and `/log` cash entry) are rate
limited **per user** via Redis, so one user can't exhaust LedgerMind's model
credits. Defaults (all configurable):

| Action        | Property prefix               | Default        |
|---------------|-------------------------------|----------------|
| Advisor chat  | `ratelimit.telegram-chat`     | 20 / minute    |
| Cash `/log`   | `ratelimit.telegram-cash-log` | 15 / minute    |

The limiter fails **open**: if Redis is unreachable, requests are allowed
rather than blocking users during an outage.

## Telegram webhook

Register the webhook with a secret so the backend can verify calls really
come from Telegram (`telegram.webhook-secret` / `TELEGRAM_WEBHOOK_SECRET`
must match):

```bash
curl "https://api.telegram.org/bot<TOKEN>/setWebhook" \
  -d "url=https://<your-domain>/telegram/webhook" \
  -d "secret_token=<TELEGRAM_WEBHOOK_SECRET>"
```

If the property is empty, verification is skipped — acceptable only in local
dev against the mock Telegram controller.

## Tests

```bash
./gradlew test
```

Unit tests (JWT, email parsing) run without any environment setup, which is
what CI (`.github/workflows/test.yml`) executes on every push.
