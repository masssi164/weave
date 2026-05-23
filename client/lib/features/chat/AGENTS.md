# Chat Feature Instructions

`chat` owns Matrix integration boundaries. Raw Matrix SDK rooms, events, and sync payloads must be mapped inside this feature before presentation consumes them.

Rules:
- keep Matrix event mapping in `data/` and chat-facing entities/view models in `domain/` or presentation-facing adapters
- do not leak raw Matrix SDK objects into widgets or into other features
- keep E2EE, device/session state, and decrypted-content handling inside chat internals
- presentation code must not bypass Matrix SDK crypto or sync rules
- keep Matrix crypto bootstrap, recovery, and verification flows behind the chat-owned `MatrixClient` and `ChatSecurityRepository` boundaries
- use `Client.getCryptoIdentityState()` as the first source of truth for whether the current device is initialized vs connected to the existing crypto identity
- if Matrix verification enters the SDK `askSSSS` state, surface it as a chat-owned recovery/unlock step instead of exposing raw SDK state names in UI
- keep recovery keys and passphrases user-held; do not imply app-local storage is sufficient across reinstall or device-restore scenarios

Sync and offline behavior:
- preserve timeline continuity across sync updates
- model pending/local-send state and retryable failures explicitly
- support offline or stale-cache rendering without collapsing the whole timeline

Current security surfaces:
- `presentation/widgets/chat_security_settings_section.dart` is the main UI for Matrix bootstrap, recovery, and verification actions even though it is hosted from Settings
- `presentation/widgets/chat_security_banner.dart` should only summarize actionable encrypted-room risk and route users to Settings for details
- verification UX currently supports SAS emoji/numbers and recovery-key unlock for SSSS-backed verification continuation; QR flows are intentionally unsupported until the client advertises those methods explicitly

Accessibility:
- chat rows must read in a logical order for sender, content, time, and status
- merge semantics for a message bubble when separate child announcements would be noisy
- avoid duplicate spoken output for decorative avatars, timestamps, or status icons
- security actions and verification dialogs must preserve `48x48` tap targets and expose clear button semantics because they are high-risk recovery flows
