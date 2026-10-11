# README release-evidence conformance

Status: implementation conformance for #1481 under the pinned
`steering/release-2026-10-product-consolidation.md` release profile.

The current README structure introduced by the documentation reset is authoritative
for its presentation. The old release-pointer checker must not require retired marketing
headings or turn historical screenshots into current acceptance. Preserve the integrity
checks: one project heading, required current navigation sections, exactly one ordered
pair of each managed pointer marker, valid source content and deterministic updates.

The README links the historical release-note draft explicitly as historical. Its content
does not prove the current #1470 product, main integration or real user/admin/MCP journeys.
The release index and draft carry the same qualification while retaining their history.
Existing release claim, security, permission, recovery and evidence gates remain intact.
The README claim guards read the current `Current status` section instead of requiring
the retired `What Is Guarded` marketing section or an `active dogfood` positioning claim.
They still require explicit nonclaims for production readiness, unrestricted agents,
universal provider interchangeability and completed lossless migration, plus the
no-unaccounted-data-loss obligation. Historical claim-matrix fixture checks are retained.

The repository owns validation through `tools/readme_release_notes.py` and its regression
tests. These checks use only checked-in Markdown and temporary fixture files; they create
no identity or runtime data and make no network or deployment request. Failure reports
contain marker/source errors only. The active documentation integrity check and strict
MkDocs build remain independent gates. Passing this check alone is never release proof.
The existing Core documentation CI job also runs marker/source regression tests, and its
integrity checker validates the actual managed pointers on every candidate.
