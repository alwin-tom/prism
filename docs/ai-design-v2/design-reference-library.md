# Design Reference Library

Admin-managed design patterns that influence AI website generation through retrieval + Design Director.

## Configuration

Design library retrieval runs whenever a `DesignReferenceRetriever` bean is available (always in the app). Hybrid vector re-rank activates when embedding provider/model/API key are configured.

| Variable | Default | Purpose |
|----------|---------|---------|
| `PRISM_AI_DESIGN_EMBEDDING_PROVIDER` | empty | e.g. `openrouter` |
| `PRISM_AI_DESIGN_EMBEDDING_MODEL` | empty | Provider model id |
| `PRISM_AI_DESIGN_EMBEDDING_DIMENSIONS` | `1536` | Must match vector column |

## Database

Flyway `V6__design_reference_library.sql`:
- Enables `vector` extension
- Tables: `design_references`, `design_reference_sections`, `design_reference_media`, `design_reference_retrieval_log`

### Production requirement

Postgres must provide pgvector (`CREATE EXTENSION vector`). Local Docker and Testcontainers use `pgvector/pgvector:pg16`.

Existing volumes created with plain Postgres need a one-time image switch / extension install before migrate.

## Seed policy

- ~40 `ORIGINAL` WebAux structured concepts (no fabricated screenshots)
- `INSPIRATION_LINK` rows for Awwwards, Land-book, Godly, Mobbin, Stitch (URL + attribution only)
- Idempotent via `seed_key`
- Screenshots optional via admin upload

## Admin APIs

- `GET /api/v1/admin/design-references/dashboard`
- `GET /api/v1/admin/design-references`
- `GET /api/v1/admin/design-references/{id}`
- `POST/PUT/DELETE` (ADMIN) for CRUD / status
- `POST /{id}/screenshots?kind=DESKTOP|MOBILE|SECTION`
- `POST /{id}/embed`, `POST /embed-missing` when embeddings configured

## Pipeline

When library + creative V2 are on:

`DESIGN_RETRIEVAL → DESIGN_DIRECTOR → (blueprint from director) → DOCUMENT → QA`

SQL retrieval always works. Embeddings only re-rank when enabled and the provider responds.
