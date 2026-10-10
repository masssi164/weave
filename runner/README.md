# Weave Runner

The Weave Runner executes organization-owned capabilities inside a private network while the Weave Engine owns identity, policy, durable tasks, artifacts and agent-facing results.

Status: first v1 vertical slice. The contracts, framework-free Engine values and local Runner primitives are under active review. Engine persistence/endpoints and MCP projection are separate dependent issues.

## Boundary

```text
Weave Engine <- outbound HTTPS/mTLS <- Runner -> organization handlers/internal systems
```

The Engine sends a fenced task lease that selects a capability already declared by the Runner. It never sends an executable, shell fragment, internal endpoint or credential.

## Repository layout

```text
runner/
  cmd/                  Runner executable
  internal/config/      local capability bundle and public-bundle derivation
  internal/identity/    local key generation, enrollment and mTLS material
  internal/protocol/    bounded control API values/client mapping
  internal/executor/    direct company-handler execution and validation
  internal/worker/      long-poll, heartbeat, cancellation and outcome state machine
  internal/detection/   deterministic observation normalization
  docs/                 company integration guidance

contracts/runner/v1/
  capability-bundle.schema.json
  public-capability-bundle.schema.json
  task-lease.schema.json
  task-result.schema.json
  observation.schema.json
  runner-control.openapi.yaml
```

## Development

```bash
cd runner
gofmt -w .
go test ./...
go vet ./...

cd ..
python3 tools/runner_contract_check.py
./gradlew :weave-application-core:test --tests '*RunnerControlTest'
```

The exact hosted lane and pinned Dev Container toolchain are owned by #1307.

## Local capability versus public capability

The local bundle is private to the Runner and may contain executable paths, fixed arguments and local environment bindings. At startup the Runner loads the declared input/output schemas and derives a public bundle containing only stable metadata, complete schemas, limits and digests.

The Engine and MCP consume only the public bundle. Company code, internal URLs, environment variable names and credentials stay local.

## Handler execution

- direct process execution, never a shell supplied by the Engine;
- validated JSON input on stdin;
- schema-valid JSON result on stdout;
- bounded stderr diagnostics;
- hard task deadline and cancellation on lease loss;
- artifacts restricted to the task output root, hashed and uploaded separately;
- no implicit host mount, network or secret grant.

See `docs/company-derived-image.md` for the derived-image pattern.

## Identity

The initial enrollment uses an administrator-created Access ID and one-time secret file. The Runner generates an ECDSA key and CSR locally, validates the returned certificate/CA/Engine origin, stores identity files atomically with private permissions, and uses TLS 1.3 mutual authentication afterward.

Certificate rotation, revocation and the Engine-side certificate authority are implemented under the Runner control-plane issue rather than in company handlers.

## Profile detection

A detector is a local organization capability that emits bounded entities, relations and evidence. The Runner normalizes ordering, rejects dangling relations and sensitive attributes, computes a digest and submits the batch to the Engine.

Detection is deterministic evidence collection, not LLM-generated topology. The Engine owns alias resolution, TTL expiry, conflict handling and bounded context compilation.

## Non-goals of v1

- arbitrary remote shell execution;
- public untrusted skill marketplace;
- inbound Engine-to-Runner networking;
- a message broker or external process engine;
- a graph/vector database;
- A2A or Open Workflow execution;
- representing a rootless container as a complete hostile-code sandbox.