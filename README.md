# Smart Email Automation (Gmail API + OAuth 2.0 + NLP)

A full-stack, portfolio-ready **Smart Email Automation** platform built with **Spring Boot**, **PostgreSQL**, **Google OAuth 2.0**, **Gmail REST API**, **Caffeine Caching**, and an **NLP Extractive Summarization** engine — complete with a responsive web dashboard UI.

---

## Key Features Implemented

### 1. OAuth 2.0 Authentication & Demo Sandbox
- **Google OAuth 2.0 Login** (`spring-boot-starter-oauth2-client`) requesting OpenID + Gmail scopes (`gmail.readonly`, `gmail.modify`, `gmail.send`).
- Automatic user provisioning/upsert in PostgreSQL (`User` entity with `googleSub`, `avatarUrl`, `oauthConnected`, `gmailHistoryId`, `lastSyncedAt`).
- Automatic OAuth 2.0 access/refresh token management via Spring Security `OAuth2AuthorizedClientManager`.
- **Portfolio Demo Mode** (`POST /api/auth/demo-login`): one-click seeded workspace with realistic Azerbaijani & English emails so reviewers can explore every feature locally without configuring Google Cloud keys.

### 2. Real Gmail API Integration & Sync Optimization
- **Incremental Sync (`historyId`)**: Subsequent syncs call Gmail's `users.me.history.list` instead of re-listing the entire mailbox, automatically falling back to bounded `messages.list` on initial sync or expired history.
- **Batch Deduplication**: Existing `gmailMessageId` records are queried in a single SQL `IN` query before calling `messages.get`, eliminating redundant Gmail API requests.
- **Bounded Parallel Fetching + Exponential Backoff**: Concurrent message hydration (`CompletableFuture` + configurable pool) with automatic retry/backoff on HTTP `429` / `5xx`.
- **Two-Way Label Sync**: Push detected smart labels back to Gmail as custom Gmail labels (`POST /api/gmail/{emailId}/push-labels`).

### 3. Intelligent Email Automation Pipeline
- **Smart Categorization** (`EmailCategorizationService`): Sender heuristics + weighted subject/body scoring across `PRIMARY`, `WORK`, `PROMOTIONS`, `SOCIAL`, `UPDATES`, and `SPAM` with pre-compiled regex patterns.
- **Keyword-Based Labeling** (`EmailLabelingService` + `LabelRule` entity): Bilingual (Azerbaijani + English) rules (`URGENT`, `INVOICE`, `MEETING`, `PROJECT`, `SECURITY`, `SUPPORT`, `HR`, `FINANCE`) plus persistent user-defined custom rules.
- **NLP Extractive Summarization** (`EmailSummarizationService`): Boilerplate stripping, stop-word filtering, TF salience, lead-position & action-intent scoring, key takeaways extraction, and compression metrics persisted in PostgreSQL.

### 4. Database, Caching & Performance Optimization
- **PostgreSQL Indexing & Batching**: Composite indexes on `(user_id, folder, created_at)`, `(user_id, category)`, `(user_id, is_starred)`, and unique `(user_id, gmail_message_id)`, plus Hibernate JDBC batch inserts/updates.
- **Caffeine TTL Cache** (`CacheConfig`): Caches hot read endpoints (`sidebarSummary`, `categorizedEmails`, `userLabels`, `emailSummary`, `labelRules`) with automatic eviction on mutations.
- **Live Optimization Telemetry** (`GET /api/metrics/performance`): Tracks cache hit/miss ratios, Gmail API calls saved via incremental sync & deduplication, and batch throughput.

### 5. Modern Web Dashboard UI
- Served directly at `http://localhost:8090/`:
  - Google OAuth 2.0 sign-in + instant Portfolio Demo Workspace
  - Folder navigation (`Inbox`, `Sent`, `Draft`, `Starred`, `Trash`), Smart Categories, and Keyword Label filters
  - Full-text email search, Compose modal, Custom Label Rule manager
  - Email detail inspector with NLP summary card, action items, compression bar, and Gmail label push
  - Live Performance & API Optimization metrics modal

---

## Project Architecture

```text
src/main/java/org/example/gmailapplication/
├── config/
│   ├── CacheConfig.java              <- Caffeine caches + hit/miss metrics
│   ├── SecurityConfig.java           <- Spring Security + Google OAuth 2.0 + SPA entry
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java           <- /api/auth/me, demo-login, logout
│   ├── EmailController.java          <- Email CRUD, search, smart pipeline, custom rules
│   ├── GmailSyncController.java      <- /api/gmail/sync, status, push-labels
│   └── MetricsController.java        <- /api/metrics/performance
├── entity/
│   ├── Email.java                    <- Indexed table, gmailMessageId, persisted takeaways
│   ├── LabelRule.java                <- Persistent custom keyword rules
│   └── User.java                     <- OAuth 2.0 profile & Gmail historyId state
├── services/
│   ├── CustomOAuth2UserService.java
│   ├── DemoDataService.java
│   ├── EmailCategorizationService.java
│   ├── EmailLabelingService.java
│   ├── EmailService.java
│   ├── EmailSummarizationService.java
│   ├── GmailApiClient.java           <- RestClient + retry/backoff for Gmail REST API
│   ├── GmailSyncService.java         <- Incremental historyId sync + deduplication
│   └── PerformanceMetricsService.java
└── src/main/resources/static/        <- Portfolio Web UI (index.html, css/, js/)
```

---

## Setup & Running Locally

### 1. Prerequisites
- Java 17+
- PostgreSQL 14+ (or H2 test profile for unit/integration tests)

### 2. Configure Environment
Copy `.env.example` and `src/main/resources/application.properties.example`:
```bash
cp .env.example .env
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

To enable real **Google OAuth 2.0 + Gmail API**:
1. Create a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable **Gmail API**.
3. Configure OAuth Consent Screen and create **OAuth 2.0 Client ID (Web application)**.
4. Add Authorized Redirect URI:
   ```text
   http://localhost:8090/login/oauth2/code/google
   ```
5. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables (or in `application.properties`).

### 3. Run the Application & Tests
```bash
./gradlew bootRun
./gradlew test
```

Open in browser:
- **Web UI**: `http://localhost:8090/`
- **Swagger / OpenAPI**: `http://localhost:8090/swagger-ui.html`
