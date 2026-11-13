# Acceptance criteria

## Visual differentiation
- At least 8 data-driven archetypes exist and are category-aware.
- A user-selected archetype is respected.
- Two generations with the same event facts but different selected directions produce materially different composition, typography, image treatment, and section rhythm — not just different colours.
- The generator avoids repeated generic section sequences.

## Design system
- Semantic design tokens are validated and resolved consistently.
- Existing schema-v1 documents remain readable and render as before.
- Token values cannot inject arbitrary CSS, URLs, or executable content.
- Typography has sensible fallback fonts and accessible sizes.

## Composition
- The blueprint specifies the purpose and composition of each section.
- Hero layout is deliberate and category-appropriate.
- Section rhythm includes meaningful variation in width, alignment, density, media balance, and background.
- Missing media does not leave broken or empty image boxes.

## Visual QA
- Actual renderer output is checked at desktop and mobile sizes when screenshot infrastructure is available.
- Vision QA is optional and capability-detected.
- QA returns structured findings; refinements are bounded and revalidated.
- Generation still works when screenshot or vision QA is unavailable, with deterministic checks.
- Logs accurately state which checks ran.

## Editor and publishing
- Generated designs open in the existing editor.
- New components are editable and responsive.
- User edits are not silently overwritten.
- Existing revision, review, payment, and publishing behaviour is unchanged.
- Public rendering contains no editor chrome.

## Reliability and security
- Existing free/paid model controls, retries, budgets, timeouts, and secret handling remain intact.
- Strict schema and media ownership validation remains enforced.
- No arbitrary HTML/JS/CSS is accepted.
- Existing schema-v1 and public rendering tests pass.
