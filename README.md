# WebAux

WebAux hosts an event website at its own address. Hosting is ₹500 a day. A customer builds the site from a template, a reviewer approves it, and the site goes live only after a verified payment.

## Layout

```text
backend/     Java 21, Spring Boot 3.5, PostgreSQL, Flyway
frontend/    React, TypeScript, Vite
```

The frontend is one application with three route groups: the customer portal (`/app`), the staff portal (`/admin`), and the public site (`/{slug}`).

## Local development

Copy the example environment and set an admin phone and password before the first boot if you want a staff account:

```bash
copy .env.example .env
```

Backend, from `backend/`, with PostgreSQL already running and the variables from `.env` exported:

```bash
mvn spring-boot:run
```

Frontend, from `frontend/`:

```bash
npm install
npm run dev
```

The Vite server proxies `/api` and `/render` to `http://localhost:8080`. Open `http://localhost:5173`.

With the OTP bypass on, the development code is accepted by `POST /api/v1/auth/otp/verify`. The code is not returned by the API. With the payment bypass on, `POST /api/v1/events/{id}/payments` writes a real `PAID` payment with gateway `BYPASS` and then publishes.

## Docker Compose

```bash
docker compose up --build
```

The site is served at `http://localhost:8088`. PostgreSQL data and uploaded/generated media survive container restarts (`pgdata` and `media` volumes). AI-generated images use the same `media` volume under `/data/media` — no extra volume is required. See `docs/ai-image-generation.md`. Set `PRISM_ADMIN_INITIAL_PHONE` and `PRISM_ADMIN_INITIAL_PASSWORD` in the environment before the first start. The password must be at least 10 characters. An existing admin is not overwritten. For Compose, set `PRISM_PUBLIC_BASE_URL` and `PRISM_API_BASE_URL` in `.env` (defaults are `http://localhost:8088`).

### Production (`https://webaux.app`)

Coolify (or any reverse proxy) can deploy the same Compose file. Point DNS and TLS at the **frontend** service (container port **8080**) and set:

```bash
SPRING_PROFILES_ACTIVE=prod
PRISM_PUBLIC_BASE_URL=https://webaux.app
PRISM_API_BASE_URL=https://webaux.app
PRISM_COOKIE_SECURE=true
JWT_SECRET=<unique secret, at least 32 characters>
PRISM_OTP_BYPASS_ENABLED=true
PRISM_PAYMENT_BYPASS_ENABLED=false
```

Use a strong `DATABASE_PASSWORD`. Redirect `www` to the apex (or the reverse) so CORS matches the single public origin. Razorpay webhooks go to `https://webaux.app/api/v1/payments/webhooks/razorpay`. In a production profile the process refuses to start if payment bypass is enabled, cookie secure is off, or `JWT_SECRET` is missing/weak/default. OTP bypass may stay on until WhatsApp is configured (`111111`).

If Coolify reports `dependency backend failed to start` / `backend is unhealthy`, open **backend → Logs**. A production misconfig exits immediately with one of:

- `PRISM_PAYMENT_BYPASS_ENABLED must be false…`
- `Set JWT_SECRET to a unique secret…`
- `Set PRISM_COOKIE_SECURE=true…`
- `PRISM_ADMIN_INITIAL_PASSWORD must be at least 10 characters.` (only when that password env is set)

Fix those Coolify env vars and redeploy. Do not leave payment bypass on or cookie secure off when `SPRING_PROFILES_ACTIVE=prod`.

## Tests

```bash
cd backend && mvn test
cd frontend && npm test
```

Backend integration tests use Testcontainers (`postgres:16-alpine`) and do not call WhatsApp or Razorpay. Unit tests cover OTP rules, pricing, webhook signatures, the production startup guard, and configuration sanitising. `PrismJourneyTest` walks registration, review, payment bypass, publishing, RSVP, extension, and expiry.

## Further reading

- [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md)
- [API_SPECIFICATION.md](API_SPECIFICATION.md)
- [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md)
- [PROJECT_STATUS.md](PROJECT_STATUS.md)
- [docs/content-moderation.md](docs/content-moderation.md)
- [docs/ui-model-selection.md](docs/ui-model-selection.md)
