# WebAux — Creative Design Intelligence V2
## Master implementation brief for Cursor Agent

**Objective:** Upgrade WebAux so generated websites are distinctive, modern, visually compelling, responsive, and meaningfully different from one another — without replacing the existing document architecture, renderer, editor, revision model, or publishing workflow.

This is an implementation brief, not a request for a proposal. Work directly in the existing repository. Inspect first, create a plan internally, then implement the complete change in safe, reviewable phases. Do not stop after writing a plan or producing mockups.

---

## 0. Non-negotiable operating rules

1. **Inspect the repository before changing anything.** Read the existing deep-dive document `openrouter-website-generation-deep-dive.md` if present, prompt files, questionnaire definitions, OpenRouter client, generation orchestration, document schema, normalizer, validator, renderer, visual editor registry, public rendering path, tests, migrations, feature flags, and `.env.example`.
2. Treat the actual source code as authoritative where it differs from this brief. Preserve working behaviour unless a change is explicitly required below.
3. Do not replace the owned document model with GrapesJS, Puck, a second renderer, or an unrelated page-builder framework.
4. Preserve the existing revision, admin review, payment, approval, and publishing flow. AI must never approve, charge, or publish.
5. Preserve the existing feature flags and defaults. New capabilities must be safely gated and backward-compatible.
6. Never break existing saved schema-version-1 documents. Add a migration only if truly necessary; prefer optional fields and a compatible schema evolution strategy.
7. Keep all OpenRouter calls server-side. Never expose API keys or secrets to the browser.
8. Never invent event names, dates, locations, contact details, URLs, image IDs, or factual content. Use only supplied answers and valid event media.
9. Keep document sanitization and allowlisting strict. New component types, properties, styles, links, and animation settings must be explicitly allowlisted.
10. Do not weaken security to make generation easier. Preserve ownership checks, safe URL rules, media ownership checks, request budgets, timeouts, retry caps, and structured output validation.
11. Use existing project conventions, package manager, code style, component patterns, logging, tests, and migration framework. Avoid unnecessary dependencies.
12. **Do not create a disconnected demo.** The generated document must open in the existing WebAux studio and render through the existing public renderer, including no-JavaScript rendering where currently supported.
13. Do not silently discard design tokens. Investigate the current normalizer/validator behaviour that clears `designTokens`; implement a safe, validated token strategy so global design systems can actually be used. Maintain compatibility with existing documents.
14. Investigate the stated `CAROUSEL` mismatch: it is used in generation documentation but absent from the registered element list. Reconcile it fully across schema, validator, normalizer, editor, renderer, and tests, or use a consistent existing representation. Do not leave a half-supported type.
15. Before finishing, run relevant backend/frontend tests, lint, type checks, and production builds. Fix regressions introduced by this work. Report any pre-existing failures separately.

If Google Stitch MCP is configured in Cursor, discover and use its available tools for design exploration and reference creation. If it is unavailable, continue using the local reference specs and do not block implementation. Do not claim Stitch actions succeeded unless the MCP tool actually reports success.

---

## 1. Existing product and architecture to preserve

WebAux (formerly Prism) is an event and website creation platform. Current categories include:

`WEDDING`, `BIRTHDAY`, `ANNIVERSARY`, `BABY`, `CORPORATE`, `CONFERENCE`, `BUSINESS`, `PORTFOLIO`, `CUSTOM`.

Current architecture:
- React frontend and Spring Boot backend.
- The visual editor is a second mode on the same event revision; template editor remains available.
- The website is a versioned JSON document stored in `event_page_documents.document_json`.
- Studio, draft preview, admin review, and public site consume the same document tree.
- Selection, zoom, open panels, and other editor-only state stay outside the persisted website document.
- Current schema is version 1 with `page`, `breakpoints`, `designTokens`, and a recursive `root` tree.
- Registered primitives currently include `SECTION`, `CONTAINER`, `FLEX`, `GRID`, `GROUP`, `HEADING`, `PARAGRAPH`, `BUTTON`, `IMAGE`, `SPACER`, `DIVIDER`.
- Existing generation uses a questionnaire, an OpenRouter planning call, a document-generation call, validation/normalization, optional repair, and draft installation.
- The generated page opens in the existing visual studio.
- Existing draft/revision, review, payment, and publishing flows remain authoritative.

Website generation and the visual editor are always enabled.

Keep existing environment variable names working even if internal naming is modernised. Do not perform a broad branding rename as part of this task.

---

## 2. Product outcome

Turn the generation system from “questionnaire answers → generic page tree” into a **creative director pipeline**:

1. Understand event facts, audience, tone, and available media.
2. Select or propose a distinctive creative direction.
3. Define a complete design system.
4. Plan page-level visual storytelling and varied section compositions.
5. Generate a document using supported WebAux components.
6. Render and evaluate actual desktop/mobile output.
7. Make bounded, targeted improvements.
8. Install the final draft into the existing revision and editor workflow.

The result should not be six superficial colour variations of the same layout. Different directions must vary in typography, composition, image treatment, spacing, section rhythm, decorative language, contrast, and visual hierarchy.

### Quality principles

- Distinctive, not random.
- Beautiful, not merely complex.
- Strong visual hierarchy and an intentional first viewport.
- Photography-led when suitable media exists; never force empty image placeholders.
- Varied section rhythm; avoid repeating the same card grid section after section.
- Intentional whitespace, typography scale, colour contrast, and alignment.
- Responsive by design, not just desktop layout squeezed onto mobile.
- Appropriate to category, culture, audience, supplied content, and selected style.
- Accessible semantics, keyboard behaviour, readable contrast, reduced-motion support.
- Avoid gratuitous gradients, glassmorphism, excessive rounded cards, generic icon rows, and repetitive centred headings.
- Do not optimise for novelty at the expense of usability or factual correctness.

---

## 3. Required implementation workstreams

### Workstream A — Repository audit and compatibility baseline

- Inspect the complete generation flow and the deep-dive document if present.
- Trace every place that reads, writes, validates, normalizes, renders, edits, converts, previews, or publishes the document.
- Identify schema/version assumptions, component registries, allowed style keys, responsive behaviour, fonts, image handling, and public/no-JS rendering.
- Add or update tests that prove schema-v1 documents still work.
- Document actual findings and any deviations from this brief in `docs/ai-design-v2/implementation-notes.md`.

### Workstream B — Creative direction / design archetypes

Create a data-driven, versioned archetype catalogue. Do not hardcode archetype logic throughout prompt strings.

Initial cross-category archetypes (adapt names and definitions to fit the existing codebase):
- Luxury Editorial
- Botanical Romance
- Modern Minimal
- Royal Heritage
- Playful Contemporary
- Cinematic Luxury
- Contemporary Art / Gallery
- Warm Storybook
- Bold Typographic
- Classic Formal

Archetypes are creative systems, not fixed templates. Each must define:
- Suitable categories and audience/context.
- Mood and emotional intent.
- Typography roles and recommended pairings.
- Palette direction (not necessarily one fixed palette).
- Layout/composition tendencies.
- Hero patterns.
- Image treatment.
- Section rhythm and variation.
- Decorative vocabulary.
- Interaction/motion character.
- Accessibility cautions.
- Anti-patterns to avoid.
- A few reference keywords / search descriptions for Stitch.

Category-specific adaptation:
- Weddings/anniversaries: ceremony, editorial, cultural heritage, modern party, intimate, destination, etc.
- Birthdays/baby: playful, storybook, pastel, bold, family, milestone, etc.
- Corporate/conference/business: professional, editorial, data-led, high-contrast, trustworthy; avoid wedding-like ornamentation.
- Portfolio: art direction, case-study storytelling, expressive typography, strong work imagery.
- Custom: infer carefully from answers; ask for clarification only if essential.

The model may select an archetype based on answers and media, but it must also return a concise rationale. If a user explicitly chooses a style, that choice takes precedence. Offer an “AI choose for me” path.

Prevent repetition:
- Store archetype and composition metadata with the generation job/document metadata in a backward-compatible way.
- Where history is available, use recent designs for the same user/category as a diversity signal. Do not expose another user's private data.
- Do not falsely claim uniqueness. Aim for meaningful variation using measurable composition differences.

### Workstream C — Explicit design-system output

Introduce a validated design-system object. It should include, at minimum:
- Semantic colour roles: background, surface, surfaceAlt, text, mutedText, primary, secondary, accent, border, overlay, focus.
- Typography roles: display, heading, body, accent (optional); weights, sizes, line heights, letter spacing.
- Spacing scale and section spacing.
- Content width and grid settings.
- Shape language: corner radii, border style, button style.
- Image treatment: aspect ratios, masks, crop/focal position, overlays.
- Shadow/elevation tokens.
- Responsive breakpoints and rules.
- Motion tokens: duration, easing, entrance style, reduced-motion fallback.

Requirements:
- Use semantic tokens rather than scattering arbitrary values across every node.
- Validate tokens against strict schemas and safe CSS allowlists.
- Resolve tokens consistently in studio, draft preview, admin preview, public renderer, and no-JS output.
- Preserve existing schema-v1 behaviour. If schema version changes, provide explicit compatibility and migration tests.
- Do not use external font loading in a way that breaks privacy, performance, CSP, or offline/local fallback. Follow existing font strategy and allowlist.
- Ensure contrast and readable type sizes. Include safe fallbacks.

### Workstream D — Visual blueprint / composition planning

Add a structured visual blueprint stage between creative direction and document-tree generation.

The blueprint should specify:
- Selected archetype and design-system reference.
- Page story / narrative intent.
- Hero composition and content hierarchy.
- Ordered sections, each with purpose, composition pattern, background treatment, content density, media needs, and responsive adaptation.
- Component choices and variants.
- Visual rhythm: alternating density, alignment, width, colour, and image/text balance.
- CTA and conversion priorities.
- Motion/interaction intent where supported.
- Missing media/content handling.
- Category-specific mandatory content (without inventing facts).

Avoid generic sequences such as centred hero → three cards → centred text → three cards → footer. Require deliberate variation and explain why each section exists.

The blueprint is a plan, not a second renderer. Convert it into the existing document tree.

### Workstream E — Component system expansion

Keep primitives and introduce a composable design-component layer. Implement only components that can be fully supported across validation, normalisation, editor, renderer, public output, and tests.

Prioritise:
1. Hero compositions: full-bleed, split, editorial, overlapping, collage, typographic.
2. Storytelling: timeline, story chapters, quote/statement, event details, schedule.
3. Media: gallery, masonry/editorial grid, carousel with accessible controls, image collage.
4. Utility: countdown, RSVP integration using the existing RSVP API, map/location link, contact details.
5. Content: feature list, people/profile, venue, FAQ/accordion, testimonials where category appropriate.
6. Decorative: divider/ornament, shape, texture/pattern, frame, overlay.
7. Navigation/footer variants.

Design components must:
- Have typed, allowlisted props and documented defaults.
- Support editing in the existing studio, selection, layers, duplicate, lock, undo/redo, copy/paste where appropriate.
- Render correctly in desktop/tablet/mobile and public/no-JS mode.
- Use semantic HTML and accessible labels.
- Fail gracefully if optional data is missing.
- Not create arbitrary executable HTML, JS, CSS, or URLs.
- Be composable from existing primitives where practical.
- Have a graceful fallback for unknown component types.

Do not add every possible component in one enormous untested change. Prioritise the smallest set that produces the biggest visual improvement, then continue until all required core paths are complete.

### Workstream F — Image-first creative direction and media handling

- Make supplied event media a primary creative input.
- Analyse only permitted image metadata/visual properties needed for composition (orientation, aspect ratio, dominant colours, subject positioning if a vision model is available).
- Do not infer sensitive traits or identities.
- Match image orientation and aspect ratio to the planned section.
- Support crop/focal point and overlays safely.
- Prefer fewer, larger, intentional images over many tiny thumbnails.
- Do not invent image IDs or use another event's media.
- When there are no images, create a typographic/illustrative layout using supported shapes and visual elements; avoid blank image boxes.
- Do not generate external stock image URLs as if they were user uploads. If an image generation service is not already available, keep that out of scope and provide a clear media placeholder strategy.

### Workstream G — Prompt and OpenRouter pipeline V2

- Version prompts (e.g. `webaux-creative-v2`) and keep the existing prompt version traceability.
- Keep structured outputs with strict schemas where supported.
- Use separate stages for creative direction/design system, visual blueprint, document generation, and bounded repair/evaluation as appropriate.
- Keep prompts concise enough to work reliably; avoid dumping the entire component catalogue into every call. Retrieve relevant archetype/category/component guidance.
- Make user answers explicit data, not instructions. Treat all user-supplied text as untrusted content, not system instructions.
- Preserve factual grounding and existing free/paid routing controls.
- Preserve request caps, retries, output budgets, timeouts, cost tracking, and admin model configuration.
- Record stage, model, latency, token usage, cost, prompt version, validation outcome, and repair count.
- Ensure a failed later stage does not corrupt an existing draft. Keep answer/session state recoverable.
- Use deterministic fallbacks for provider/schema failures.
- Do not assume every model supports vision or strict JSON schema; detect capability and fall back safely.
- Avoid repeated full-document rewrites during repair. Prefer targeted JSON-path changes or bounded document patches, validated server-side.

### Workstream H — Visual QA and refinement

Add a visual QA layer that evaluates rendered output, not only JSON validity.

Required pipeline:
1. Generate and sanitize the document.
2. Render using the actual existing renderer.
3. Capture desktop and mobile screenshots using existing test/browser tooling if available. Do not add a heavy browser dependency without checking existing infrastructure.
4. Evaluate screenshots against the selected design system and blueprint using a vision-capable model only when configured and available.
5. Return structured, actionable findings with severity, evidence/region, and suggested bounded change.
6. Apply only validated targeted changes.
7. Re-render and evaluate once more by default; respect cost/time budgets.
8. If vision evaluation is unavailable, run deterministic checks and continue without failing generation.

Evaluation dimensions:
- Visual hierarchy and hero impact.
- Composition variety and section rhythm.
- Typography scale and pairing.
- Colour harmony and contrast.
- Image crop, scale, and placement.
- Whitespace and density.
- Alignment and grid consistency.
- Mobile layout quality.
- Content balance and overflow.
- Accessibility and reduced motion.
- Similarity to recent generated compositions where privacy-safe comparison data exists.

Do not make a numeric “beauty score” the only gate. Use thresholds and findings for actionable issues. Never claim visual evaluation occurred if no screenshot/vision stage ran. Record which checks actually ran.

### Workstream I — Questionnaire creative preferences

Add an optional visual-preference step to the creation wizard:
- Visual direction cards with representative local/reference thumbnails if available.
- Choices should be plain-language, not design jargon.
- Options: let WebAux choose; show a small number of suitable archetypes; optionally select visual richness (minimal / balanced / expressive) and motion (subtle / cinematic / playful).
- The preference step must adapt to category.
- Do not make the wizard longer or block creation for users who skip it.
- Persist the questionnaire version/session behaviour already in place.
- Use preference answers as explicit inputs to creative direction.
- Keep accessible labels and mobile usability.
- Do not use remote image URLs that are not approved assets.

### Workstream J — Stitch-assisted reference library

If Stitch MCP is available:
- Discover tools first and inspect the available operations and required schemas.
- Create or use a WebAux design-reference project.
- Generate distinct concept screens for representative categories and archetypes.
- Ask for desktop and mobile variants.
- Save design outputs/links/IDs returned by the tool in a local reference manifest; never fabricate IDs or URLs.
- Extract design principles and component specs from approved references.
- Do not simply copy generated Stitch source code into production.
- Do not let Stitch output bypass WebAux validation or document architecture.

If Stitch MCP is unavailable or fails:
- Continue implementation with the archetype catalogue and documented reference descriptions.
- Record the limitation; do not block core implementation.

### Workstream K — Editor and renderer experience

- Expose design system tokens in the existing editor where appropriate, with global and local overrides.
- Provide clear component names and variants in the insertion library.
- Allow users to edit component content and supported visual properties.
- Keep editor chrome separate from the public document.
- Preserve selection, undo/redo, autosave versioning, responsive style overrides, and existing keyboard shortcuts.
- Add responsive controls for new components.
- Ensure user edits are not overwritten by a later AI refinement. Refinement should operate on generated/unlocked regions or request confirmation before replacing user-modified content.
- Public output must not include editor-only controls.

---

## 4. Suggested target pipeline

Implement the following logical stages, adapting names to current code conventions:

`QUESTIONNAIRE → CONTEXT_ASSEMBLY → CREATIVE_DIRECTION → DESIGN_SYSTEM → VISUAL_BLUEPRINT → DOCUMENT_GENERATION → SANITIZE_VALIDATE → RENDER_QA → TARGETED_REFINEMENT → DRAFT_INSTALL`

Not every stage must require a separate model call. Combine calls only where it improves reliability and cost without losing clear structured outputs.

Keep the existing job lifecycle compatible. Add stage metadata rather than breaking clients that expect current statuses. Expose progress only if it fits the current API patterns.

---

## 5. Suggested data contracts

Use the supporting files in this package:
- `schemas/creative-direction.schema.json`
- `schemas/visual-blueprint.schema.json`
- `schemas/visual-qa.schema.json`
- `specs/archetypes.json`
- `specs/component-roadmap.md`
- `specs/acceptance-criteria.md`
- `stitch/stitch-reference-prompts.md`

These are starting contracts, not permission to ignore existing backend conventions. Adapt field names and strictness to the actual codebase. Keep schema validation in backend code and test it.

---

## 6. Security, reliability, performance, cost

- Server-side secrets only.
- Strict ownership and event-media validation.
- Allowlisted element types, props, styles, fonts, CSS values, transforms, animation presets, and URL schemes.
- No arbitrary HTML/JS/CSS from the model.
- No external network URLs in background-image CSS; no `url()` in model-controlled styles.
- Sanitize all generated content and escape text at render boundaries.
- Prevent prompt injection through questionnaire answers and uploaded metadata.
- Respect free-model restrictions and paid fallback settings.
- Add stage-specific budgets and an overall generation budget.
- Cache static archetype/component guidance where appropriate.
- Avoid sending full image payloads or unrelated user data to models.
- Log useful diagnostics without logging API keys, secrets, or unnecessary personal data.
- Ensure retries are bounded and idempotent.
- Use existing asynchronous job and cancellation patterns.
- Make the pipeline recoverable when a provider times out or returns malformed output.

---

## 7. Tests required

At minimum add/update tests for:
- Existing schema-v1 document round-trip and public rendering.
- New design-system schema validation and token resolution.
- Every new component: valid props, invalid props, normalizer, editor, desktop/mobile renderer, public/no-JS output.
- `CAROUSEL` consistency if retained.
- Unknown types/props/styles rejected or safely dropped according to existing policy.
- URL, CSS, font, transform, animation, and media ownership restrictions.
- User facts never invented by generation.
- User-selected archetype takes precedence over AI choice.
- AI-selected archetype is category-compatible.
- Blueprint sections map to supported components.
- Empty/missing media produces a designed fallback, not broken placeholders.
- Visual QA unavailable does not fail generation.
- Visual repair is bounded and revalidated.
- Existing free/paid routing and cost limits remain enforced.
- Existing draft revision and publishing invariants remain unchanged.
- User manual edits are preserved during refinement.
- Mobile layouts do not overflow horizontally.
- Feature flags default to current safe values.

---

## 8. Delivery and execution expectations

Implement the work in coherent commits/steps internally, but do not require the user to approve each technical subtask. Do not stop after the first workstream.

Before coding:
- Produce a short internal implementation checklist from the actual repository.
- Identify risks and dependencies.
- If a critical ambiguity blocks safe implementation, ask one focused question. Otherwise make conservative choices and proceed.

During implementation:
- Update documentation and `.env.example`.
- Add database migrations only when required.
- Keep new feature flags off by default unless the current product explicitly enables them.
- Make the system usable with Stitch absent and with vision QA absent.
- Avoid placeholder TODO implementations for core requested functionality.

At completion, provide:
1. Summary of architecture changes.
2. Files/modules changed.
3. New/updated configuration and defaults.
4. Migrations, if any.
5. Tests/builds executed and results.
6. Which Stitch and visual-QA capabilities actually ran.
7. Known limitations and follow-up items.
8. How to enable the new pipeline safely in local/dev and production.

**Success means:** WebAux produces pages with visibly different creative directions, stronger first screens, intentional section rhythm, polished typography and image composition, responsive layouts, and a verifiable visual QA process — while preserving existing document, editor, revision, security, and publishing guarantees.
