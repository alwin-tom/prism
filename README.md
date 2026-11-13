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

The site is served at `http://localhost:8088`. PostgreSQL data and uploaded media survive container restarts (`pgdata` and `media` volumes). Set `PRISM_ADMIN_INITIAL_PHONE` and `PRISM_ADMIN_INITIAL_PASSWORD` in the environment before the first start. The password must be at least 10 characters. An existing admin is not overwritten.

Coolify can deploy the same Compose file. Point the public URL at the frontend service and set `PRISM_PUBLIC_BASE_URL` to that origin. In a production profile (`SPRING_PROFILES_ACTIVE=prod` or `production`) both bypass flags must be false, `PRISM_COOKIE_SECURE` must be true, and `JWT_SECRET` must be a non-default secret of at least 32 bytes. The process refuses to start otherwise.

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
