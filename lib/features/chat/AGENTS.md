# Chat Feature Instructions

`chat` owns Matrix integration boundaries. Raw Matrix SDK rooms, events, and sync payloads must be mapped inside this feature before presentation consumes them.

Rules:
- keep Matrix event mapping in `data/` and chat-facing entities/view models in `domain/` or presentation-facing adapters
- do not leak raw Matrix SDK objects into widgets or into other features
- keep E2EE, device/session state, and decrypted-content handling inside chat internals
- presentation code must not bypass Matrix SDK crypto or sync rules

Sync and offline behavior:
- preserve timeline continuity across sync updates
- model pending/local-send state and retryable failures explicitly
- support offline or stale-cache rendering without collapsing the whole timeline

Accessibility:
- chat rows must read in a logical order for sender, content, time, and status
- merge semantics for a message bubble when separate child announcements would be noisy
- avoid duplicate spoken output for decorative avatars, timestamps, or status icons
