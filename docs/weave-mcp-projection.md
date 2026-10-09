# Weave MCP projection boundary

Status: **Guarded / curated Files and Calendar slices active**. The previous member-oriented v1
runtime was removed without compatibility readers. The replacement admits only an ARC-bound cell
workload and exposes curated Files read and Calendar read/write projections.

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
   `files.search`, `weave://files/{canonicalFileId}`, `calendar.agenda`, or the
   supported `calendar.create`, `calendar.update`, and `calendar.delete` tools.
6. The MCP process uses the generated JVM User client for Files and Calendar HTTP operations:
   bounded Files traversal/content and Calendar reads or mutations. It does not call a
   provider or a tool-specific backend endpoint.

The edge publishes protected-resource metadata and a discoverable bearer challenge. Human tokens,
generic service accounts, the fixed MCP edge account, missing extension negotiation, scope
escalation, stale profiles, and direct workload calls to Admin APIs fail closed. Exchanged
workload tokens can use only the guarded User Files or Calendar operations for their exact
admitted scope. Calendar writes additionally require the current owner/admin capability and
Space EDIT permission at the Server.

## Module and bean ownership

The two processes intentionally keep independent framework lifecycles:

The complete JVM dependency, bean, profile, and enforcement inventory is maintained in
the [JVM module, dependency, and bean contract](architecture/jvm-module-and-bean-contract.md).

| Module | Runtime dependencies | Owned beans | Forbidden dependencies/beans |
| --- | --- | --- | --- |
| `weave-files-core` | Java only | none | Spring, HTTP, Servlet, Jackson, JPA, MCP and provider SDKs |
| `server` | Java 21, Spring Boot 4.1, WebMVC, OAuth2 Resource Server, Spring Data JPA, Hibernate | public/control-plane security chains, canonical Files use cases, provider ports/adapters, explicit identity-provider OAuth2 client, JPA composition and one-shot schema initialization | Spring AI MCP, MCP OAuth2 token exchange, MCP tool beans |
| `weave-mcp-server` | Java 21, Spring Boot 4.1, Spring AI MCP 2.0, generated JVM User client, OAuth2 Resource Server, OAuth2 Client, Actuator, Bouncy Castle PEM support | MCP transport/security, Boot-managed `RestClient.Builder`, request-scoped exchanged credentials, RFC 8693 token-exchange adapter, curated Files and Calendar projections | JDBC/JPA/schema initialization, provider adapters, product repositories, duplicate domain use cases |

Spring manages exactly one default bean for each boundary concern in the MCP process:

- `McpBackendTokenExchange`: `SpringSecurityMcpBackendTokenExchange`;
- `RestClient.Builder`: the single Boot-managed builder; tests import the official auto-configuration;
- `McpInvocationCredentials`: request-scoped and unavailable outside an admitted MCP request;
- `FilesMcpProjection`: the sole owner of the active Files tool and resource annotations.
- `CalendarMcpProjection`: the sole owner of the curated Calendar tool annotations.

The same generated JVM User client serves MCP and product E2E. Its HTTP operations and transport
models come from the server-owned code-first OpenAPI artifact; MCP tool schemas come from the
annotated MCP records. Chat remains on Matrix.

## Removed code and contracts

- `MemberMcp*` DTOs and the `member-mcp-contract-v1` catalog;
- member-oriented admission, forwarded-member-token exchange, and caller-supplied profile headers;
- caller-supplied elicitation as approval authority;
- `/api[/v1]/workspace/weaver/**` runtime-profile, discovery, and invocation routes;
- the fake Weaver Scout response and UI;
- the old backend runtime, registry, dispatcher, bridge, and receipt classes;
- the Python/FastMCP gateway and handwritten Java JSON-RPC controller.

## Active catalog

- `files.search`: read-only, idempotent, closed-world MCP tool over generated User Files operations.
- `weave://files/{canonicalFileId}`: bounded textual content resource; the canonical ID is
  percent-encoded in the URI path and resolved exactly before the authorized download.
- `calendar.agenda`: read-only, bounded agenda over generated User Calendar operations;
  stable Event IDs and transient preview handles remain distinct, and browsing never materializes.
- `calendar.create`, `calendar.update`, and `calendar.delete`: generated User Calendar
  operations with `calendar.write`, current owner/admin capability, Space EDIT, and
  idempotency or strong version preconditions. The existing OpenClaw approval flow owns
  presentation and decision state for these actions.
- Prompts and Files, Chat, Admin, or broad ARC write tools remain absent.

## Next activation gate

Live supported OpenClaw invocation and provider readback are required before #1479 can close.
Further tools require a current #1470 acceptance criterion and the intersection of the curated
catalog, current signed RuntimeProfile, product-domain authorization, and runtime availability.
The historical broad ARC approval-evidence profile is deferred; OpenClaw owns the existing
approval lifecycle for the current Calendar write tools, while Weave remains the final
authorization and side-effect authority.

See the pinned `weave-specs` corpus and
`infra/docs/weave-mcp-tool-contract.md` for the normative and executable contracts.
