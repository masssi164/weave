# Infrastructure Modules Guide

These child modules keep the root stage orchestration-only.

## Modules

- `postgres/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: PostgreSQL image, volume, and container.
  - `variables.tf`: database bootstrap and init SQL inputs.
  - `outputs.tf`: container and volume identifiers.
- `reverse-proxy/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: Caddy image, volumes, published HTTP/HTTPS ports, Caddyfile mount, and TLS cert mount.
  - `variables.tf`: proxy, Caddyfile, TLS cert, and hostname inputs.
  - `outputs.tf`: container and volume identifiers.
- `keycloak/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: Keycloak image, volume, container, and public URL wiring.
  - `variables.tf`: database, hostname, and admin inputs.
  - `outputs.tf`: container and volume identifiers.
- `backend/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: Weave backend image, container, healthcheck, OIDC environment, and Docker network aliases.
  - `variables.tf`: image, port, hostname, and OIDC contract inputs.
  - `outputs.tf`: container identifier.
- `mcp/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: Spring AI MCP container, healthcheck, OIDC environment, and backend authority wiring.
  - `variables.tf`: image, ports, backend, and OIDC inputs.
  - `outputs.tf`: container identifier.
- `matrix/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: MAS and Synapse images, Synapse volume, containers, and MAS local CA trust.
  - `variables.tf`: generated-file paths, ports, and hostnames.
  - `outputs.tf`: Matrix container and volume identifiers.
- `nextcloud/`
  - `AGENTS.md`: module summary and ownership notes.
  - `main.tf`: Nextcloud image, volume, container, reverse proxy trust, and local CA mount.
  - `variables.tf`: database, hostname, and admin inputs.
  - `outputs.tf`: container and volume identifiers.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
