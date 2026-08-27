# Private runner control plane

Status: first executable architecture slice for the provider-neutral Weave pivot.

## Purpose

Weave exposes company-owned capabilities to users and agent harnesses without requiring inbound access to the company network and without moving internal credentials or implementation code into the Weave Engine.

The product boundary is:

```text
OpenClaw / other approved harness
        -> Weave MCP edge
            -> Weave Engine
                -> outbound-only runner control protocol
                    -> company-derived Weave Runner image
                        -> company handlers and internal systems
```

The Engine coordinates and authorizes work. The Runner executes locally. A company owns every handler, internal endpoint and internal credential added to its derived image or runtime mounts.

## Deployables

### Weave Engine

The existing Java 21/Spring Boot server becomes the control and knowledge plane. PostgreSQL stores Runner registrations, capability declarations, tasks, leases, artifacts and topology observations.

The Engine never sends an arbitrary command or executable path. A task references a capability ID and version that the Runner already declared from its local bundle.

### Weave MCP edge

The MCP process remains a separately authorized projection with no database and no company credentials. It turns approved Runner capabilities into bounded tools, creates Tasks through the semantic Weave API and returns compact results plus `weave://` artifact or resource links.

OpenClaw owns tool discovery, model selection and its agent loop. Weave owns capability authorization, task state and result validation.

### Weave Runner

Weave publishes one multi-architecture base image. A company derives an image and adds its own capability bundle, schemas, handlers and language runtimes:

```dockerfile
FROM ghcr.io/masssi164/weave-runner:1

COPY capabilities.json /etc/weave-runner/capabilities.json
COPY handlers/ /opt/company/handlers/
```

The Runner opens only outbound HTTPS connections to the Engine. No reverse tunnel, public webhook listener or inbound control port is required.

## Trust model

### Enrollment

An administrator creates an Access ID and a one-time enrollment secret in Weave. On first start the Runner:

1. generates its private key locally;
2. creates a certificate signing request;
3. exchanges Access ID, one-time secret and CSR with the Engine over TLS;
4. stores the returned Runner certificate and CA chain in its identity volume;
5. uses mutual TLS for later control-plane requests.

The private key never leaves the Runner. The enrollment secret is not a runtime credential and is not reused after successful enrollment. Certificate rotation is a separate mTLS-authenticated operation.

The protocol follows the security properties of OAuth mutual-TLS client authentication and certificate-bound credentials from RFC 8705 without requiring the Runner to become a general OAuth client in the first slice.

### Company credentials

Internal API keys, database passwords, repository credentials and provider tokens are mounted locally. They are referenced by company code and are neither declared in the capability bundle nor uploaded to Weave.

### Handler selection

The Engine supplies only a capability reference and validated input. The Runner resolves the executable path from its local bundle. This prevents a compromised control-plane task from converting the Runner into a remote shell.

## Capability bundle

`contracts/runner/v1/capability-bundle.schema.json` defines the local declaration. Each capability contains:

- stable ID and semantic version;
- local handler path;
- input and output schema paths;
- side-effect classification;
- timeout and output bounds;
- optional artifact types.

A detector is a local handler that emits bounded topology observations. The bundle contains no secret values and no internal endpoint credentials.

The Runner computes a SHA-256 digest over the exact bundle bytes and reports the digest with enrollment, heartbeat and task claims. A task may be leased only against the currently observed bundle digest.

## Handler ABI

A company handler is language-independent:

```text
stdin                 validated task JSON
stdout                one JSON result document
WEAVE_INPUT_DIR       read-only task/context files
WEAVE_OUTPUT_DIR      artifact files and artifact-manifest.json
WEAVE_TASK_ID         stable task identifier
WEAVE_CAPABILITY_ID   selected local capability
```

The Runner invokes the configured executable directly, never through a shell. It applies a deadline, bounded stdout/stderr capture, a controlled working directory and an explicit environment allowlist. The first image supports trusted company code; stronger sandboxes are an execution adapter, not part of the control protocol.

## Task and lease model

Tasks are durable PostgreSQL rows. Claiming uses a transaction and `FOR UPDATE SKIP LOCKED` so multiple Engine instances can allocate ready work without blocking one another. `LISTEN/NOTIFY` may wake waiting Engine request handlers, but notifications are never the durable queue.

A Task Lease contains:

- Task and Lease IDs;
- monotonically increasing fencing token;
- exact Runner and capability coordinates;
- bundle digest;
- attempt and idempotency key;
- validated payload;
- bounded context/resource grants;
- lease expiry and optional task deadline.

Initial operational defaults are deliberately conservative:

```text
long poll maximum: 25 seconds
lease duration:    60 seconds
heartbeat period:  20 seconds
runner concurrency: 1 unless configured otherwise
```

A completion, failure or heartbeat with an old fencing token is rejected. An expired task can be offered again only according to its retry policy.

## Runner control protocol

`contracts/runner/v1/runner-control.openapi.yaml` is the wire authority. The first profile contains:

```text
POST /runner/v1/enrollments:exchange
POST /runner/v1/certificates:rotate
POST /runner/v1/heartbeat
POST /runner/v1/tasks:claim
POST /runner/v1/tasks/{taskId}:heartbeat
POST /runner/v1/tasks/{taskId}:complete
POST /runner/v1/tasks/{taskId}:fail
POST /runner/v1/observations
```

Task claim is an HTTP long poll and returns either `200` with one lease or `204` when no compatible task becomes ready. The Runner reconnects with bounded exponential backoff and jitter after transport failures.

HTTP was selected over WebSocket, gRPC streaming and a message broker because the required interaction is low-volume request/response, must traverse common enterprise proxies and must work with outbound port 443 only. The protocol remains versioned so another transport can implement the same application port later.

## Evidence-based topology detection

Profile detection is not an LLM inference pipeline. A detector emits typed observations conforming to `contracts/runner/v1/observation.schema.json`.

Supported evidence origins include:

- company declarations;
- OpenAPI or AsyncAPI documents;
- SBOM/component metadata;
- OpenTelemetry resource and service observations;
- runtime inventory;
- custom deterministic detectors.

An observation contains local entity keys, typed relations, source kind, confidence, evidence references, observation time and TTL. The Engine preserves provenance and does not promote an unsupported model guess to factual topology.

The initial graph is stored relationally in PostgreSQL. Entity identity and relation tables are sufficient for authorization-first, depth-bounded traversal; a graph database is deferred until measured queries require it.

## Context compiler

Before an agent receives context, the Engine:

1. starts from the Task, Space and explicitly granted Resources;
2. removes nodes the caller may not access;
3. follows an allowlisted set of relation types for at most a small configured depth;
4. ranks observations by relation priority, evidence confidence and freshness;
5. emits a bounded context pack containing summaries and `weave://` links rather than a graph dump.

This is distinct from tool search and full-text search. OpenClaw discovers tools; the Context Compiler explains the authorized local relationships needed for one selected task.

## Result flow to OpenClaw

```text
OpenClaw invokes a capability tool
  -> MCP creates a Weave Task
  -> Engine leases it to a matching Runner
  -> Runner executes the company handler
  -> Runner validates and uploads the result/artifact manifest
  -> Engine records Artifacts and accepted topology observations
  -> MCP returns compact structured content and weave:// links
```

Short tasks may complete inside one bounded MCP request. Long tasks return a Task handle and are read or cancelled through stable task operations; adopting the MCP Tasks extension is a protocol projection over the same internal Task state, not a second task engine.

## Open standards used

- OpenAPI 3.1 for the Runner control protocol;
- JSON Schema 2020-12 for capability, lease, observation and result contracts;
- OCI images for the company-derived Runner distribution model;
- RFC 8705 security properties for mTLS Runner identity;
- CloudEvents-compatible event metadata for progress and lifecycle events;
- OpenTelemetry/Trace Context identifiers for cross-boundary correlation.

The standards describe boundaries. They do not replace Weave domain authorization, task leasing or topology provenance.

## Explicit non-goals for this slice

- arbitrary remote command execution;
- a public ingress tunnel into company networks;
- a generic workflow/process engine;
- Kafka, Temporal or a graph database;
- untrusted third-party code execution;
- server-side storage of internal credentials;
- LLM-generated topology as factual state;
- dynamic MCP implementation before the Runner contract and task state are executable.

## First implementation sequence

1. capability, lease, result, observation and OpenAPI contracts;
2. framework-free Java Runner/Capability/Lease model;
3. generic Go Runner image with local bundle loading and bounded handler execution;
4. PostgreSQL task leasing and enrollment endpoints;
5. dynamic MCP capability projection and task handles;
6. topology observation ingestion and Context Compiler;
7. black-box `OpenClaw -> MCP -> Engine -> Runner -> Artifact -> OpenClaw` evidence.
