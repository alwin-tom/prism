# WebAux Creative Design Intelligence V2 — Cursor handoff

This package is intended to be opened or copied into the WebAux repository and used with Cursor Agent.

## Start here
1. Copy this folder into the repository (recommended location: `docs/ai-design-v2/`).
2. Open Cursor Agent in the repository.
3. Paste the complete contents of `CURSOR_MASTER_PROMPT.md` as the single implementation instruction.
4. Keep the supporting `specs/`, `schemas/`, and `stitch/` files available in the repository so Cursor can inspect them.
5. Let Cursor inspect the actual source before modifying it.

The master prompt explicitly tells Cursor to inspect the existing `openrouter-website-generation-deep-dive.md`, preserve current architecture and flows, use Stitch MCP if available, and continue without Stitch if unavailable.

## Important
- This is a comprehensive implementation brief. A single prompt can direct the work, but a large codebase upgrade may exceed one Agent execution context. If Cursor stops because of context/tool limits, ask it to continue from the implementation notes and existing code without restarting or reverting completed work.
- Keep the existing feature flags off by default until tests and manual review pass.
- The JSON schemas and archetypes are starting contracts. Cursor must adapt them to actual backend conventions and validate them server-side.
