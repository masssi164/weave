# Provider HTTP integration provenance

This is the implementation record for [#1730](https://github.com/masssi164/weave/issues/1730)
and its existing HTTP-client PR #1732, reconciled into domain extraction PR #1746.
The [Java/Spring standard](../../docs/java-spring-standard.md) owns implementation rules;
[this ownership plan](../../docs/architecture/server-domain-ownership.md) owns the living extraction status.
This record does not define another architecture or product contract. The accepted corpus pin
remains `f049c0b1879414eb529a3df83a18224407f1a436`.

## Implemented owners

| Boundary | Actual owner and transport |
|---|---|
| Human Keycloak administration | Core `backend.identity.keycloak.KeycloakAdminClient` declares named HTTP operations. `KeycloakAuthConfig` binds the protected transport; `identity.invitation.KeycloakIdentityAdminClient` translates domain results and `KeycloakIdentityAdminOperationPolicy` enforces exact admission/status. |
| Keycloak OAuth2 client authentication and SecretRefs | Existing Core `security.keycloak` and `security.secret` implementations; no duplicate token cache or transport. |
| Files Nextcloud WebDAV | Files `files.config.http.NextcloudHttpConfig`; `files.provider.NextcloudFilesAdapter` retains specialized DAV/streaming/207 behavior. |
| Calendar CalDAV | Calendar runtime `calendar.config.http.CalDavHttpConfig`; REPORT/PROPFIND and bounded parsing remain provider-owned. |
| Chat Synapse | Chat `chat.provider.synapse.config.SynapseHttpConfig`, physically under App until Chat runtime extraction; Matrix streaming/auth/status remain adapter-owned. |
| Future Boards OpenProject | `boards.openproject.config` HTTP interface/factory; physical App ownership remains transitional. Its two verb-level operations are not claimed as a complete endpoint-specific declarative conversion. |

`IdentityInvitationProperties` is now Core identity-owned and remains the sole binder with
unchanged `weave.identity.invitations` keys/defaults. The redundant incoming
`KeycloakAdminProperties` snapshot was consolidated into that existing typed configuration.
OpenProject's immutable client settings redact their credential in generated output.

Core registers actual serving identity administration through module-local
`CoreIdentityAdminAutoConfiguration` imports, without component scanning. Non-web schema/operator
contexts do not automatically construct serving transport. The existing Dogfood operator explicitly
imports the protected config and adapter. Construction does not read mounted credentials or contact
Keycloak; token acquisition remains deferred to an authorized operation.

## Preserved security and behavior

The protected client retains exact private-key JWT authentication, SecretRef access, connection/read
timeouts, configured URL context paths, and 401 invalidation. The closed operation policy runs before
transport and rejects unexpected successful statuses before response conversion. Managed-fixture
admission is request-local metadata, never an outbound header. The production RestClient test-only
constructor was removed; owning Core tests explicitly create the same protected proxy.

Dynamic Keycloak client registration remains its separate protected workload flow. The offline realm
migration CLI and framework-owned OAuth2/JWT discovery remain separate. No provider-selection policy,
public API, supported endpoint, dependency version, or deployment contract changed in this merge.

Validation commands and exact final results are recorded in the PR/checkpoint report. Owning checks:
`./gradlew :server:core:check :server:domains:files:check`, App provider/auth regressions and
`checkOpenApiContractFresh`. These JVM checks do not establish native/AOT or integrated deployment
qualification.
