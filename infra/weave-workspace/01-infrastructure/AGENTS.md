# Infrastructure Stage Guide

This stage owns Docker networking, generated runtime config files, local containers, and the shared PostgreSQL bootstrap that creates one database per service.

## Files

- `main.tf`: root orchestration, per-service database bootstrap, generated file creation, module composition, Weave backend wiring, and state-preserving `moved` blocks.
- `variables.tf`: public input contract for the infrastructure stage.
- `outputs.tf`: exported service names, URLs, and hostnames.
- `.tofu.lock.hcl`: pinned provider selections for reproducible init behavior.
- `templates/Caddyfile.tpl`: Caddy reverse proxy and TLS routing template.
- `templates/mas-config.yaml.tpl`: Matrix Authentication Service config template.
- `templates/homeserver.yaml.tpl`: Synapse delegated-auth config template.
- `templates/synapse-appservice.yaml.tpl`: private, exclusive Matrix Chat Application Service registration template.
- `modules/AGENTS.md`: map of child modules and their responsibilities.

## Child Module Responsibilities

- `modules/postgres`: shared PostgreSQL container and volume; root bootstrap logic creates the service databases inside it.
- `modules/reverse-proxy`: Caddy edge container with local TLS cert mounts.
- `modules/keycloak`: Keycloak container and storage.
- `modules/backend`: Weave backend container, OIDC environment, healthcheck, and Caddy routing.
- `modules/mailpit`: dogfood mail catcher with a persistent SQLite volume and bounded retention.
- `modules/matrix`: MAS and Synapse containers plus local CA trust for MAS.
- `modules/nextcloud`: Nextcloud container and storage.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
