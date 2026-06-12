# Sprint 32 preparation — Local DNS, local server, and invitation onboarding

Status: implemented locally for Sprint 32 dogfood verification. This is an implementation/evidence note, not a canonical product specification. Fachliche product truth remains the pinned Weave Specification Corpus in `specs/weave-specs.lock.json`; this plan derives the next local dogfood slice from repo state, Sprint 31 evidence, and Massimo's local DNS correction.

## Sprint goal

Make the local dogfood stack DNS-first for `*.weave.test`, keep any LAN host/IP input non-canonical, bootstrap the local CA from `weave.test`, and prove that a member can join Weave by invitation link, QR code, or normal credential login without seeing raw provider configuration.

## Governing context

- Current branch: `feat/enterprise-app-start-ux`.
- Current dogfood contract: DNS-first `weave.test` / `*.weave.test`; LAN IPs are not canonical app, issuer, service, or CA URLs.
- New operator fact: Massimo added LAN DNS for `*.weave.test`.
- Product direction: Weave remains provider-neutral and organization-first. Normal members consume invite/auth URL plus `/api/platform/config`; they must not configure Matrix, Nextcloud, raw provider URLs, secrets, diagnostics, or admin setup details.
- Sprint 31 baseline: physical iPhone LAN dogfood used a single LAN host/IP path-routed fallback because local DNS could not be assumed.
- Sprint 32 correction: local DNS can now be assumed for Massimo's LAN dogfood profile, so DNS names become canonical. The LAN host/IP may remain an operator input for SAN/debug compatibility, but it is not an advertised app/service/CA URL truth.

## Current working tree already touched

The current working tree contains local stack fixes that should be reconciled, not discarded:

- `infra/weave-workspace/01-infrastructure/main.tf`
  - Reconciled app, API, issuer, Matrix, files, and CA public outputs to DNS-first `weave.test` / `*.weave.test` values.
  - Keeps LAN host/IP input non-canonical; it must not change generated app-start, issuer, redirect, Matrix, or CA URLs.
  - Passes the generated local CA into Matrix and Nextcloud modules.
- `infra/weave-workspace/01-infrastructure/outputs.tf`
  - Updates generated app/runtime outputs toward client-facing URL values.
- `infra/weave-workspace/01-infrastructure/templates/Caddyfile.tpl`
  - Uses DNS-first default SNI on `weave.test`.
  - Adds HTTP CA download at `http://weave.test:44080/weave-local-ca.pem` and HTTPS CA download at `/weave-local-ca.pem`.
  - Keeps service vhosts DNS-first for `/api`, `/auth`/Keycloak, Matrix/MAS, `/files`, and `/calendar`.
- `infra/weave-workspace/01-infrastructure/variables.tf`
  - Adds `local_lan_host` input.
- `infra/weave-workspace/02-keycloak-setup/main.tf`
  - Uses client-facing Keycloak/MAS public URLs.
- `infra/weave-workspace/02-keycloak-setup/variables.tf`
  - Adds local LAN host input.
- `infra/weave-workspace/install.sh`
  - Persists DNS-first app/runtime env values.
  - Generates a local CA and leaf certificate when needed.
  - Regenerates the leaf certificate when required SANs are missing.
  - Installs the local CA into Nextcloud.
  - Prints CA download and app/server URL hints.
- `infra/weave-workspace/operator-check.sh`
  - Asserts DNS-first runtime/app config paths.
- `infra/weave-workspace/smoke-test.sh`
  - Asserts DNS-first platform config and smoke targets.
- `client/ios/Podfile.lock`
  - Changed in the working tree; not yet assessed as part of this sprint slice.

## Verified Sprint 32 local evidence

DNS-first evidence from the local stack:

- `./install.sh` completed with `TF_VAR_local_lan_host=''` and generated DNS-first app config.
- `./smoke-test.sh` passed, including real Keycloak password-grant token minting, authenticated backend `/me`, authenticated `/organization/manifest`, and DNS-first platform config assertions.
- `./operator-check.sh` passed.
- `curl http://weave.test:44080/weave-local-ca.pem` returned `200`.
- `curl --cacert ... https://weave.test:44443/weave-local-ca.pem` returned `200`.
- `curl --cacert ... https://api.weave.test:44443/api/platform/config` returned `200` and DNS-first URLs.
- `openssl s_client -connect api.weave.test:44443 -servername api.weave.test -CAfile .../weave-local-ca.pem -verify_hostname api.weave.test` returned `Verify return code: 0 (ok)`.
- `flutter test test/features/onboarding/member_handoff_test.dart test/features/onboarding/consume_member_handoff_test.dart` passed for invite-link and deterministic QR-payload contracts.

## Desired local dogfood contract

DNS-first path:

- LAN DNS resolves these names to the local stack host:
  - `weave.test`
  - `api.weave.test`
  - `auth.weave.test`
  - `matrix.weave.test`
  - `files.weave.test`
  - `admin.weave.test`
- The local CA signs one leaf certificate that covers:
  - `weave.test`
  - `*.weave.test`
  - the concrete service hostnames above if wildcard coverage is not enough for tooling checks
- Public app/server config uses DNS-first URLs:
  - Product: `https://weave.test:44443` when isolated ports are active, or `https://weave.test` on canonical ports.
  - API: `https://api.weave.test:44443/api` or canonical-port equivalent.
  - Auth/IAM: `https://auth.weave.test:44443/realms/weave` or canonical-port equivalent.
  - Matrix: `https://matrix.weave.test:44443` or canonical-port equivalent.
  - Files technical fallback: `https://files.weave.test:44443` or canonical-port equivalent.
- Native app startup uses `/join` and `/api/platform/config`; the app does not derive provider topology from a guessed base URL.
- Services that call back into local HTTPS trust the same central local CA instead of disabling verification.

IP fallback and CA bootstrap path:

- HTTP CA bootstrap is advertised DNS-first only:
  - `http://weave.test:44080/weave-local-ca.pem`
- LAN IP URLs are non-canonical and must not appear in generated app config, platform config, issuer/redirect metadata, smoke/operator defaults, or support-safe CA instructions.
- The iPhone flow remains explicit: install and fully trust the local CA once; Safari/AppAuth and the app use system trust; no hidden certificate bypass is accepted.

## Ordered backlog with acceptance criteria

### 1. Local infra setup — DNS-first `*.weave.test` profile

Acceptance criteria:

- `install.sh` supports a DNS-first local profile where generated public and CA URLs stay on `weave.test` / `*.weave.test`; stale LAN-IP inputs do not create a second truth.
- Generated Caddy config serves `weave.test`, `api.weave.test`, `auth.weave.test`, `matrix.weave.test`, `files.weave.test`, and `admin.weave.test` over the same central local CA.
- The generated certificate includes `DNS:weave.test` and `DNS:*.weave.test`; if compatibility needs concrete names, the concrete service hosts are included too.
- `openssl s_client -connect api.weave.test:44443 -servername api.weave.test -CAfile .../weave-local-ca.pem -verify_hostname api.weave.test` verifies successfully.
- `curl --cacert ... https://api.weave.test:44443/api/platform/config` returns `200` and DNS-first URLs.
- The DNS-first CA bootstrap endpoint `http://weave.test:44080/weave-local-ca.pem` returns the CA with `200`; IP CA advertisement is not canonical evidence.
- No service uses `--insecure`, disabled TLS validation, or certificate-ignore flags as final evidence.

Recommended issue draft:

```markdown
Title: infra(local): make dogfood stack DNS-first for *.weave.test with DNS CA bootstrap

Body:
Implement the Sprint 32 local DNS correction. The local stack must use `*.weave.test` public URLs now that LAN DNS exists. Root CA bootstrap is also DNS-first via `weave.test`; LAN IPs are non-canonical debug data only.

Acceptance:
- `install.sh` has a DNS-first mode/profile for `weave.test`, `api.weave.test`, `auth.weave.test`, `matrix.weave.test`, `files.weave.test`, and `admin.weave.test`.
- Generated Caddy certificate covers `DNS:weave.test`, `DNS:*.weave.test`, and concrete service hosts as needed.
- `/weave-local-ca.pem` is advertised over HTTP on `weave.test`.
- `curl --cacert ... https://api.weave.test:44443/api/platform/config` returns `200` and DNS-first service URLs.
- `openssl s_client -servername api.weave.test -verify_hostname api.weave.test` verifies successfully with the generated CA.
- `./smoke-test.sh` and `./operator-check.sh` pass in DNS-first mode.

Evidence:
- `git diff -- infra/weave-workspace`
- `caddy fmt --diff` for the generated Caddyfile
- DNS-first curl and openssl commands above
- `./smoke-test.sh`
- `./operator-check.sh`
```

### 2. Local server setup — platform config and issuer alignment

Acceptance criteria:

- Backend `/api/platform/config` returns DNS-first public URLs when the DNS-first local profile is active.
- Backend issuer validation expects `https://auth.weave.test[:port]/realms/weave`; JWKS can still be fetched over the internal Docker network.
- Keycloak realm/client config advertises DNS-first issuer, redirect, logout, and app auth URLs.
- Matrix/MAS auth metadata and well-known endpoints advertise DNS-first Matrix/Auth endpoints.
- Nextcloud OIDC discovery and bearer validation trust the same local CA and do not require insecure local HTTP.
- Server-side tests or smoke checks prove that the public issuer in tokens matches backend validation.

Recommended issue draft:

```markdown
Title: server(local): align platform config, OIDC issuer, and provider callbacks to DNS-first local URLs

Body:
Ensure the local backend/server setup consumes the DNS-first local contract instead of exposing a single LAN-IP origin when `*.weave.test` is available.

Acceptance:
- `/api/platform/config` returns DNS-first product, API, auth, Matrix, files, and calendar URLs.
- Keycloak issuer is `https://auth.weave.test[:port]/realms/weave` and backend token validation accepts that issuer.
- Internal JWKS fetch remains Docker-network local and does not change the public issuer.
- MAS/Matrix metadata uses DNS-first public URLs.
- Nextcloud OIDC provider validation succeeds with the central local CA installed in the container.
- Smoke evidence includes authenticated flow readiness or the narrowest available token/issuer check.

Evidence:
- `curl --cacert ... https://api.weave.test:44443/api/platform/config`
- Keycloak discovery curl against `auth.weave.test`
- Matrix well-known/auth metadata curl against `matrix.weave.test`
- `./smoke-test.sh`
```

### 3. Central local CA consumed by services and app

Acceptance criteria:

- The generated CA path is stable: `infra/weave-workspace/01-infrastructure/.generated/caddy/certs/weave-local-ca.pem`.
- Caddy uses a leaf certificate signed by this CA.
- Nextcloud trusts this CA for OIDC discovery and bearer validation.
- Matrix/Synapse/MAS trust this CA where local HTTPS callbacks or federation-like discovery paths require it.
- The app/native device path documents that the same CA must be installed into iOS trust settings before SSO.
- The app does not introduce certificate-pinning bypass, custom trust-all HTTP clients, or hidden fallback to insecure HTTP.
- Smoke/operator evidence includes both service-side and client-side trust expectations.

Recommended issue draft:

```markdown
Title: local-ca: share one generated development CA across Caddy, services, and native app setup

Body:
Prove that local TLS is anchored in one generated Weave Local Development CA consumed by services and by the native device trust path.

Acceptance:
- Generated CA and leaf files are created together and fail closed when inconsistent.
- Leaf cert covers DNS-first hosts.
- Nextcloud and Matrix/Synapse/MAS containers consume the CA where needed.
- Operator output gives one support-safe CA installation instruction for iPhone/native devices.
- No final evidence uses disabled certificate verification.
- Documentation explains that production/customer links require publicly trusted TLS; the generated CA is local-dev only.

Evidence:
- `openssl x509 -in .../weave.test.pem -noout -text` SAN excerpt
- service trust setup diff
- `openssl s_client` hostname verification for `api.weave.test`
- CA download curl over `weave.test`
```

### 4. App discovery and config endpoint — DNS-first join consumption

Acceptance criteria:

- The member app accepts `https://weave.test[:port]/join?...` and/or `https://join.<tenant-domain>/join?...` when that domain is LAN DNS-resolvable and covered by the local CA.
- The parser no longer treats all `.local` hostnames as forbidden when the host is the intentional `weave.test` local dogfood domain or a configured LAN DNS domain.
- Loopback, `0.0.0.0`, container-only names, and unrelated Mac-only names remain rejected.
- The app fetches `/api/platform/config` and saves DNS-first API/auth/facade config.
- The app continues to reject credential-bearing query parameters and raw provider URLs.
- Tests cover DNS-first `.weave.test`, rejected LAN-IP app-start links, production DNS, and forbidden loopback/container cases.

Recommended issue draft:

```markdown
Title: client(onboarding): accept DNS-first weave.test join links without weakening unsafe local-target guards

Body:
Update the member handoff parser and onboarding tests for Sprint 32. Massimo's LAN now resolves `*.weave.test`, so `.weave.test` must be valid for the local dogfood profile while loopback and container-only targets stay blocked.

Acceptance:
- `https://weave.test:44443/join?...` parses for local dogfood when backed by `/api/platform/config`.
- `https://api.weave.test:44443/api/platform/config` is accepted as explicit discovery URL.
- `localhost`, `127.0.0.1`, `0.0.0.0`, `host.docker.internal`, and bare container names remain rejected.
- Credential-bearing query keys and provider-specific URL query keys remain rejected.
- Tests cover DNS-first local, IP fallback, production DNS, and forbidden host classes.

Evidence:
- `cd client && flutter test test/features/onboarding/member_handoff_test.dart test/features/onboarding/consume_member_handoff_test.dart`
- Relevant client analysis gate if surrounding code changes require it.
```

### 5. Invitation link, QR, and IAM URL onboarding flow

Acceptance criteria:

- Admin/operator can produce a support-safe invitation handoff containing only refs, organization/workspace context, and discovery URL; no secrets or provider diagnostics appear in the link or QR payload.
- The same payload can be rendered as a URL, QR code source, and app/deep link fallback.
- Member can alternatively enter the organization IAM/auth URL, such as `https://auth.weave.test[:port]/realms/weave`, and the app can discover or derive the platform config through an explicit supported contract rather than guessing provider topology.
- Invitation flow is inspired by organizations: invite link/QR for most members; IAM URL plus credentials/SSO for credential-based entry; both land in the same authenticated workspace/home path.
- Expiry, revocation, tenant/workspace binding, and audit are specified or explicitly deferred with follow-up issues before production/customer claims.
- Accessibility: QR has adjacent copyable link text and screen-reader label; manual IAM URL entry has validation errors that do not rely on color alone.
- Acceptance is mapped to e2e/product scenarios for member join through invite, organization URL, or deep link.

Recommended issue draft:

```markdown
Title: onboarding: prepare invite link, QR, and IAM URL member join flow

Body:
Implement the first test invitation flow for Weave local dogfood. Members should join through an invitation link or QR code, or manually enter the IAM/auth URL and authenticate with credentials/SSO. The app must still consume `/api/platform/config` and must not expose raw provider setup to normal members.

Acceptance:
- Operator/admin flow emits a support-safe invite URL and QR payload with no secrets, raw provider diagnostics, Matrix URL, Nextcloud URL, or credential URL.
- Native app consumes the invite/deep link, fetches `/api/platform/config`, starts SSO, and lands in workspace/home.
- Manual IAM URL entry is supported through a documented discovery path and does not ask for provider internals.
- Invite link/QR and IAM URL paths share the same authenticated organization manifest/capability state after login.
- Tests or e2e mapping cover link, QR payload, IAM URL entry, invalid/expired invite, and no-secret redaction.
- QR/manual entry UI is accessible and localizable.

Evidence:
- onboarding unit/widget tests
- acceptance/e2e scenario mapping update
- local dogfood smoke command with emitted invite/QR payload
```

## Implemented in the first DNS-first slice

- Generated app, service, issuer, Matrix, files, and CA URLs are DNS-first on `weave.test` / `*.weave.test`.
- Stale LAN-IP inputs are treated as non-canonical and smoke/operator checks fail if they leak into generated public app/service URLs.
- Certificate SAN generation includes `weave.test`, `*.weave.test`, and concrete service hostnames.
- Caddy advertises CA bootstrap on `http://weave.test:44080/weave-local-ca.pem`.
- App handoff validation permits the intentional `weave.test` / `*.weave.test` LAN DNS domain and rejects LAN-IP app-start links, loopback, container-only names, and unrelated `.local` names.
- Client tests cover invite link onboarding, deterministic QR payload/decode, DNS-first app-start config consumption, and existing credential-login integration coverage.
- `client/ios/Podfile.lock` remains pre-existing/unassessed working-tree state.

## Suggested follow-up issue DAG

- First: review and land the local DNS-first infra/client slice.
- Second: run the same evidence on the physical iPhone after installing/trusting `Weave Local Development CA`.
- Third: improve visible invite/QR/IAM URL onboarding UX beyond the deterministic parser/discovery contracts.
- Final: sprint closure report with Massimo's physical-device result.

## Risks and open decisions

- `.local` is often mDNS-reserved. Massimo's LAN DNS resolves `*.weave.test`, but the app and docs must distinguish intentional LAN DNS from unsafe Mac-only assumptions.
- iOS Universal Links with self-signed local TLS may not verify like production. Keep `weave:/join?...` as local-dev fallback unless device testing proves Universal Links work with the trusted local CA and association file.
- A wildcard cert may not satisfy every service/tooling check if the checked hostname is the apex `weave.test`; include both apex and wildcard.
- Local CA trust is intrusive on a phone. Instructions must be explicit and local-dev-only, and the app must surface certificate trust failures honestly.
- Manual IAM URL entry needs a real discovery contract. Do not let the app infer API/Matrix/Files endpoints from an auth URL by string replacement unless that is explicitly specified and tested.
- GitHub milestone/issue creation is an external write. I verified GitHub read access and found no open Sprint 32 milestone; create the milestone/issues only after Massimo approves the prepared issue bodies or confirms that repo policy allows immediate external writes.

## Lightweight verification targets for the first implementation PR

- `git status --short`
- `git diff -- infra/weave-workspace client/lib/features/onboarding client/test/features/onboarding docs e2e`
- `cd infra/weave-workspace && ./install.sh`
- `cd infra/weave-workspace && ./smoke-test.sh`
- `cd infra/weave-workspace && ./operator-check.sh`
- `curl http://weave.test:44080/weave-local-ca.pem`
- `curl --cacert infra/weave-workspace/01-infrastructure/.generated/caddy/certs/weave-local-ca.pem https://api.weave.test:44443/api/platform/config`
- `openssl s_client -connect api.weave.test:44443 -servername api.weave.test -CAfile infra/weave-workspace/01-infrastructure/.generated/caddy/certs/weave-local-ca.pem -verify_hostname api.weave.test`
- `cd client && flutter test test/features/onboarding/member_handoff_test.dart test/features/onboarding/consume_member_handoff_test.dart`
- `./gradlew acceptanceContract` after e2e/scenario mapping changes.

## Tomorrow's operator commands

Use these after deciding whether to implement the DNS-first fix on the current branch or split it into issue branches:

```sh
git status --short

# Review this sprint prep artifact.
sed -n '1,260p' docs/sprint-32-local-dns-server-and-invite-onboarding-plan.md

# Confirm DNS from the Mac and from the iPhone network if possible.
dig +short weave.test
dig +short api.weave.test
dig +short auth.weave.test

# Re-run the stack after the DNS-first implementation updates.
cd infra/weave-workspace
./install.sh
./smoke-test.sh
./operator-check.sh
curl http://weave.test:44080/weave-local-ca.pem
curl --cacert 01-infrastructure/.generated/caddy/certs/weave-local-ca.pem \
  https://api.weave.test:44443/api/platform/config
openssl s_client \
  -connect api.weave.test:44443 \
  -servername api.weave.test \
  -CAfile 01-infrastructure/.generated/caddy/certs/weave-local-ca.pem \
  -verify_hostname api.weave.test

# Then validate app join parsing and DNS-first app-start consumption.
cd ../../client
flutter test test/features/onboarding/member_handoff_test.dart test/features/onboarding/consume_member_handoff_test.dart
```
