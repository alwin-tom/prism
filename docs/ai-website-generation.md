# AI website generation

For a full analysis of every step, verbatim prompts, OpenRouter response parsing, conversion to the client website, schemas, model knobs, post-process, and quality levers, see **[openrouter-website-generation-deep-dive.md](./openrouter-website-generation-deep-dive.md)**.

WebAux starts a website from a category questionnaire, then asks OpenRouter for a page in the existing visual document. The template wizard at `/app/new` stays available.

The generated page is schema 1. It opens in the existing studio and follows the existing review, payment, and publishing rules. Nothing is published automatically.

## Questionnaire

Definitions live in `questionnaire_definitions.configuration_json`, seeded from `backend/src/main/resources/questionnaires/v1.json`. A session stores the version it started with. Publishing a new definition creates the next version and leaves older sessions on theirs.

Categories: WEDDING, BIRTHDAY, ANNIVERSARY, BABY, CORPORATE, CONFERENCE, BUSINESS, PORTFOLIO, CUSTOM. Each one has its own questions. Follow-up questions use a condition object, not JavaScript.

A condition is one of:

- `{ "key", "eq" | "neq" | "contains" | "empty" | "gt" | "lt" }`
- `{ "all": [ ... ] }`
- `{ "any": [ ... ] }`

The same rules run in `ConditionEvaluator` and `frontend/src/features/website-creation/conditions.ts`. Hidden answers stay stored and are left out of the prompt. Progress uses the questions that are currently visible.

Question types in the first definitions: text, textarea, single, multi, dropdown, yesno, date, time, number, url, email, phone, images, and repeat. Images are event media ids. The file bytes stay in the existing media store.

BABY uses the baptism template category. CONFERENCE, BUSINESS use corporate. PORTFOLIO and CUSTOM use invitation. The draft event is created when the category is chosen so uploads have an owner. Its name stays "Untitled website" until generation renames it.

## OpenRouter

The browser never calls OpenRouter. `HttpOpenRouterClient` posts to `{OPENROUTER_BASE_URL}/chat/completions` with the API key, `HTTP-Referer`, and `X-OpenRouter-Title: WebAux`.

Generation always uses Creative Design Intelligence V2 (`webaux-creative-v2`): archetype catalogue, design-system tokens, visual blueprint, and deterministic visual QA. When a design-reference retriever bean is available, retrieval + Design Director consume the Design Reference Library. See [ai-design-v2/implementation-notes.md](./ai-design-v2/implementation-notes.md) and [ai-design-v2/design-reference-library.md](./ai-design-v2/design-reference-library.md).

A carousel is a `CAROUSEL` element whose children are `IMAGE` nodes. Its preset is one of `minimal`, `cards`, `hero`, `coverflow`, `fade`, or `editorial`. The model leaves `preset`, `autoplay`, `interval`, and `perView` empty on every other element. `styles.background` may be a hex colour or a `linear-gradient` with no `url()`. The system text is fixed. Answers are placed in the user message as data. The selected `style` is the writing tone. A birthday `theme` is the tone as well. A conference `theme` is the subject of the event, not the voice. Headings are titles and paragraphs are two to four sentences in that tone. The model may connect the supplied facts. It may not invent a name, date, place, phone number, or URL. When no tone is chosen, weddings and anniversaries read as elegant, birthdays and baby celebrations as warm, portfolios as editorial, and corporate, conference, and business pages as formal.

Two calls, each with `response_format.type = json_schema` and `strict: true` when a schema is supplied:

1. `prism_website_plan` — title, description, style, voice, sections.
2. `prism_website_document` — schema version 1, page, and a tree of registered element types. `props.text` is part of the schema. A page whose headings and paragraphs are blank is rejected and repaired.

`provider.require_parameters` is set on the structured request, so OpenRouter only uses a provider endpoint that supports those parameters. Reasoning is capped at 1024 tokens so a thinking model cannot spend the whole reply budget before it writes the page. If it still stops for length, WebAux retries that model once with twice the output budget, up to 16000 tokens. `openrouter/free` is the free router. A model id ending in `:free` is a free model. A paid id ending in `:floor` asks OpenRouter for the cheapest provider. The actual model, token counts, cost when the provider reports it, latency, and status are stored on the generation job.

A free request is discarded when the returned model is paid or the reported cost is above zero. The next try stays on the configured free fallbacks. Paid fallbacks run only when `OPENROUTER_ALLOW_PAID_FALLBACK=true`. Retries stop at `OPENROUTER_MAX_RETRIES` (capped at 2) per model, with at most two fallbacks and eight requests in one generation unless an admin sets a lower request cap. If structured output is rejected, one follow-up on the same model asks for a JSON object. The server still checks the JSON against the WebAux schema before saving.

An administrator changes models, fallbacks, retries, and budgets at `GET` and `PUT /api/v1/admin/ai-models` without a new build. That row overrides the environment until it is changed again. The API key stays in the environment.

## Document check

`DocumentNormalizer` drops unknown element types, maps unknown fonts onto Fraunces or IBM Plex Sans, strips unsafe style values, and clears image ids that do not belong to the event. `DocumentValidator.sanitize` is the gate. Colours have to be on element styles because sanitize clears `designTokens`.

If sanitize fails, one repair call (configurable) receives the errors. A document that still fails is not saved. The job is `FAILED` and the answers remain.

## Jobs

`QUEUED`, `PLANNING`, `GENERATING`, `VALIDATING`, `REPAIRING`, `COMPLETED`, `FAILED`, `CANCELLED`.

The API returns the existing in-flight job instead of starting a second one. Replacing an existing draft requires `confirmReplace`. A published event is refused. The install writes the document on the current draft revision and keeps a version snapshot.

## API

- `GET /api/v1/config/features` includes runtime flags such as `imageGenerationEnabled`.
- `GET /api/v1/website-categories` and `GET /api/v1/website-categories/{code}`
- `GET /api/v1/questionnaires/{code}`
- `GET|POST /api/v1/website-creation-sessions`
- `GET /api/v1/website-creation-sessions/{id}`
- `PUT .../answers`, `PUT .../progress`, `POST .../complete`
- `POST .../generate`
- `GET|POST /api/v1/website-generation-jobs/{id}` plus `/retry`, `/cancel`, `/result`
- `POST /api/v1/website-creation-sessions/{id}/open-editor`
- Staff: `GET|PUT /api/v1/admin/website-categories/{code}`, `PUT /api/v1/admin/questionnaires/{code}`
- Admin: `GET|PUT /api/v1/admin/ai-models`

## Configuration

See `.env.example`. Leave `OPENROUTER_API_KEY` empty in git. Do not commit a real key. Coolify can set the same variables; `docker-compose.yml` forwards them.

`PRISM_AI_MAX_GENERATIONS_PER_USER` defaults to 20. Repair attempts default to 1. Timeout defaults to 120 seconds. Output tokens default to 8000. Paid fallback defaults to off. A daily budget of 0 means no spend cap. The same is true for the per-generation cost cap.

## Security

The key stays on the server. Sessions and media are checked against the signed-in user. Generated links that are not http(s), mailto, tel, hash, or site-relative are dropped. Unknown media ids are cleared. Chat completion requests and responses are written to the application log. The API key is removed from those lines. The model catalog response is logged as a status and a size, because the full list is large. The model cannot approve, pay, or publish.

## Done in this pass

Questionnaire data, session storage, the guided wizard, structured OpenRouter calls, validation, one repair pass, draft install, and preview through the existing canvas.

## Still open

A staff screen for editing questions (the versioned API exists). Per-answer cost budgets beyond the generation count. Streaming progress. Audio upload, because the media store accepts images and video; music is collected as a URL. Map coordinates. A second renderer is intentionally not part of this work.
