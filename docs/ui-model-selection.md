# Website UI/UX model selection

Backend-only switch between the existing free OpenRouter website-generation models and a premium UI/UX model chain.

## Environment

```env
# true = preserve current free-model workflow (default when missing/invalid)
# false = use configured premium UI/UX models
WEBAUX_USE_FREE_UI_MODELS=true

# Verified OpenRouter model IDs (override as needed)
WEBAUX_PREMIUM_UI_PRIMARY_MODEL=anthropic/claude-opus-5.5
WEBAUX_PREMIUM_UI_SECONDARY_MODEL=openai/gpt-6-astra
WEBAUX_PREMIUM_UI_FALLBACK_MODEL=google/gemini-4-carbon
WEBAUX_PREMIUM_UI_REFINEMENT_MODEL=moonshotai/kimi-k3.1-pro
```

Bound in `application.yml` under `prism.ui-models.*`.

## Behaviour

| `WEBAUX_USE_FREE_UI_MODELS` | Strategy |
|---|---|
| `true`, blank, missing, or any value other than false/0/no/off | **FREE** — existing `OPENROUTER_PLANNING/GENERATION/REPAIR_*` models and admin overlays |
| `false`, `0`, `no`, `off` | **PREMIUM** — `WEBAUX_PREMIUM_UI_*` only |

Premium mode never silently falls back to free models. If premium models are unavailable or all fail, generation fails with a clear error.

## Task routing (premium)

| Task | Preferred model | Fallbacks |
|---|---|---|
| PLANNING (director / direction / blueprint / plan) | Primary (Claude Opus 5.5) | Secondary → Fallback → Refinement |
| GENERATION (page document) | Secondary (GPT-6 Astra) | Primary → Fallback → Refinement |
| REPAIR / REFINEMENT | Refinement (Kimi K3.1 Pro) | Fallback → Secondary → Primary |

## Scope

Affects website-design generation in `WebsiteDocumentGenerator` only.

Does **not** control:
- Content moderation models
- Image generation models
- Typography edit AI
- Design-reference embeddings

## Observability

Generation usage reports include `uiModelStrategy` (`FREE` or `PREMIUM`). Each stage note records `strategy`, `task`, requested/actual model, tokens, latency, and retries already tracked by the OpenRouter client.

## Deploy

1. Set the env vars in `.env` / Coolify / compose.
2. Restart the backend.
3. No database migration. No frontend change.
