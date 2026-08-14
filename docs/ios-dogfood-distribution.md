# iOS dogfood for active development

Dogfood is a fast development loop, not a release-distribution system. A commit is ready for human testing when `Full Compose E2E` succeeded for that exact `dogfood` SHA. There is no Candidate Cut, Fresh Start, TestFlight upload, environment approval, or release manifest in this loop.

Run the manual GitHub workflow `Prepare Human Test` with the exact dogfood SHA. It performs the complete handoff on `weave-live-mac-mini`:

1. verify the successful exact-SHA `Full Compose E2E` run;
2. start the dogfood stack through `./gradlew dogfoodUp`;
3. start it a second time and prove that the CA and leaf certificate fingerprints did not change;
4. optionally create or resend the first-owner invitation and leave its activation link only in private Mailpit at `https://mail.weave.test:44443`;
5. build the exact commit with development signing, install it over the existing app, and launch the handoff on the paired iPhone.

The only private workflow value required for the device is `WEAVE_IOS_DEVICE_ID`. The first-owner path additionally uses the existing dogfood member email secret and display-name variable. Apple distribution and App Store Connect secrets are not part of development dogfood.

## Stable device identity

Every normal iteration is an update in place. The workflow and installer preserve:

- bundle identifier `com.massimotter.weave`;
- Apple team `KNDHGC2KV6`;
- Apple Development certificate team `6RUS2Z848X`;
- the development Keychain application identity;
- the existing app container and Developer App trust.

The installer defaults to `update_in_place`. It may uninstall only when a developer explicitly selects the separate `destructive_uninstall` recovery mode; the human-test workflow never selects that mode. The build embeds the exact commit, the GitHub preparation-run reference, and a positive build number.

The active `Profile` configuration uses the Personal Team-compatible development entitlements: it preserves the Keychain application identity but omits Associated Domains, which Personal Development Teams cannot provision. The production `Release` configuration keeps the full Associated Domains entitlement. Dogfood login and handoff continue through the explicit AppAuth/custom-scheme path rather than universal links.

TLS material lives outside Docker at `/Users/flotterotter/.weave/dogfood/generated/tls`. `dogfoodUp`, `dogfoodDown`, and `dogfoodReset` must not delete or rotate it. The Weave Local Development CA therefore normally needs to be installed and trusted on the iPhone only once.

## Before pressing Run

- The iPhone is unlocked, paired with `weave-live-mac-mini`, reachable over the same WLAN, and Developer Mode is enabled.
- The Weave Local Development CA is still fully trusted.
- LAN DNS resolves `weave.test` and its subdomains to the dogfood host.
- `WEAVE_IOS_DEVICE_ID` identifies that physical phone.
- If the realm already has a human account, dispatch with `bootstrap_owner=false`; otherwise leave the default enabled.

## What the tester must actually verify

Installation is preparation, not a human-test result. The tester opens the invitation in Mailpit, activates the account in the system browser, and then verifies on the physical iPhone:

- app launch and Authorization Code with PKCE;
- normal session, refresh, logout, and login again;
- Home, Chat, Files, Calendar, Settings, and Profile navigation;
- native Files search and opening a file resource;
- native Calendar create/read/update/delete behavior;
- Chat room/message behavior, including existing encrypted-history continuity where implemented;
- Weaver/MCP discovery, grant, `files.search`, resource open, revoke with immediate rejection, regrant, and restored access when that surface is available;
- VoiceOver labels/order, keyboard/focus behavior where applicable, and identity/session continuity after relaunch.

A missing Calls or Weaver surface is recorded as `blocked`, never invented as a pass. Human outcomes remain tester statements; automated E2E and installation evidence do not replace them.

Contract marker: `HUMAN_TESTING_HAS_NO_MANIFEST_GATE`.

## Local equivalent

The physical-device portion can be repeated from the checked-out dogfood commit without TestFlight:

```sh
WEAVE_IOS_DEVICE_ID=<paired-device-id> \
WEAVE_CANDIDATE_COMMIT=<full-dogfood-sha> \
WEAVE_IOS_BUILD_NUMBER=<positive-build-number> \
WEAVE_IOS_LOCAL_CA_TRUST_STATUS=trusted \
tools/dogfood_iphone_entry.sh --run --reset-mode update_in_place --transport wifi
```

Use `tools/dogfood_ios_session_restore_smoke.sh` after login when support-safe automated evidence of a successful session restore is useful. It does not read or export Keychain secrets.
