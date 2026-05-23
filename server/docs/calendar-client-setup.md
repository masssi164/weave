# Calendar client setup and profile download plan

Weave should make Calendar a fully integrated Weave feature while still allowing users to connect native calendar clients where platforms support it. The app UI remains backed by the `/api/calendar/**` facade; external client setup is a separate standards bridge, not a direct Flutter-to-CalDAV product shortcut.

## Research findings

### Apple platforms: `.mobileconfig`

Apple supports a Calendar configuration profile payload (`PayloadType = com.apple.caldav.account`) for iOS, iPadOS, macOS, Shared iPad user channel, and visionOS. The payload can carry the CalDAV host, port, SSL flag, principal URL, account description, username, and optionally a password. Apple documents that omitted passwords are entered by the user during install.

Active product implications:

- A Weave profile download can produce a `.mobileconfig` for Apple platforms.
- The profile must be generated per user and should be signed before end-user release to avoid scary installation warnings and tampering concerns.
- We must not embed the backend service account credential or a permanent user secret in a static profile.
- Safe credential choices are either:
  - omit the password and let the user enter a revocable per-client app password; or
  - generate/use a revocable scoped setup token/app password with clear revocation.

### Android: no universal native CalDAV profile

Android does not provide a universal OS-level CalDAV profile install equivalent. DAVx5 is the practical open CalDAV/CardDAV sync adapter. DAVx5 can be launched with explicit intents or `davx5://`, `caldav(s)://`, and `carddav(s)://` links, and can use the Nextcloud login flow so each client receives its own app password. ICS/webcal subscriptions are a separate one-way path; DAVx5 recommends ICSx5 for HTTP/Webcal `.ics` subscriptions.

Active product implications:

- Weave should expose a secret-free DAVx5 setup URL and copyable CalDAV discovery URL.
- Android two-way sync depends on DAVx5 or another CalDAV sync adapter, not on Android Calendar alone.
- Webcal/ICS should be positioned as read-only subscription/download, not full calendar integration.

### Desktop: mixed support

Desktop support is fragmented:

- macOS can use the same `.mobileconfig` approach as iOS/iPadOS or manual CalDAV account setup.
- Thunderbird supports CalDAV calendars and can use the discovery/principal URL.
- GNOME/KDE calendar stacks can use CalDAV through their account/calendar integrations.
- Outlook on Windows generally does not support CalDAV natively without an add-in; read-only ICS/webcal can help for subscription-only use cases.

Active product implications:

- Provide copyable CalDAV discovery/principal URLs and username for manual setup.
- Provide a future read-only webcal/ICS feed for clients that cannot do CalDAV, backed by revocable tokens.
- Do not claim universal two-way native desktop support.

### Nextcloud credential boundary

Nextcloud's Login Flow / Login Flow v2 issues per-client credentials/app passwords and lets users revoke clients. It is a safer fit than reusing the user's primary password or embedding backend credentials. Nextcloud also documents app password conversion and deletion endpoints for clients that already authenticate.

Active product implications:

- External clients need a per-client Nextcloud credential or future Weave-scoped token accepted at the CalDAV/subscription boundary.
- The backend must never expose its service-account CalDAV credential to users or generated profiles.
- If Weave generates a token itself, it needs revocation, expiry/rotation, audit metadata, and a clear mapping to read/write or read-only scope.

## First backend slice

`GET /api/calendar/client-setup` now exposes authenticated, secret-free setup metadata:

- current calendar scope (`workspace`)
- explicit access model metadata (`workspace-calendar`, private personal calendars unavailable until a reviewed provisioning/sharing/delegated-token model exists)
- profile/feed credential readiness metadata showing Apple profile signing, revocable credentials, and read-only subscription tokens are still blocked
- current user's external calendar username
- CalDAV discovery and principal URLs
- platform option matrix for Apple `.mobileconfig`, Android DAVx5, desktop manual CalDAV, and webcal/ICS subscription
- explicit credential policy that no password, bearer token, app password, or backend actor credential is returned

This endpoint is intentionally not a profile generator yet. It creates the contract surface the app can show in a feature-gated profile/calendar-settings screen while keeping unsafe paths closed.

## Backend profile endpoint scaffold

`GET /api/calendar/client-setup/apple.mobileconfig` is now reserved for Apple profile downloads. It is authenticated through the same `weave:workspace` API boundary, but it intentionally returns `503 calendar-apple-profile-unavailable` until real profile signing is wired. The backend keeps an unsigned, no-secret profile renderer under test so the eventual signer has a stable input artifact, but unsigned profiles are not downloadable from the API.

The renderer includes only CalDAV host, port, SSL, principal URL, display label, username, and profile metadata. It deliberately omits `CalDAVPassword` and never reads backend actor credentials, bearer tokens, or static setup secrets. Password-bearing profiles remain blocked until a revocable per-client credential issuance and revocation model exists.

## Active implementation sequence

1. **Contract and setup metadata (this PR):** expose `/api/calendar/client-setup` and document the platform/security model.
2. **Access/credential readiness contract:** expose explicit access-model and credential-readiness fields so clients can show honest blocked states before any profile/feed download path exists. Keep `{user}` CalDAV templates fail-closed until tested.
3. **Private/user calendar access model:** resolve `weave-backend#52` by choosing service-shared workspace calendar + optional per-user sharing, Nextcloud Login Flow/app passwords, or a Weave token bridge.
4. **Apple profile generator:** keep the authenticated `.mobileconfig` endpoint fail-closed until profile signing is configured; then serve the tested no-secret profile through a signer, or include only a revocable scoped credential once issuance/revocation exists. Add tests proving no backend actor credential can appear in the profile.
5. **Android setup handoff:** add app/UI support for DAVx5 setup URI, manual fallback instructions, and read-only subscription copy once tokenized ICS exists.
6. **Read-only subscription tokens:** implement revocable webcal/ICS feed tokens for clients that cannot do CalDAV; label them one-way.
7. **Calendar product promotion:** once create/read/update/delete and profile setup are safe, enable Calendar as a active module in app capability/navigation tests.
8. **Boards/Tasks promotion:** after the provider-neutral API, connector skeleton, and OpenProject-first read-sync seam are tested, enable Boards/Tasks with non-drag accessible movement as a hard release gate. Vikunja/Deck remain comparison/fallback paths.

## References

- Apple Calendar payload settings: `com.apple.caldav.account`, host/port/SSL/principal/username/password behavior.
- DAVx5 integration docs: explicit/implicit intents, `davx5://` URLs, Nextcloud login flow support.
- DAVx5 ICS FAQ: webcal/ICS is one-way subscription, not two-way CalDAV sync.
- Nextcloud Login Flow docs: per-client credentials/app passwords and revocation.
