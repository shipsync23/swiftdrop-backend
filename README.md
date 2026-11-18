# SwiftDrop Backend

Spring Boot 3 (Java 21) API and worker for the SwiftDrop Parcel Delivery & Courier Management System.
Group 14 – ShipSync.

## Prerequisites
- JDK 21
- Docker Desktop
- IntelliJ IDEA (bundled Maven is enough)

## Run locally

1. Copy the environment file:
   ```
   copy .env.example .env      (Windows)
   cp .env.example .env        (macOS/Linux)
   ```
2. Start the infrastructure:
   ```
   docker compose up -d postgres rabbitmq mailhog
   ```
3. Open the project in IntelliJ and run `SwiftDropApplication`.
   Flyway creates all tables and loads demo data on the first start.
4. Check it works:
   - http://localhost:8080/api/v1/ping
   - http://localhost:8080/actuator/health

## Local tools
| Tool | URL | Login |
|---|---|---|
| RabbitMQ UI | http://localhost:15672 | values from `.env` |
| MailHog (emails) | http://localhost:8025 | – |

## Demo accounts
All use password `Password@123`.

| Email | Role |
|---|---|
| admin@swiftdrop.lk | Admin |
| dispatcher@swiftdrop.lk | Dispatcher |
| courier1@swiftdrop.lk | Courier (Colombo) |
| courier2@swiftdrop.lk | Courier (Gampaha) |
| customer@swiftdrop.lk | Customer |

## Project structure
```
src/main/java/com/swiftdrop
├── common/          shared code: exceptions, web helpers
├── auth/            (phase 2) login, JWT, Google OAuth
├── parcel/          (phase 3) parcels, tracking, status
├── pricing/         (phase 3) fee calculation (Strategy pattern)
├── merchant/        (phase 5) merchant API and approval
├── notification/    (phase 6) email / mock SMS consumer
└── webhook/         (phase 6) merchant webhook dispatcher
src/main/resources/db/migration   Flyway SQL migrations
```

## Tests and coverage

Docker Desktop must be running (integration tests start their own PostgreSQL and RabbitMQ containers).

```
mvnw verify        (or: Maven tool window -> Lifecycle -> verify)
```

- Test results: `target/surefire-reports/`
- Coverage report: open `target/site/jacoco/index.html` in a browser

| Test class | What it proves |
|---|---|
| `ParcelStatusTest` | Status lifecycle: allowed steps, no skipping, final states |
| `PricingServiceTest` | Strategy pattern: zone/weight rule, flat-rate fallback, limits |
| `StatusServiceTest` | Courier rules: not-assigned → 403, invalid transition → 409, proof/reason required, event published |
| `NotificationConsumerTest` | Idempotent consumer: duplicate event is skipped; SMS number masking |
| `WebhookSenderTest` | HMAC-SHA256 signature, encrypted webhook secret |
| `AuthorizationIntegrationTest` | 401 without token, role checks, **a courier cannot access another courier's deliveries**, customer isolation, private data hidden on public tracking, merchant API key checks |
| `EventFlowIntegrationTest` | **Status change → outbox → RabbitMQ → notification consumer → email**, and a duplicate event is processed only once |

Only unit tests (no Docker): `mvnw test -Dtest="!*IntegrationTest"`
