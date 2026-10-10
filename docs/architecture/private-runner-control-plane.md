# Private runner control plane

Status: first executable architecture slice for the provider-neutral Weave pivot.

## Purpose

Weave exposes company-owned capabilities to users and agent harnesses without requiring inbound access to the company network and without moving internal credentials or implementation code into the Weave Engine.

```text
OpenClaw / approved harness
        -> Weave MCP edge
            -> Weave Engine
                -> outbound-only Runner Control API
                    -> company-derived Weave Runner image
                        -> company handlers and internal systems
```

The Engine coordinates and authorizes work. The Runner executes locally. The company owns every handler, internal endpoint and internal credential added to its derived image or runtime mounts.

## Responsibilities

### Weave Engine

The Java 21/Spring Boot server is the control and knowledge plane. PostgreSQL stores Runner registrations, public capability contracts, Tasks, fenced Leases, Artifacts and evidence-based topology observations.

The Engine never sends an arbitrary command, executable path or environment variable. A Task references only a capability ID/version that the Runner previously published from its loaded local bundle.

### Weave MCP edge

The MCP process has no database and no company credential. It projects authorized capabilities as bounded tools, creates Tasks through the semantic Weave API and returns compact results plus `weave://artifact/...` and `weave://resource/...` links.

OpenClaw owns tool discovery, model selection and its agent loop. Weave owns authorization, Task state, output validation and Artifact identity.

### Weave Runner

Weave publishes a multi-architecture base image. A company derives an image and adds its own capability bundle, schemas, handlers and language runtimes:

```dockerfile
FROM ghcr.io/masssi164/weave-runner:1
COPY capabilities.json /etc/weave-runner/capabilities.json
COPY schemas/ /opt/company/schemas/
COPY handlers/ /opt/company/handlers/
```

The Runner requires only outbound HTTPS. It is neither a reverse tunnel nor a general remote shell.

## Local and public capability contracts

The local `capability-bundle.schema.json` contains handler paths, arguments, schema paths, environment allowlists and execution limits. It remains on the Runner.

The Runner resolves and validates the schema files, then derives `public-capability-bundle.schema.json`. The public bundle contains only:

- capability ID/version, title and description;
- effect classification;
- input/output JSON Schemas and their SHA-256 digests;
- timeout/output limits and Artifact types;
- exact local bundle digest.

Handlers, arguments, internal endpoints, environment names and credentials are forbidden in public capability metadata. The public schemas allow the Engine to validate inputs/results and the MCP edge to generate tools without learning how the company implements them.

## Enrollment and identity

An administrator creates an Access ID and one-time enrollment secret. On first start the Runner:

1. generates an ECDSA P-256 private key locally;
2. creates a certificate signing request;
3. exchanges Access ID, one-time secret, CSR and bundle digest over TLS;
4. atomically stores the returned client certificate and CA chain;
5. uses mutual TLS for every later control request.

The private key never leaves the Runner. Internal system credentials are local mounts and never enter a Task, public bundle or Engine database. Certificate rotation is a separate mTLS-authenticated operation.

## Handler ABI

A handler is language-independent:

```text
stdin                 validated task envelope JSON
stdout                one JSON result value
WEAVE_INPUT_DIR       read-only task/context files
WEAVE_OUTPUT_DIR      local Artifact files and artifact-manifest.json
WEAVE_TASK_ID         Task UUID
WEAVE_CAPABILITY_ID   selected capability ID
WEAVE_CAPABILITY_VERSION
```

The Runner invokes the configured executable directly, never through a shell. It enforces the local argument vector, deadline, bounded stdout/stderr, controlled working directory and explicit environment allowlist.

The first version executes trusted company code. Stronger isolation such as gVisor or microVMs is a later execution adapter, not part of the control protocol.

## Task leasing

Tasks are durable PostgreSQL rows. Multiple Engine instances claim ready rows transactionally with `FOR UPDATE SKIP LOCKED`. `LISTEN/NOTIFY` may wake waiting claim requests but is never the durable queue.

A lease contains Task/Lease IDs, a monotonically increasing fencing token, Runner and capability coordinates, exact bundle digest, attempt/idempotency key, validated payload, authorized context/resource grants and expiry/deadline.

Initial defaults:

```text
long poll maximum: 25 seconds
lease duration:    60 seconds
heartbeat period:  20 seconds
Runner concurrency: 1 unless configured otherwise
```

Heartbeat, Artifact upload and completion require the current Lease ID and fencing token. Stale work cannot commit after a Task has been re-leased.

## Runner Control API

`runner-control.openapi.yaml` is the versioned HTTP authority. The first profile provides enrollment, certificate rotation, public bundle publication, heartbeat, long-poll Task claim, lease heartbeat, fenced Artifact upload, completion/failure and observation upload.

HTTP long polling was selected over WebSocket, gRPC streaming and a broker because the traffic is low volume, must cross common enterprise proxies and needs only outbound port 443. The Runner reconnects with bounded exponential backoff and jitter.

## Artifact flow

Large outputs are not embedded in Task completion. A Runner:

1. validates local Artifact paths and refuses symlinks/traversal;
2. computes size and SHA-256;
3. uploads each Artifact with its stable Artifact UUID, Lease ID and fencing token;
4. completes the Task with only schema-valid result data and the uploaded Artifact metadata.

The Engine verifies the streamed digest and returns stable `weave://artifact/{id}` URIs.

## Evidence-based topology detection

Profile detection is deterministic evidence ingestion, not LLM-generated truth. Detectors may inspect company declarations, OpenAPI/AsyncAPI documents, SBOMs, OpenTelemetry resources, runtime inventories or custom sources.

A detector emits typed entities and relations with source kind, confidence, evidence references, observation time and TTL. The Engine preserves provenance, expires stale observations and never promotes an unsupported model guess to factual topology.

PostgreSQL initially stores entities and edges relationally. Authorization-first, depth-bounded recursive queries are sufficient until measured workloads justify another store.

## Context compiler

For one selected Task the Engine:

1. starts from the Task, Space and explicitly granted Resources;
2. removes inaccessible nodes before traversal;
3. follows allowlisted relation types to a small maximum depth;
4. ranks by relation priority, evidence confidence and freshness;
5. emits a bounded context pack with summaries and `weave://` links instead of a graph dump.

This does not duplicate OpenClaw Tool Search or introduce central full-text/vector search. Tool Search finds the capability; the Context Compiler explains the authorized internal relationships relevant to that Task.

## Result flow to OpenClaw

```text
OpenClaw invokes a capability tool
  -> MCP creates a durable Task
  -> Engine leases it to a compatible Runner
  -> Runner invokes the company handler
  -> Runner validates result and uploads Artifacts/observations
  -> Engine records Task, Artifacts and accepted graph evidence
  -> MCP returns compact structured content and stable links
```

Short work may complete within one bounded MCP request. Long work returns a Task handle. The MCP Tasks extension can later project the same internal Task state; it is not a second task engine.

## Open standards

- OpenAPI 3.1 for the Runner Control API;
- JSON Schema 2020-12 for capability, lease, result and observation contracts;
- OCI images for company-derived Runner distribution;
- RFC 8705 security properties for mutual-TLS identity;
- RFC 9530 `Content-Digest` for Artifact integrity;
- CloudEvents-compatible lifecycle metadata;
- W3C Trace Context/OpenTelemetry identifiers for correlation.

Standards define boundaries; Weave still owns authorization, leasing, provenance and Task semantics.

## Explicit non-goals for v1

- arbitrary remote command execution;
- inbound tunnels into company networks;
- a workflow/process engine, Kafka, Temporal or graph database;
- untrusted marketplace code;
- Engine-side storage of internal credentials;
- LLM-generated topology as factual state;
- a second Tool Search or general content-search platform.

## Implementation sequence

1. local/public bundle, lease, result, observation and OpenAPI contracts;
2. framework-free Java Runner/Capability/Lease model;
3. generic Go Runner image with enrollment, long polling and bounded execution;
4. PostgreSQL leasing and Engine endpoints;
5. dynamic MCP capability projection and Task handles;
6. observation reconciliation and Context Compiler;
7. black-box `OpenClaw -> MCP -> Engine -> Runner -> Artifact -> OpenClaw` evidence.
