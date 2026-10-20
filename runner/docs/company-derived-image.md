# Build a company-derived Weave Runner image

The Weave Runner base image contains only the control-plane client, local executor and contract validation. Company code and internal credentials remain under the organization's control.

## Image

```dockerfile
FROM golang:1.25-alpine AS handler-build
WORKDIR /src
COPY handler/ .
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/internal-lookup ./cmd/internal-lookup

FROM ghcr.io/masssi164/weave-runner:1
COPY --from=handler-build /out/internal-lookup /opt/company/bin/internal-lookup
COPY capabilities.json /etc/weave/capabilities.json
COPY schemas/ /opt/company/schemas/
```

The company layer may instead copy Python, Java, Rust or native executables. The Runner invokes the configured executable directly; it does not interpret a remote shell command.

## Local capability bundle

The local bundle maps a stable capability to a local handler and schema files. The exact accepted fields are defined by `contracts/runner/v1/capability-bundle.schema.json`.

```json
{
  "contractVersion": "weave-runner-capabilities/v1",
  "capabilities": [
    {
      "id": "internal.cmdb.lookup",
      "version": "1.0.0",
      "title": "Internal CMDB lookup",
      "description": "Returns support-safe metadata for one internal configuration item.",
      "effect": "READ_ONLY",
      "handler": {
        "executable": "/opt/company/bin/internal-lookup",
        "arguments": []
      },
      "inputSchema": "/opt/company/schemas/cmdb-input.json",
      "outputSchema": "/opt/company/schemas/cmdb-output.json",
      "timeoutSeconds": 60,
      "maximumOutputBytes": 1048576,
      "artifactKinds": ["cmdb-report"]
    }
  ]
}
```

At startup the Runner validates this local bundle, loads the schemas, computes canonical digests and publishes a separate public bundle. The public bundle contains the complete JSON Schemas but excludes `handler`, executable arguments, local paths and environment bindings.

## Handler ABI

The handler reads exactly one JSON value from stdin and writes exactly one JSON value to stdout. It should treat cancellation of the process context or `SIGTERM` as a request to stop promptly.

```text
stdin   validated task input
stdout  schema-valid structured result
stderr  bounded diagnostics; never credentials or raw private payloads
```

Artifacts are written only below the task output directory supplied by the Runner and declared through the artifact manifest. Path traversal, symlinks and files outside that directory are rejected.

## Local credentials

Internal credentials are mounted by the organization at runtime. They are referenced only by local handler configuration and are never listed in the public capability bundle.

```yaml
services:
  weave-runner:
    image: registry.example/internal/weave-runner:1.0.0
    read_only: true
    volumes:
      - runner-identity:/var/lib/weave-runner
      - ./secrets/cmdb-token:/run/company-secrets/cmdb-token:ro
    environment:
      WEAVE_ENGINE_URL: https://weave.example.org
      WEAVE_RUNNER_IDENTITY_DIR: /var/lib/weave-runner
      WEAVE_RUNNER_CAPABILITY_BUNDLE: /etc/weave/capabilities.json
      CMDB_TOKEN_FILE: /run/company-secrets/cmdb-token
```

The base Runner does not read or transmit `CMDB_TOKEN_FILE`; only the company handler receives that local environment binding when declared by the local execution policy.

## Enrollment

The administrator creates an Access ID and one-time enrollment secret in Weave. The first container start receives both through mounted files or a secret manager. After exchanging a locally generated CSR, the Runner persists only its private key, client certificate, Engine CA and non-secret identity metadata.

Normal operation uses mutual TLS. Remove the one-time enrollment secret mount after successful enrollment.

## Recommended runtime policy

- run as a non-root UID;
- read-only root filesystem;
- writable identity and task scratch volumes only;
- no host network;
- explicit egress allowlist for the Engine and required internal systems;
- explicit CPU, memory, process and task timeout limits;
- no Docker/Podman socket unless a declared capability genuinely needs container control;
- one derived image digest approved before production use;
- secrets mounted as files and excluded from stdout, stderr, artifacts and observations.

The initial Runner is an execution boundary for organization-approved code, not a sandbox for untrusted marketplace code.