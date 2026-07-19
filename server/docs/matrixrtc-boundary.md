# MatrixRTC Calls boundary

Weave Server does not expose a member Calls API. Calls signaling, membership, ringing, decline,
and media-key state use Matrix Client-Server v1.19 plus the revision-pinned
`weave.matrixrtc/profile-0` contract in `docs/architecture/matrixrtc-profile-0.yaml`.

## Server ownership

Weave Server owns organization policy, entitlement, current authorization, provider readiness,
consent, governed artifact rules, and support-safe audit. Matrix Authentication Service owns the
Matrix-facing Native OAuth role and uses Keycloak as its upstream organization identity provider.

The RTC Authorizer is a separate least-privilege boundary. A Matrix OpenID credential proves
identity only. Before issuing a short-lived LiveKit transport grant, the authorizer must recheck
current room membership, slot/member binding, role, organization policy, requested permissions,
expiry, and replay state. No LiveKit administration secret crosses the member boundary.

## Client and native ownership

The shared Rust/Ruma core owns Matrix discovery, OAuth, sync/E2EE, Profile-0 wire state, and key
events. LiveKit owns media transport. A thin Flutter/native coordinator maps MatrixRTC/media state
to CallKit and Android Core-Telecom lifecycle and audio routing; it does not define signaling,
authorization, or provider room administration.

The capability remains Experimental/Guarded until exact wire, third-party interoperability, RTC
authorization abuse, media E2EE, TURN/reconnect, physical-device, consent, artifact lifecycle, and
WCAG 2.2 AA plus EN 301 549 evidence passes.
