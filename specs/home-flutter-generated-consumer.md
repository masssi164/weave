# Flutter Home generated User consumer

Implementation evidence for #1472/#1473 against the pinned consolidation profile.
Server owns the Home v3 schema and current member authorization. This client slice
consumes that generated User contract; Admin policy and release-readiness operations
remain outside the member Home consumer.

- Use generated `WorkspaceApi.home` and its generated transport models. Preserve
  the existing app-facing facade and map transport values to Home presentation
  entities without handwritten URLs, request DTOs or response JSON parsing.
- Home section counts are nullable. An unmeasured count stays unknown and is
  omitted from count labels; a measured zero remains zero. Readiness cannot invent
  an item count. Negative counts are rejected instead of being coerced to zero.
  The Home summary remains separately available to assistive technology, with
  known counts announced and unknown counts omitted.
- Keep support-safe activity reference validation, duplicate detection, typed
  readiness and safe product-route checks. Failed requests display localized
  member recovery text and never render response bodies or operator diagnostics.
- Keep ordinary Weave session restoration/refresh behavior. Discard a pending Home
  response after a session, server or explicit integration invalidation changes;
  do not republish another member's activity or capability snapshot. A refresh
  state carrying an error displays the localized recovery action instead of a
  perpetual loading indicator.

Validate generated HTTP route/authorization consumption, Home v3 mapping, nullable
counts, negative and unsafe payloads, member failure presentation and stale response
fencing. The architecture guard must require the generated `WorkspaceApi.home`
operation and reject the superseded handwritten Home JSON decoder. Run focused
Flutter tests, analysis and spec conformance. These fixtures
are not live single-sign-in or integrated product acceptance evidence.
