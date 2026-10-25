# Chat extraction

Canonical ledger and Matrix identity projection mappings/repositories now live in `persistence`.
Shared member/session revocation is Core Security state, independently usable without Chat.
The old generic root JPA project is retired. This is not yet complete Chat runtime extraction:
remaining Matrix/JNI, services, controllers and provider wiring are transitional in Server.

See [Server ownership](../../../docs/architecture/server-domain-ownership.md) for progress and
[Java/Spring standard](../../../docs/java-spring-standard.md) for implementation rules.
