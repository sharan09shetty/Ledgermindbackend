# LedgerMind

AI-powered personal finance tracker for Indian users. Reads bank transaction-alert
emails from Gmail, parses them into categorized transactions, and exposes dashboards,
analytics, and an AI financial advisor (Telegram bot + in-app chat).

Two sibling repos:

- **Backend** — `/Users/sharanmshetty/Documents/Ledgermindbackend` (this repo): Spring Boot 3 / Java 21
- **Frontend** — `/Users/sharanmshetty/Documents/ledgermind-frontend`: React 19 + Vite 8 + Tailwind 4 (PWA)

## Commands

Backend (run from this repo; `./gradlew` is not executable — use `sh`):

```bash
sh ./gradlew compileJava     # compile
sh ./gradlew test            # run tests
sh ./gradlew bootRun         # run (requires env: DB, Redis, Google OAuth, AWS, Telegram)
```

Frontend (run from `ledgermind-frontend`):

```bash
npm run dev                  # dev server on :5173, proxies /api → :8080
npm run build                # production build (also generates PWA service worker)
npm run lint                 # eslint — keep this clean
LM_API_TARGET=http://localhost:8081 npm run dev   # point the /api proxy elsewhere (e.g. a mock)
```

## Backend architecture

Package root: `com.ledgermind.ledgermindbackend`. Feature-package layout
(`controller/`, `service/`, `repository/`, `entity/`, `dto/` inside each feature):

- `security/` — JWT auth (`JwtAuthFilter`, `JwtService`), `SecurityConfig` (all routes
  authenticated except OAuth redirects + Telegram webhook). `SecurityUtils.currentUserId()`
  gives the authenticated user's UUID.
- `email/` — Gmail integration: `GoogleAuthController` (login + Gmail-connect OAuth flows),
  bank-specific parsers, `Transaction`/`Bank`/`RawEmail` entities, transaction CRUD.
- `analytics/` — summary/categories/merchants/transactions endpoints under `/analytics/*`.
- `telegram/` — bot webhook, link tokens (`TelegramLinkService`), and the advisor
  (`telegram/advisor/FinancialAdvisorService` + `FinancialAdvisorTools` + `RedisChatMemoryStore`).
- `chat/` — in-app advisor chat (`/chat/history`, `/chat/message`, `DELETE /chat/history`).
  `WebChatService` reuses `FinancialAdvisorTools`; `WebChatMemoryStore` is a **separate**
  Redis namespace (`ledgermind:webchat:<userId>`, 40 msgs / 168h TTL) so Telegram and web
  history never mix. Web chat messages carry epoch-millis timestamps for the UI.
- `ratelimit/` — Redis fixed-window limiter (`RateLimiterService`, fails open).
  Buckets configured in `RateLimitProperties` / `ratelimit.*` properties:
  `telegram-chat`, `telegram-cash-log`, `web-chat` (per-user).
- `queue/` — SNS/SQS pipeline for raw email processing.
- `ai/` — LLM config (Spring AI; the advisor uses the `chatChatClient` qualifier) and
  cash-transaction parsing.
- `user/` — `User` entity + `/users/status`, `/users/bank`, `/users/onboarding/complete`.

Conventions & gotchas:

- Flyway migrations in `src/main/resources/db/migration` (`V<N>__name.sql`). Hibernate
  writes explicit column lists, so a DB `DEFAULT` alone is not enough for new NOT NULL
  columns — also set a Java-side default (`@Builder.Default`, since entities use Lombok
  `@Builder` and plain field initializers are ignored by builders).
- `User.onboarded` gates the frontend onboarding wizard; set via
  `POST /users/onboarding/complete`, exposed in `UserStatusResponse`.
- `FinancialAdvisorTools` is intentionally NOT a Spring bean — one instance per request
  so `userId` stays final (prevents cross-user data leaks).
- Non-idempotent create paths hit by possibly-duplicated client calls should tolerate
  unique-constraint races (see `TelegramLinkService.generateLinkToken` retry pattern).
- Gmail OAuth callback redirects to `<frontend>/settings?gmail=connected|gmail_error=…`;
  the frontend's onboarding gate re-routes that to `/onboarding` for un-onboarded users.

## Frontend architecture

`src/` layout:

- `api/` — `axios.js` (JWT header, 401 → /login) and `endpoints.js` (all API calls).
- `context/` — `ThemeContext` (3 themes: light/dark/midnight, applied via
  `<html data-theme>` + CSS variables in `index.css`; `theme.chart.*` colors exist for
  Recharts), `ToastContext` (`useToast().toast(msg, {type})`), `DateRangeContext`
  (shared Monthly / Last-30-days range incl. previous-period bounds for comparisons).
- `components/brand/Logo.jsx` — `LogoMark` (inline SVG icon) and `Logo` (lockup `<img>`,
  theme-aware; assets in `src/assets/lockup-{light,dark}.svg`). PWA icons in `public/`.
- `components/ui/` — design system: `Button`, `Modal` (bottom sheet on mobile), `Field`
  (Input/Select/Label), `Skeleton*`, `EmptyState`, `ConfirmDialog`, `DateRangePicker`,
  `SwipeableRow` (mobile swipe-left → Edit/Delete), `SetupChecklist`.
- `components/layout/` — `Layout` (sidebar/bottom-nav shell; `overflow-x-hidden` on main),
  `Sidebar` (desktop rail + mobile top bar & bottom nav), `GmailReconnectBanner`
  (only for onboarded users with a bank set), `CommandPalette` (⌘K, desktop).
- `pages/` — Login, Onboarding (6-step wizard, resumes via `lm-onboarding-step` in
  localStorage), Dashboard, Transactions, Analytics, Chat (advisor), Settings, NotFound.
- Styling: Tailwind utilities mapped to theme CSS variables (`bg-card`, `text-muted`,
  `border-border`, `bg-accent`, `text-danger`…) — never hardcode theme colors in JSX;
  chart libs use `useTheme().theme.chart/accent`.

Conventions & gotchas:

- Inline styles are avoided; everything uses the Tailwind/CSS-variable system.
- Long content inside CSS grid: grid items need `min-w-0` or they force horizontal
  overflow on mobile (this has bitten Settings and Analytics before).
- Effects that fire network calls on mount must survive React StrictMode's double
  mount: use a cancelable `setTimeout(…, 0)` in the effect with cleanup (see
  `TelegramLinkModal`) — NOT a ref guard (the request would run on the discarded
  first instance and the survivor stays pending forever).
- Mobile primary actions live at the top of page headers (no FABs); the bottom nav is
  navigation-only. Analytics intentionally has no "log transaction" action.
- QR codes are rendered locally with `qrcode.react` (never via external QR services).
- Advisor chat (`pages/Chat.jsx`): input locked while a reply is pending, local
  time-of-day greeting when history is empty or >6h stale, suggestion chips, 429 →
  friendly rate-limit bubble, history cached under the `['chat-history']` query key.
- `useQuery(['status'])` (`/users/status`) is the shared user/status source
  (name, gmailConnected, telegramLinked, bankCode, onboarded).

## Testing changes locally without the real backend

A mock API approach that has worked well: a small Node http server implementing
`/users/status`, `/banks`, `/analytics/*`, `/chat/*`, `/telegram/link-token` on :8081,
then `LM_API_TARGET=http://localhost:8081 npm run dev -- --port 5199` and drive
screenshots with `playwright-core` + installed Chrome
(`executablePath: /Applications/Google Chrome.app/Contents/MacOS/Google Chrome`).
Seed auth with `localStorage.setItem('token', 'devtoken')` via `addInitScript`.
Check `document.documentElement.scrollWidth === clientWidth` at 390px to catch
mobile horizontal overflow. Port 5173 is often occupied by the user's own dev server —
don't kill it; use another port.
