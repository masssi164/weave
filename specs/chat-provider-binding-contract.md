# Chat provider binding for the #1470 release

Authority: pinned `weave-specs` release profile at `71a2093d91ad300bc733ede66080bddc90f97e60`, Weave #1470 and #1475.

The Matrix Client-Server endpoint is a Weave endpoint. A member request resolves the current organization before Chat is admitted. The selected southbound `ChatProviderPort` must agree with one durable active `(organization, chat)` binding. A missing, retired, foreign, or mismatched binding is unavailable to member traffic, even when a process-wide adapter is configured and healthy. The global provider-selection record is legacy administration metadata; it cannot override this organization binding.

The present deployment admits one configured organization. On an empty database, startup creates that organization's Chat binding for the configured adapter and its profile reference. Restart verifies the existing binding instead of replacing it. A changed adapter or profile reference is a fail-closed startup conflict. During #1470, activation cannot rotate or replace a Chat binding that already exists; adoption and verified replacement belong to #1498. This prevents a staged or accidentally reconfigured provider from receiving member traffic.

The active binding stays private to Server. Matrix discovery, User and Admin OpenAPI artifacts, Flutter and Weaver tools do not expose its provider identifier, credentials, revision or configuration reference. A provider choice cannot change the public Matrix URL or stable room/event references.

Verification requires a real PostgreSQL binding-repository test for restart, foreign organization and blocked transition; a Server admission test for missing/mismatched binding; exact-head generated-contract and protocol checks; and an isolated Matrix client flow. The binding check alone does not qualify the guarded Matrix support profile or prove provider replacement.
