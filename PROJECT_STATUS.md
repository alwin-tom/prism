# Project status

`mvn test` passed, including `PrismJourneyTest` on PostgreSQL 16. Frontend `npm test` (4 tests) and `npm run build` passed.

## Done

- Workspace layout, Compose, Dockerfiles, Nginx slug routing, `.env.example`, and `.gitignore`.
- Flyway schema, seed of five categories and ten templates, optional admin from the environment.
- OTP and staff login, cookies, CSRF, roles, ownership checks, production bypass guard.
- Event create, configuration sanitising, revisions, submit, review, approve, reject, request changes.
- Media upload with byte checks, private draft access, public access only for a live published reference.
- Quotes, Razorpay orders and signed idempotent webhooks, payment bypass through the same completion path.
- Hosting periods, extensions, expiry job, suspend, unpublish, restore.
- WhatsApp client and outbox. Disabled WhatsApp does not block the app. OTP then needs the bypass.
- RSVP submit, owner list, summary, CSV, honeypot and rate limit.
- Customer, staff, and public React routes against those APIs. Shared section renderer for every template.
- A visual page document, stored per revision, behind `PRISM_ADVANCED_EDITOR_ENABLED` (default false). The template editor stays. Published pages render a saved visual document even when the flag is off.
- Frontend unit tests: hosting price, runway cells, escaped hero text, six-digit OTP rejection, canvas history, and visual document operations. `npm test` and `npm run build` should be re-run after this slice.

## Verification

- Backend: earlier `mvn test` passed, including `PrismJourneyTest` on PostgreSQL 16. This slice compiled, and `DocumentValidatorTest` passed (2 tests). The full suite was not re-run after the visual document code.
- Frontend `tsc --noEmit` passed. Vitest passed for the visual document operations and the public site renderer (13 tests). Docker 29 speaks API 1.44 or newer. Surefire passes `-Dapi.version=1.44` so Testcontainers can connect. The Docker CLI was already fine; the Java client was not.
- Screens were not clicked in a browser. No browser tool is available in this session.

## Not done in the browser

The screens were not clicked through in a browser. There is no browser automation tool in this session. API behaviour is covered by MockMvc in the journey test once that suite passes.

The design inspector was not run. The app direction is framed-sheet, seed `1674171548`. The frame is `#f2f6f7` rather than the sheet's `#f9fbfc`, using the sheet's own option to tint the frame from the product hue. No other redraw.

## Known gaps

- The visual studio is a first slice: section, container, row, grid, group, heading, text, button, picture, spacer, and divider. Rich text, video, gradients, carousels, accordions, tabs, modals, animation, and hover actions are not built. See `docs/ADVANCED_EDITOR.md`.
- `GET /admin/categories`, template and version reads, and `GET /events/{id}/hosting` still serialise JPA entities.
- The dashboard `customers` figure counts every user.
- Login throttling is in memory, so it is per process. OTP limits are in the database.
- A public HTML render and the JSON public endpoint each increment the view count.
- Files for a draft that has not been saved for 14 days are deleted. The database rows remain.
- Placeholder SVGs stand in for photography. Seeded names, venues, and dates are sample copy and must be replaced on a real site.
- Template codes: `WEDDING_LINEN`, `WEDDING_NIGHT`, `ANNIVERSARY_YEARS`, `ANNIVERSARY_TABLE`, `BIRTHDAY_DAY`, `BIRTHDAY_DARK`, `INVITATION_CARD`, `INVITATION_EVENING`, `CORPORATE_AGENDA`, `CORPORATE_HALL`.
- Sample titles include Meera & Arun, Leela & Kabir, Nalini and Dev Kapoor, Farah & Imran, Asha at 30, Rohan at 40, the Iyer house dinner, Studio Rasa, Sable Atelier, and Harbour Line.
