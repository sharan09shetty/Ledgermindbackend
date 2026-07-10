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
   JWT secret, Telegram bot token, AWS/LocalStack settings, and
   `ai.provider` (`gemini` or `ollama`).

3. Run with the `local` profile (dev/mock endpoints are only registered
   under this profile):

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```

Database schema is managed by Flyway (`src/main/resources/db/migration`).
Migrations run automatically at startup; a pre-Flyway database is baselined
at V1 automatically on first run.

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
