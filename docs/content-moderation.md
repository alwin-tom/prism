# WebAux content moderation

Permanent AI-powered text moderation runs on every website submission before an administrator can approve the site.

## Architecture

```text
Submit revision snapshot
        → create website_moderation_reports row (PENDING)
        → async ModerationJobService
        → WebsiteContentExtractionService
        → OpenRouterContentModerationProvider (chunked)
        → ModerationResultValidator + risk aggregation
        → persist findings
        → admin review UI
```

Moderation is always on. There is no feature flag to disable the workflow. Model selection uses the existing OpenRouter configuration.

## Severity

| Level | Meaning |
| --- | --- |
| CRITICAL | Immediate or particularly serious risk |
| HIGH | Clearly harmful or seriously inappropriate |
| MEDIUM | Needs human assessment |
| LOW | Mild / borderline |
| CLEAN | No concerning text detected |

Severity is an AI assessment only. Administrators retain final authority. AI findings never auto-approve or auto-reject a website.

## Categories

`PROFANITY`, `HARASSMENT`, `HATE_SPEECH`, `THREATS`, `VIOLENCE`, `SEXUAL_CONTENT`, `BULLYING`, `DEFAMATION`, `SELF_HARM`, `ILLEGAL_ACTIVITY`, `SCAM_FRAUD`, `OTHER`

## Report statuses

`PENDING` → `PROCESSING` → `COMPLETED`  
Failures become `RETRY_REQUIRED` until attempts are exhausted, then `FAILED`. Failed moderation is never treated as clean.

Finding review statuses: `PENDING`, `CONFIRMED`, `FALSE_POSITIVE`, `RESOLVED`, `DISMISSED`

## Database

Flyway `V8__content_moderation.sql`:

- `website_moderation_reports` (unique per revision)
- `website_moderation_findings`
- `moderation_audit_logs`

Resubmission creates a new revision and a new report. Previous reports remain.

## AI configuration

Uses the shared OpenRouter client.

| Property | Env | Notes |
| --- | --- | --- |
| `prism.openrouter.moderation-model` | `OPENROUTER_MODERATION_MODEL` | Preferred moderation model |
| `prism.openrouter.moderation-fallbacks` | `OPENROUTER_MODERATION_FALLBACKS` | Optional fallbacks |

If moderation model is blank, the service falls back to repair → generation → planning model. This configures which model is used; it does not enable or disable moderation.

## APIs

Admin / reviewer only (`/api/v1/admin/**`):

- `GET /events/{eventId}/moderation`
- `GET /events/{eventId}/moderation/findings`
- `POST /events/{eventId}/moderation/retry`
- `PATCH /moderation/findings/{findingId}/status`
- `POST /moderation/findings/{findingId}/notes`
- `GET /moderation/summary`
- `GET /moderation/audit`
- `GET /moderation/reports/{reportId}`
- `GET /moderation/reports/{reportId}/audit`

Event payloads now include a `moderation` summary card for staff review queues.

## Admin UI

The review queue shows moderation status, overall risk, and finding counts, with filters for critical/high/medium/low/clean/processing/failed.

The review page includes a Content Moderation panel, finding actions, retry on failure, and preview highlighting via `data-node` element IDs.

Approve requires a `COMPLETED` moderation report. Decisions stay locked while moderation is `PENDING` or `PROCESSING`.

## Privacy

Only extracted textual content is sent to the model. Image binaries are not moderated. Reports are admin/reviewer only. Prompts instruct the model to treat website text as untrusted data.

## Limitations

- Text moderation only; images, video, and audio are not assessed.
- Exact in-preview text-span highlighting depends on element IDs; when spans cannot be located, the element is outlined and the excerpt is shown in the panel.
- Large sites are chunked (~6k characters). Overlapping findings are deduplicated after aggregation.
- If OpenRouter is misconfigured, moderation fails visibly and must be retried; it does not silently pass.
