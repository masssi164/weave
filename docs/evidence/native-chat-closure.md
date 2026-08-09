# Native Chat closure evidence

This document is the qualification ledger for `weave-native` Chat and the optional northbound Matrix facade.

## Authority boundaries

Canonical Chat remains provider-neutral behind `ChatProviderPort`. `weave-native` is the selected default implementation. The Matrix Client-Server facade is an independent northbound protocol edge and does not select Synapse or MAS.

The server Matrix protocol crate is `weave-matrix-protocol` and contains Ruma plus the closed JNI projection boundary only. Client cryptography and Flutter bindings live in `weave-matrix-client`; server code does not depend on Matrix SDK crypto.

Matrix routing/E2EE metadata is normalized in PostgreSQL. The former tenant-wide serialized snapshot is not a steady-state authority and is removed after the relational cutover migration.

## Required qualification

Evidence is accepted only from PostgreSQL-backed CI. Closure requires native-only startup with the Matrix facade disabled, restart continuity, multi-instance one-time-key claim contention, to-device retry/progress behavior, exact device-scoped idempotency replay, sync snapshot/pagination concurrency, opaque encrypted event persistence, receipt/redaction continuity, and absence of server-side decryption.

No external-provider content migration, compatibility reader, or dual write is part of this closure.
