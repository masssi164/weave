# Auth Feature Instructions

`auth` is a foundational feature for onboarding and setup, but it is not an `AppShell` tab. Setup flows may trigger auth work, but token and session ownership stays inside this feature.

Native OIDC boundary:
- keep authorization, token exchange, refresh, and logout logic inside `auth`
- expose auth state in a form onboarding/setup can consume without duplicating auth internals
- do not let presentation code talk directly to token endpoints or persistence

Token handling and storage:
- keep token models, expiry checks, and refresh decisions in `domain/` and `data/`
- persist only what is needed for session restoration
- storage access must live behind auth repositories/contracts, not in widgets or other features
- do not introduce ad hoc token persistence in unrelated features

Session expectations:
- refresh proactively when expiry is known and a request would otherwise fail
- treat refresh failure as an invalid session and collapse cleanly to signed-out state
- keep auth reusable for setup and later authenticated features without coupling it to `onboarding`
- downstream integrations may consume restored app OIDC sessions to derive their own protocol access, but auth still owns token refresh, storage, and validity decisions
- do not let non-auth features or integrations persist duplicate copies of app OIDC tokens

Accepted native OIDC configuration:
- keep the app-native redirect scheme aligned to the accepted contract: `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout`
- resolve the validated issuer and native client ID from the single OrgManifest v2 bootstrap returned by the generated User `GET /api/platform/config` operation
- members enter only the public organization URL or an invitation; do not expose manual issuer, realm, client ID or provider configuration, or introduce a second bootstrap contract
- use the system browser and Authorization Code with PKCE; preserve passkey and MFA compatibility and never collect passwords inside Flutter

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance from GitHub issues/milestones, repo specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
