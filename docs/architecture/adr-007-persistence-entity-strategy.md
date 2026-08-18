# Superseded persistence architecture document

This path is retained so older links remain understandable. It is not current architecture authority.

Use [Weave data-sovereignty core](data-sovereignty-core.md) and [Canonical transfer kernel](canonical-transfer-kernel.md). Issue #1320 owns Flyway/JPA implementation.

JPA entities are adapter-private persistence representations. Flyway owns schema evolution; Hibernate validates. Historical code-first authority remains in Git history only.
