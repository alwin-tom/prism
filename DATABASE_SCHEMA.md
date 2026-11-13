# Database schema

Flyway migration: `backend/src/main/resources/db/migration/V1__init.sql`.

Identifiers are UUIDs. Timestamps are `timestamptz` and written in UTC. Money is `integer` paise. Template schema, customer configuration, WhatsApp payloads, audit metadata, outbox payloads, and webhook bodies are `jsonb`.

Reference rows (five categories and ten templates) are inserted by `ReferenceDataSeeder` from `TemplateCatalog` when `event_categories` is empty. The admin account is inserted only when `PRISM_ADMIN_INITIAL_PHONE` and `PRISM_ADMIN_INITIAL_PASSWORD` are set, the password is at least 10 characters, and that phone does not already exist.

## Tables

| Table | Role |
| --- | --- |
| `users` | Name, unique phone, optional unique email, nullable password hash, `ACTIVE` or `DISABLED`. |
| `user_roles` | `CUSTOMER`, `ADMIN`, or `REVIEWER`. Unique per user and role. |
| `otp_verifications` | HMAC of the code, expiry, attempts (0–10), verification time, request IP. |
| `refresh_tokens` | SHA-256 of the opaque refresh cookie, expiry, revocation. |
| `event_categories` | Name, unique code, description, thumbnail, active flag. |
| `templates` | Category, unique code, description, thumbnail, active flag. |
| `template_versions` | Unique `(template_id, version_number)`, `schema_json`, `default_configuration`, active flag. |
| `events` | Owner, category, template version, name, unique slug, status, requested days (1–365), approved and published revision ids. |
| `event_revisions` | Unique `(event_id, revision_number)`, configuration, submission status, submit and approval times. |
| `event_reviews` | Reviewer, decision (`APPROVED`, `REJECTED`, `CHANGES_REQUESTED`), comments. History is append-only. |
| `event_publications` | Revision, published and unpublished times, status. A partial unique index allows one `PUBLISHED` row per event. |
| `payments` | Type `INITIAL_HOSTING` or `HOSTING_EXTENSION`, gateway `RAZORPAY` or `BYPASS`, amount, days, daily rate, currency, status. Bypass rows must have null gateway ids. Gateway order ids are unique when present. |
| `hosting_periods` | Payment, `start_at`, `end_at` (`end_at > start_at`), days, daily rate, `SCHEDULED`, `ACTIVE`, or `EXPIRED`. |
| `hosting_extensions` | Extra days and amount, pointing at the new period and its payment. |
| `event_media` | Original name, random stored name, type `IMAGE` or `VIDEO`, detected MIME type, size, path, optional thumbnail. |
| `event_rsvps` | Guest name, optional phone and email, `ACCEPTED` or `DECLINED`, party size 0–20, message, request IP. |
| `url_reservations` | Unique slug held for an event and user. |
| `whatsapp_messages` | Template name, payload, `PENDING`, `SENT`, `FAILED`, or `SKIPPED`, provider id, attempts. |
| `audit_logs` | Actor, action, entity, metadata, IP. |
| `outbox_events` | Aggregate, type, payload, `PENDING`, `PROCESSED`, or `FAILED`, attempts, availability, optional unique `dedupe_key`. |
| `payment_webhook_events` | Unique `(gateway, event_id)` plus the raw payload. |
| `event_views` | Primary key `(event_id, viewed_on)`, count of page loads for that UTC date. Not unique visitors. |

## Status checks

Event status is constrained to `DRAFT`, `SUBMITTED`, `IN_REVIEW`, `CHANGES_REQUESTED`, `APPROVED`, `REJECTED`, `PUBLISHED`, `EXPIRED`, `SUSPENDED`.

Slugs match `^[a-z0-9]+(-[a-z0-9]+)*$` and are unique on both `events` and `url_reservations`.

`events.approved_revision_id` and `events.published_revision_id` reference `event_revisions`. Those foreign keys are added after `event_revisions` exists.
