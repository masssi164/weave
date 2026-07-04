# Files native provider setup

Weave Files must integrate with native OS file surfaces through Weave-owned facades, not through raw storage-provider setup.

## Current executable slice

`GET /api/files/native-provider-setup` returns authenticated, support-safe setup metadata for native file providers:

- iOS boundary: File Provider extension.
- Android boundary: DocumentsProvider / Storage Access Framework.
- Flutter/native bridge role: setup, status, and revoke only.
- File IO proof hooks: `GET /api/files`, `GET /api/files/{id}/download`, and `POST /api/files/upload`.
- Support-safe blocked states for the remaining work: native extension/provider implementation, per-device token revocation, and physical-device provider proof.

The response deliberately contains only Weave-owned API paths. It must not include provider hostnames, WebDAV URLs, provider credentials, bearer tokens, or provider diagnostics.

## Product boundary

The member app may show native setup status and let the user start or revoke native setup. It must not become a WebDAV client and must not store storage-provider credentials. Native OS file IO belongs in the iOS File Provider extension or Android DocumentsProvider, backed by Weave file facade endpoints.

## First dogfood proof

The first proof is acceptable when a native-provider boundary can list/open at least one Weave file through the Weave facade, or when this setup contract is exercised with a documented first-step proof and the remaining extension work is explicitly blocked in evidence.

Full native availability remains blocked until physical-device evidence proves the OS provider boundary and revocation behavior.
