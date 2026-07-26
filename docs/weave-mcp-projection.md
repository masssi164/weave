# Weave MCP projection boundary

Status: **Guarded / first read-only domain slice active**. The previous member-oriented v1
runtime was removed without compatibility readers. The replacement admits only an ARC-bound cell
workload and exposes the first authorized Files projection.

## Implemented path

1. A dedicated `weaver-cell-{cellId}` Keycloak service account obtains a short-lived RFC 9068
   access token for the exact MCP resource through the MCP Client Credentials extension.
2. Spring Security validates token type, issuer, lifetime, exact audiences, workload identity,
   role, and required scopes before Spring AI sees the request.
3. `weave-mcp-server` exchanges that token with Keycloak Standard Token Exchange V2 for a new,
   short-lived, exact-audience backend token. It never forwards the inbound bearer.
4. `weave-backend` resolves the immutable service-account-to-cell mapping and revalidates the
   current entitlement, lifecycle, RuntimeProfile v2 hash, policy, and domain scopes.
5. Only then may the framework-native stateful Streamable HTTP transport dispatch
   `files.search` or `weave://files/{canonicalFileId}`.
6. The MCP process consumes the existing `/dav/files` projection: bounded RFC 5323 `SEARCH`,
   exact `{urn:weave:files}canonical-id` resolution, and bounded `GET`. It does not call a
   provider or a tool-specific backend endpoint.

The edge publishes protected-resource metadata and a discoverable bearer challenge. Human tokens,
generic service accounts, the fixed MCP edge account, missing extension negotiation, scope
escalation, stale profiles, and direct workload calls to member/admin APIs fail closed.

## Module and bean ownership

The two processes intentionally keep independent framework lifecycles:

The complete JVM dependency, bean, profile, and enforcement inventory is maintained in
the [JVM module, dependency, and bean contract](architecture/jvm-module-and-bean-contract.md).

| Module | Runtime dependencies | Owned beans | Forbidden dependencies/beans |
| --- | --- | --- | --- |
| `weave-files-core` | Java only | none | Spring, HTTP, Servlet, Jackson, JPA, MCP and provider SDKs |
| `server` | Java 21, Spring Boot 4.1, WebMVC, OAuth2 Resource Server, Spring Data JPA, Hibernate, Flyway | public/control-plane security chains, canonical Files use cases, provider ports/adapters, explicit identity-provider OAuth2 client, JPA/Flyway composition | Spring AI MCP, MCP OAuth2 token exchange, MCP tool beans |
| `weave-mcp-server` | Java 21, Spring Boot 4.1, Spring AI MCP 2.0, OAuth2 Resource Server, OAuth2 Client, Actuator, Bouncy Castle PEM support | MCP transport/security, Boot-managed `RestClient.Builder`, request-scoped exchanged credentials, RFC 8693 token-exchange adapter, Files MCP projection | JDBC/JPA/Flyway, provider adapters, product repositories, duplicate domain use cases |

Spring manages exactly one default bean for each boundary concern in the MCP process:

- `McpBackendTokenExchange`: `SpringSecurityMcpBackendTokenExchange`;
- `RestClient.Builder`: the single Boot-managed builder; tests import the official auto-configuration;
- `McpInvocationCredentials`: request-scoped and unavailable outside an admitted MCP request;
- `FilesMcpProjection`: the sole owner of the active Files tool and resource annotations.

No shared DTO module is used to couple MCP to a private business REST API. OpenAPI remains the
generated control-plane contract for Flutter/Admin consumers; MCP schemas come from the annotated
MCP records, while collaboration data stays on WebDAV, CalDAV/iCalendar, and Matrix.

## Removed code and contracts

- `MemberMcp*` DTOs and the `member-mcp-contract-v1` catalog;
- member-oriented admission, forwarded-member-token exchange, and caller-supplied profile headers;
- caller-supplied elicitation as approval authority;
- `/api[/v1]/workspace/weaver/**` runtime-profile, discovery, and invocation routes;
- the fake Weaver Scout response and UI;
- the old backend runtime, registry, dispatcher, bridge, and receipt classes;
- the Python/FastMCP gateway and handwritten Java JSON-RPC controller.

## Active catalog

- `files.search`: read-only, idempotent, closed-world MCP tool over the Weave WebDAV facade.
- `weave://files/{canonicalFileId}`: bounded textual content resource; the canonical ID is
  percent-encoded in the URI path and resolved exactly before the authorized `GET`.
- Prompts and write tools remain absent.

## Next activation gate

Calendar and Chat read tools may be added only over the existing CalDAV/iCalendar and Matrix
Client-Server projections and only from the intersection of the canonical catalog, current signed
RuntimeProfile, current product-domain authorization, and runtime availability. Write-like tools
also require signed, single-use, argument-bound ApprovalDecisionEvidence v2 and immutable
ActionEvidence v2. OpenClaw owns the native approval lifecycle; Weave remains the final
authorization and side-effect authority.

See the pinned `weave-specs` corpus and
`infra/docs/weave-mcp-tool-contract.md` for the normative and executable contracts.
