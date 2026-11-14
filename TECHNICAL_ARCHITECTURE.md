# Technical architecture

WebAux is one Spring Boot process and one React application. There is no message broker and no Redis. PostgreSQL is the system of record, the outbox, and the lock for scheduled work.

## Modules

Packages under `com.prism`: `auth`, `user`, `category`, `template`, `event`, `revision`, `media`, `review`, `pricing`, `payment`, `hosting`, `publishing`, `rsvp`, `notification` (including `whatsapp`), `analytics`, `admin`, and `shared` (`config`, `security`, `exception`, `audit`, `api`, `util`, `jobs`).

Controllers return DTOs or maps. They do not return a customer-facing write model that a caller can publish directly. A few staff catalogue reads still serialise JPA entities (`GET /admin/categories`, templates, versions, and `GET /events/{id}/hosting`). That is recorded in `PROJECT_STATUS.md`.

## Authentication

Access JWTs live in the HttpOnly cookie `prism_access` (15 minutes). Refresh tokens are opaque, stored as SHA-256 hashes, and sent in the HttpOnly cookie `prism_refresh` (7 days, path `/api/v1/auth`). A readable `prism_csrf` cookie must be echoed as `X-CSRF-Token` on cookie-authenticated mutations. Bearer tokens skip that check so tests and non-browser clients can call the API. CSRF is also skipped for OTP request, OTP verify, staff login, refresh, logout, public routes, and the Razorpay webhook.

Customers sign in with a six-digit OTP. Staff with a password hash sign in at `POST /api/v1/auth/login`. Roles are `CUSTOMER`, `ADMIN`, and `REVIEWER`. Review actions accept either staff role. Catalogue, user, payment-list, audit, suspend, unpublish, and restore actions require `ADMIN`. A customer cannot approve their own event, and no customer route sets a site to `PUBLISHED`.

OTP codes are HMAC-SHA256 of `phone:code` using the JWT secret. They last five minutes, allow five attempts, and a resend waits 60 seconds. A phone is limited to five requests an hour and an IP to twenty. Requesting a new code expires older open codes. `PRISM_OTP_BYPASS_ENABLED` accepts `111111` and audits `OTP_BYPASS`. A real code still works. The bypass value is not included in responses. OTP bypass is allowed under `prod` for temporary access until WhatsApp is configured; turn it off when live OTP delivery is ready.

## Events

Statuses: `DRAFT`, `SUBMITTED`, `IN_REVIEW`, `CHANGES_REQUESTED`, `APPROVED`, `REJECTED`, `PUBLISHED`, `EXPIRED`, `SUSPENDED`.

Only a `DRAFT` revision is editable. Saving over any other revision clones a new draft. A revision in `SUBMITTED` or `IN_REVIEW` blocks further edits. Submitted revisions are not updated in place. While a published site has a newer revision in review, the public page keeps the published revision.

Approval of a site that already has covering hosting publishes the new revision without another charge. The first publication, and republication after expiry, require a verified payment. The slug cannot change after the first publish.

Configuration JSON is allow-listed: known section types, known property keys, a fixed font list, hex colours, placeholder paths or media ids, and Google Maps hosts. `<`, `>` and `javascript:` are rejected. The React renderer only mounts registered components and prints customer text as text.

## Money, hosting, publishing

Amounts are integer paise. The default rate is 50000 paise a day, from `PRISM_DAILY_HOSTING_PRICE_PAISE`. Quotes are computed on the server.

`PaymentService` chooses `PaymentBypassService` behaviour or `RazorpayPaymentService` behaviour. Bypass writes a `payments` row with gateway `BYPASS`, null gateway ids, status `PAID`, then runs `PaymentCompletionService`, which opens a hosting period and publishes. It does not call Razorpay. Razorpay orders are created server-side. `payment.captured` webhooks are verified with HMAC-SHA256 and stored in `payment_webhook_events` with a unique `(gateway, event_id)`. A payment that already has a hosting period is ignored. Frontend payment status is never trusted. If both integrations are enabled, Razorpay is used unless the request method is `BYPASS`. If neither is enabled, payment returns 503. The payment bypass is refused at startup on a production profile.

Hosting periods store `start_at` and `end_at` as UTC instants. An extension bought before the current end is `SCHEDULED` from that end. An extension after expiry starts at the new activation. The expiry job marks due `ACTIVE` periods `EXPIRED`, promotes a covering `SCHEDULED` period, and otherwise expires the publication and the event. Suspended sites also expire when hosting ends. Unpublish sets the publication to `UNPUBLISHED` and the event to `APPROVED`; the public API then 404s. Suspend shows a paused page and does not include the configuration. Restore republishes when a period still covers now.

WhatsApp sends go through `WhatsAppService` / `MetaWhatsAppService` and the `outbox_events` table, except the OTP send, which is synchronous. A failed notice does not roll back payment or publication. With `WHATSAPP_ENABLED=false` the app keeps working; OTP request then requires the bypass.

## Public rendering

`GET /render/{slug}` returns HTML with the title, description, canonical URL, and Open Graph tags. Nginx in the frontend image sends a single-segment public slug there. The React route `/{slug}` is the interactive page in development and inside the app. Reserved app paths (`login`, `register`, `staff`, `app`, `admin`) stay on the SPA.

## Jobs

`PrismJobs` runs every 60 seconds under advisory locks 8142001–8142004: hosting expiry and reminder notices, pending payments older than two hours, outbox delivery (five attempts, skipped when WhatsApp is off), and media file deletion. Files for expired events are removed only after `PRISM_MEDIA_RETENTION_DAYS` (default 90) and only when no hosting period is still open. Rows remain. Draft events that have not been updated for 14 days also have their files removed; the rows stay. That is broader than a temporary-upload sweep.

## Frontend

Customer and staff chrome follow the framed-sheet app direction (seed `1674171548`): teal-grey frame `#f2f6f7`, white sheet, ink `#131416`, accent `#1b6c80` on the hosting-day runway and the selected tab. IBM Plex Sans is the UI face. IBM Plex Mono is used for addresses and ids. Public event sites use the template theme, not the app chrome. Admin and public routes are loaded with `React.lazy`.
