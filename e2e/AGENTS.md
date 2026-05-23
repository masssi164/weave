# E2E / Acceptance Agent Rules

Gherkin in this directory is the Weave product contract.

- Keep scenarios readable for product, engineering, and screen-reader users.
- Describe user-visible Weave behavior, not implementation details, HTTP routes, selectors, JWTs, or provider internals.
- Every scenario in `features/` must have a stable tag and a mapping in `scenario_mappings.json`.
- Every mapping must point to checked-in executable evidence and evidence markers.
- Do not add aspirational scenarios without executable mappings; use roadmap docs or issues for future intent.
- Live-stack evidence must be sparse, sanitized, and deterministic.
- Never write tokens, cookies, private keys, raw provider errors, room IDs, event IDs, filenames, usernames, or display names into evidence artifacts.
