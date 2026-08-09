# Native Chat closure evidence

This document is the qualification ledger for the `weave-native` Chat provider behind Weave Server's permanent northbound Matrix Client-Server facade.

## Authority boundaries

Canonical Chat remains provider-neutral behind `ChatProviderPort`. `weave-native` is the selected default provider implementation. Provider selection is strictly southbound of the canonical domain.

The Matrix Client-Server facade is an always-available Weave-owned northbound standards surface. It is not a provider, does not select Synapse or MAS, and is not feature-switched off.

The server Matrix protocol crate is `weave-matrix-protocol` and contains Ruma plus the closed JNI projection boundary only. Client cryptography and Flutter bindings live in `weave-matrix-client`; server code does not depend on Matrix SDK crypto.

Matrix routing/E2EE metadata is normalized in PostgreSQL. Tenant-wide serialized snapshots are not an accepted steady-state authority and must be removed before closure.

## Required qualification

Evidence is accepted only from the committed source tree and PostgreSQL-backed CI. Closure requires:

- server startup with the required Ruma/JNI Matrix protocol artifact;
- fail-fast, support-safe diagnostics for a missing or incompatible protocol artifact;
- `weave-native` Chat operation without Synapse or MAS;
- provider switching without a change to the northbound Matrix base URL or canonical identifiers;
- restart continuity;
- multi-instance one-time-key claim contention;
- durable, ordered to-device retry and acknowledgement/progress behavior;
- exact device- and endpoint-scoped idempotency replay including provider-binding revision;
- commit-ordered sync snapshot/pagination concurrency;
- opaque encrypted-event persistence;
- receipt/redaction continuity;
- two-device E2EE proof showing ciphertext-only server state;
- absence of server-side decryption and private client key material;
- independent successful builds/tests for `weave-matrix-protocol` and `weave-matrix-client`;
- SBOM/provenance/checksum evidence for native server artifacts on every supported target.

No external-provider content migration, compatibility reader, or dual write is part of this closure.

## Evidence recording

The final closure revision records the accepted specification commit, implementation commit, Flyway schema version, exact commands, GitHub Actions run identifiers, native artifact checksums, supported platform matrix, and immutable test artifacts. Until those references are present and green this document is a qualification checklist, not a readiness claim.
