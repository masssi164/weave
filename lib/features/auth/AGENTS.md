# Auth Feature Instructions

`auth` is a foundational feature for onboarding and setup, but it is not an `AppShell` tab. Setup flows may trigger auth work, but token and session ownership stays inside this feature.

Authentik OIDC rules:
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

Infrastructure-aligned app OIDC defaults:
- keep the app-native redirect scheme aligned to infrastructure: `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout`
- keep the app client ID aligned to infrastructure: `weave-app`
- treat the app client as infra-managed; do not reintroduce manual client-registration or client-ID setup UI unless the product requirements explicitly change
