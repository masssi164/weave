# Chat implementation instructions

Follow the root Server instructions and Java/Spring standard. Chat owns actual application,
HTTP, provider, protocol and persistence runtime code; do not import App or future feature
implementations. Preserve the sole NativeMatrixCore Java name for the existing JNI ABI.

Use the owning unit and context lanes described in README.md. Keep normal envelope persistence
in JPA; preserve the narrowly tested PostgreSQL aggregate fence and key-claim locking semantics.
Never put client private cryptographic keys, decrypted payloads or recovery secrets in Server
state or diagnostics. Shared session revocation belongs to Core Security.

Future decisions/meetings use the narrow conversation admission API; they own their feature
behavior and handlers. New protocol operations must update the consumed MatrixProtocolCodec
contract and actual Ruma/provider tests, rather than introducing a Java fallback.

Installation differs from readiness and member policy. Without Chat the required manifest protocols
object has no Matrix URL and platform status has no Matrix contribution. Installed Chat still
validates required facade metadata, even when disabled; never use a placeholder URL or load Chat
into an excluded artifact. Core session revocation remains independent of this protocol boundary.
