# API specification

Base path: `/api/v1`.

Successful JSON:

```json
{ "success": true, "data": {}, "meta": { "page": 0, "size": 20, "total": 0, "totalPages": 0 } }
```

`meta` is present on paginated lists. Page indexes start at 0. Page size is capped at 100.

Errors:

```json
{ "success": false, "error": { "code": "BAD_REQUEST", "message": "Enter a mobile number.", "fields": {} } }
```

Codes follow the HTTP status: `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `PAYLOAD_TOO_LARGE`, `UNAVAILABLE`, `INTERNAL`. Validation errors include `fields`.

Cookie calls that change state send `X-CSRF-Token` equal to `prism_csrf`. Unknown JSON properties are rejected.

## Authentication

| Method | Path | Body | Result |
| --- | --- | --- | --- |
| POST | `/auth/otp/request` | `{ "phoneNumber" }` | `{ "expiresInSeconds": 300, "resendAfterSeconds": 60 }` |
| POST | `/auth/otp/verify` | `{ "phoneNumber", "otp", "fullName"? }` | Account. Sets cookies. A new phone requires `fullName`. |
| POST | `/auth/login` | `{ "phoneNumber", "password" }` | Account for `ADMIN` or `REVIEWER` only. |
| POST | `/auth/refresh` | | Rotates the refresh cookie. |
| POST | `/auth/logout` | | Revokes the refresh token and clears cookies. |
| GET | `/auth/me` | | Account. |
| PUT | `/auth/me` | `{ "fullName", "email" }` | Account. |

Account: `id`, `fullName`, `phoneNumber`, `phoneDisplay`, `email`, `roles`, `status`.

## Catalogue

`GET /categories`, `GET /categories/{id}/templates`, `GET /templates`, `GET /templates/{id}`, `GET /templates/{id}/preview`.

Preview adds `version`, `schema`, and `configuration`. Inactive rows are omitted from the customer catalogue.

## Events

`POST /events` with `{ "categoryId", "templateId", "eventName", "slug", "requestedDays" }`.

`GET /events` is the signed-in customer's page. `GET /events/slug-available?slug&eventId` returns `{ "slug", "available" }`.

`GET /events/{id}`, `PUT /events/{id}` with `{ "eventName", "slug", "requestedDays" }`, `PUT /events/{id}/configuration` with the configuration object, `GET /events/{id}/preview`, `POST /events/{id}/submit`.

`GET /events/{id}/quote?days` returns `{ "days", "dailyRatePaise", "amountPaise", "currency", "formatted" }`.

An event view includes ids and names for the category and template, revision number and submission status, `configuration`, `publicUrl`, `hosting` (`status`, `startAt`, `endAt`, `days`, `daysRemaining`, `dailyRatePaise`), and `latestReview` (`id`, `decision`, `comments`, `createdAt`, `reviewerId`). Another customer's id returns 404. A stranger who is staff can read it through the admin routes.

## Media

`POST /events/{id}/media` is multipart, field name `file`. `GET /events/{id}/media`, `GET /events/{eventId}/media/{mediaId}`, `DELETE /events/{eventId}/media/{mediaId}`.

`GET /public/media/{mediaId}` serves a file only when the event is `PUBLISHED`, hosting covers now, and the published configuration references that id.

Images: JPEG, PNG, GIF, WebP, up to `PRISM_MAX_IMAGE_SIZE_MB` (10). Video: MP4, WebM, up to `PRISM_MAX_VIDEO_SIZE_MB` (100). Type is taken from the file bytes.

## Reviews and admin

Staff:

- `GET /admin/dashboard`
- `GET /admin/reviews`
- `GET /admin/events/{id}` opens the submission and moves `SUBMITTED` to `IN_REVIEW`
- `POST /admin/events/{id}/approve` body `{ "comments" }`
- `POST /admin/events/{id}/request-changes` and `/reject` require a comment of at least 3 characters
- `GET /admin/events?status&query`
- `GET /admin/payments`

Admin only:

- `GET /admin/users?query`, `POST /admin/users` `{ "fullName", "phoneNumber", "password", "role" }`, `PUT /admin/users/{id}/status` `{ "status" }`
- `GET|POST /admin/categories`, `PUT /admin/categories/{id}`
- `GET /admin/templates`, `PUT /admin/templates/{id}`, `GET|POST /admin/templates/{id}/versions`
- `GET /admin/audit-logs`
- `POST /admin/events/{id}/suspend`, `/unpublish`, `/restore`

Dashboard data: `waitingOnReview`, `published`, `customers`, `paidTodayPaise`, `paidThisMonthPaise`, `hostingEndingSoon` (`eventId`, `eventName`, `slug`, `endAt`), `submissions` (`day`, `count` for 14 UTC days). `customers` currently counts every user, including staff.

## Payments and hosting

`POST /events/{id}/payments` with `{ "paymentType", "days", "method" }`. `paymentType` is `INITIAL_HOSTING` or `HOSTING_EXTENSION`. `POST /events/{id}/extend` forces `HOSTING_EXTENSION`.

The response is the payment plus `bypass`. A Razorpay order also includes `keyId` and `gatewayOrderId`. A bypass payment is already `PAID` and has null gateway ids.

`GET /payments/{id}` is limited to someone who can read the event. `GET /events/{id}/hosting` lists periods.

`POST /payments/webhooks/razorpay` reads the raw body and `X-Razorpay-Signature`. Optional `X-Razorpay-Event-Id` is the idempotency key; otherwise the key is the SHA-256 of the body. A repeated id returns 200 and does not create a second hosting period.

## Public site and RSVP

`GET /public/events/{slug}` returns `{ "slug", "eventName", "availability", "templateCode", "configuration", "seo" }`. `availability` is `PUBLISHED`, `EXPIRED`, or `SUSPENDED`. Expired and suspended responses omit `configuration`. Anything else is 404 with "This page is not available." Media ids in a published configuration are rewritten to `/api/v1/public/media/{id}`.

`GET /render/{slug}` is `text/html`, outside the JSON envelope. It is the document Nginx serves for a public slug.

`POST /public/events/{slug}/rsvp` with `guestName`, `guestPhone`, `guestEmail`, `attendanceStatus` (`ACCEPTED` or `DECLINED`), `numberOfGuests`, `message`, and `website`. `website` is a honeypot: a non-empty value returns a success body and stores nothing. Eight submissions an hour are allowed per event and IP. The deadline and the per-response guest cap are enforced. The response does not include other guests.

Owner: `GET /events/{id}/rsvps`, `GET /events/{id}/rsvps/summary` (`total`, `accepted`, `declined`, `expectedGuests`, `pageViews`), `GET /events/{id}/rsvps/export` (CSV), `DELETE /events/{id}/rsvps/{rsvpId}`.
