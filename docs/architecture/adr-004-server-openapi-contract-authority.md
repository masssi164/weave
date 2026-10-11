# Superseded architecture document

This path is retained so older links remain understandable. It is not current architecture authority.

For the current release, use the pinned corpus
`steering/release-2026-10-product-consolidation.md`, [epic #1470](https://github.com/masssi164/weave/issues/1470),
and [Weave data-sovereignty core](data-sovereignty-core.md). Server code generates
separate User and Admin OpenAPI artifacts. Files and Calendar member operations,
Flutter, Admin UI, MCP, and product E2E consume the appropriate generated HTTP
contract. Matrix Client-Server remains the Chat protocol exception. Historical
content remains in Git history.
