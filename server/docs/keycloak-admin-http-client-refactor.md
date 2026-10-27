# Provider HTTP integration provenance

This is the implementation record for [#1730](https://github.com/masssi164/weave/issues/1730)
and merged HTTP-client PR #1732, integrated into domain extraction PR #1746.
The [Java/Spring standard](../../docs/java-spring-standard.md) owns implementation rules;
[the ownership plan](../../docs/architecture/server-domain-ownership.md) owns the living extraction status.
This record does not define another architecture or product contract.

## Implemented owners

| Boundary | Actual owner and transport |
|---|---|
| Human Keycloak administration | Core `identity.keycloak.KeycloakAdminClient` declares named HTTP operations. `KeycloakAuthConfig` registers the `keycloak-admin` Boot HTTP-service group; `identity.invitation.KeycloakIdentityAdminClient` translates domain results and `KeycloakIdentityAdminOperationPolicy` enforces exact admission/status. |
| Keycloak OAuth2 client authentication and SecretRefs | Existing Core `security.keycloak` and `security.secret` implementations; no duplicate token cache or transport. |
| Files Nextcloud WebDAV | Files `files.provider.NextcloudFilesAdapter` retains its Spring `RestClient`, explicit DAV methods, binary streaming, 207 and ETag behavior. |
| Calendar CalDAV | Calendar `calendar.provider.caldav.CalDavCalendarAdapter` retains its JDK `HttpClient`, REPORT/PROPFIND and bounded parsing. |
| Chat Synapse | Chat `chat.provider.synapse.SynapseApplicationServiceClient` retains its JDK `HttpClient` and deferred secret access; Matrix streaming/auth/status remain adapter-owned. |
| Future Boards OpenProject | Boards `boards.provider.openproject.OpenProjectBoardsClient` retains endpoint-oriented imperative `RestClient` operations until a declarative conversion removes meaningful code. |

`IdentityInvitationProperties` is Core identity-owned and remains the sole binder with unchanged
`weave.identity.invitations` keys/defaults. No redundant settings snapshot, generic GET/PATCH proxy,
secret-bearing OpenProject record or one-constructor HTTP wrapper remains from the earlier draft.

Core registers serving identity administration through module-local
`CoreIdentityAdminAutoConfiguration` imports, without component scanning. Non-web schema/operator
contexts do not automatically construct serving transport. The existing Dogfood operator explicitly
imports the protected config and adapter. Construction does not read mounted credentials or contact
Keycloak; token acquisition remains deferred to an authorized operation.

## Preserved security and behavior

`@ImportHttpServices` and `RestClientHttpServiceGroupConfigurer` own production registration and
group-local policy. Spring handles query/form encoding and JSON conversion. The anti-corruption
layer retains strict `JsonNode` field validation rather than accepting coerced provider values.
The protected client retains private-key JWT authentication, exact SecretRef access, connection/read
timeouts, configured URL context paths and 401 invalidation. The closed operation policy runs before
transport and rejects unexpected successful statuses before response conversion. Managed-fixture
admission is request-local metadata, never an outbound header. Member tokens are never relayed and
provider writes are never implicitly retried.

Only the test-source `KeycloakAdminTestClient` creates manual proxies for owning Core transport tests.
The obsolete `KeycloakTestTransport` helper and production proxy factory are removed. Module extraction
preserves the accepted #1732 transport instead of maintaining a second client implementation.

Dynamic Keycloak client registration remains its separate protected workload flow. The offline realm
migration CLI and framework-owned OAuth2/JWT discovery remain separate. Provider selection, public APIs,
supported endpoints, dependency versions and deployment contracts are unchanged by this integration.

## Verification

Owning Core tests cover Boot registration, no construction-time credential access, context paths,
encoded queries/forms, request-local fixture admission, exact expected statuses and 401 invalidation.
The normal imports-discovery tests verify one serving transport and its absence in non-web contexts.
Run `./gradlew :server:core:check :server:domains:files:check`, affected App provider/auth regressions
and `checkOpenApiContractFresh`. The current PR records exact outcomes and remaining gates. JVM or
static checks do not establish native/AOT, live provider, Dogfood or human acceptance.
