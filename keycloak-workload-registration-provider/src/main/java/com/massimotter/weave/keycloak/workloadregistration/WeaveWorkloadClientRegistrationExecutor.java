package com.massimotter.weave.keycloak.workloadregistration;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.keycloak.OAuthErrorException;
import org.keycloak.authentication.authenticators.client.JWTClientAuthenticator;
import org.keycloak.crypto.Algorithm;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.RoleUtils;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.AdminClientRegisterContext;
import org.keycloak.services.clientpolicy.context.AdminClientUnregisterContext;
import org.keycloak.services.clientpolicy.context.AdminClientUpdateContext;
import org.keycloak.services.clientpolicy.context.AdminClientUpdatedContext;
import org.keycloak.services.clientpolicy.context.AdminClientViewContext;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.keycloak.services.clientpolicy.context.DynamicClientRegisterContext;
import org.keycloak.services.clientpolicy.context.DynamicClientRegisteredContext;
import org.keycloak.services.clientpolicy.context.DynamicClientUnregisterContext;
import org.keycloak.services.clientpolicy.context.DynamicClientUpdateContext;
import org.keycloak.services.clientpolicy.context.DynamicClientUpdatedContext;
import org.keycloak.services.clientpolicy.context.DynamicClientViewContext;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.keycloak.services.clientregistration.ClientRegistrationTokenUtils;

/**
 * Enforces the merged Weave workload-registration contract in Keycloak itself.
 *
 * <p>The provider deliberately has no application-domain dependency. It accepts only the
 * version-pinned Keycloak 26.7 Client Policy CRUD contexts, and it never logs request metadata,
 * access tokens, Registration Access Tokens, or JWK material.</p>
 */
public final class WeaveWorkloadClientRegistrationExecutor
        implements ClientPolicyExecutorProvider<ClientPolicyExecutorConfigurationRepresentation> {

    static final String RUNTIME_ADMIN_CLIENT_ID = "weave-agent-runtime-admin";
    static final String WORKLOAD_ROLE = "weaver-runtime";
    static final String DEFAULT_SCOPE = "weaver-runtime-workload";
    static final Set<String> OPTIONAL_SCOPES =
            Set.of("agent-runtime.profile.read", "mcp.tools", "files.read");
    static final Pattern CLIENT_ID_PATTERN =
            Pattern.compile("^weaver-cell-[A-Za-z0-9_-]+$");
    static final String INVALID_DETAIL = "Weave workload client metadata is not permitted.";
    static final String CROSS_CELL_DETAIL =
            "Weave workload client lifecycle authority does not match the target client.";

    private static final String USE_REFRESH_TOKENS = "use.refresh.tokens";
    private static final String BACKCHANNEL_LOGOUT_SESSION_REQUIRED =
            "backchannel.logout.session.required";
    private static final String BACKCHANNEL_LOGOUT_REVOKE_OFFLINE_TOKENS =
            "backchannel.logout.revoke.offline.tokens";
    private static final String FRONTCHANNEL_LOGOUT_SESSION_REQUIRED =
            "frontchannel.logout.session.required";
    private static final String JWT_PUBLIC_KEY =
            JWTClientAuthenticator.ATTR_PREFIX + ".public.key";
    private static final String JWT_KEY_ID =
            JWTClientAuthenticator.ATTR_PREFIX + ".kid";
    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            OIDCConfigAttributes.USE_JWKS_STRING,
            OIDCConfigAttributes.USE_JWKS_URL,
            OIDCConfigAttributes.JWKS_STRING,
            JWT_PUBLIC_KEY,
            JWT_KEY_ID,
            OIDCConfigAttributes.TOKEN_ENDPOINT_AUTH_SIGNING_ALG,
            USE_REFRESH_TOKENS,
            BACKCHANNEL_LOGOUT_SESSION_REQUIRED,
            BACKCHANNEL_LOGOUT_REVOKE_OFFLINE_TOKENS,
            FRONTCHANNEL_LOGOUT_SESSION_REQUIRED);
    private static final Map<String, String> FIXED_ATTRIBUTES = Map.of(
            OIDCConfigAttributes.USE_JWKS_STRING, Boolean.TRUE.toString(),
            OIDCConfigAttributes.USE_JWKS_URL, Boolean.FALSE.toString(),
            OIDCConfigAttributes.TOKEN_ENDPOINT_AUTH_SIGNING_ALG, Algorithm.PS256,
            USE_REFRESH_TOKENS, Boolean.FALSE.toString(),
            BACKCHANNEL_LOGOUT_SESSION_REQUIRED, Boolean.FALSE.toString(),
            BACKCHANNEL_LOGOUT_REVOKE_OFFLINE_TOKENS, Boolean.FALSE.toString(),
            FRONTCHANNEL_LOGOUT_SESSION_REQUIRED, Boolean.FALSE.toString());
    private static final Set<String> CONVERTER_FALSE_ATTRIBUTES = Set.of(
            "oauth2.device.authorization.grant.enabled",
            "oidc.ciba.grant.enabled",
            OIDCConfigAttributes.STANDARD_TOKEN_EXCHANGE_ENABLED,
            OIDCConfigAttributes.JWT_AUTHORIZATION_GRANT_ENABLED);

    private final KeycloakSession session;

    public WeaveWorkloadClientRegistrationExecutor(KeycloakSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public String getProviderId() {
        return WeaveWorkloadClientRegistrationExecutorFactory.PROVIDER_ID;
    }

    @Override
    public void executeOnEvent(ClientPolicyContext context) throws ClientPolicyException {
        if (!(context instanceof ClientCRUDContext crud)) {
            return;
        }

        if (isAdminContext(context)) {
            rejectAdminBypass(crud);
            return;
        }

        if (context instanceof DynamicClientRegisterContext) {
            requireRuntimeAdmin(crud);
            prepareRegistration(crud.getProposedClientRepresentation());
            return;
        }

        ClientModel target = crud.getTargetClient();
        if (target == null || !isWorkloadClient(target.getClientId())) {
            throw invalid(INVALID_DETAIL);
        }

        if (context instanceof DynamicClientRegisteredContext) {
            requireRuntimeAdmin(crud);
            enforceFinalState(target);
        } else if (context instanceof DynamicClientUpdateContext) {
            requireBoundRegistrationAccessToken(crud, target);
            prepareUpdate(crud.getProposedClientRepresentation(), target.getClientId());
        } else if (context instanceof DynamicClientUpdatedContext) {
            requireBoundRegistrationAccessToken(crud, target);
            enforceFinalState(target);
        } else if (context instanceof DynamicClientViewContext
                || context instanceof DynamicClientUnregisterContext) {
            requireBoundRegistrationAccessToken(crud, target);
            validateFinalState(target);
        } else {
            throw invalid(INVALID_DETAIL);
        }
    }

    static void prepareRegistration(ClientRepresentation representation)
            throws ClientPolicyException {
        String requestedClientId = representation.getName();
        if (!isWorkloadClient(requestedClientId)
                || (representation.getClientId() != null
                        && !requestedClientId.equals(representation.getClientId()))) {
            throw invalid(INVALID_DETAIL);
        }
        representation.setClientId(requestedClientId);
        preparePosture(representation, requestedClientId);
    }

    static void prepareUpdate(ClientRepresentation representation, String targetClientId)
            throws ClientPolicyException {
        if (!isWorkloadClient(targetClientId)
                || !targetClientId.equals(representation.getClientId())
                || !targetClientId.equals(representation.getName())) {
            throw invalid(INVALID_DETAIL);
        }
        preparePosture(representation, targetClientId);
    }

    private static void preparePosture(
            ClientRepresentation representation, String expectedClientId)
            throws ClientPolicyException {
        if (!expectedClientId.equals(representation.getClientId())
                || !OIDCLoginProtocol.LOGIN_PROTOCOL.equals(representation.getProtocol())
                || !JWTClientAuthenticator.PROVIDER_ID.equals(
                        representation.getClientAuthenticatorType())
                || !Boolean.TRUE.equals(representation.isServiceAccountsEnabled())
                || Boolean.TRUE.equals(representation.isPublicClient())
                || Boolean.TRUE.equals(representation.isBearerOnly())
                || Boolean.TRUE.equals(representation.isStandardFlowEnabled())
                || Boolean.TRUE.equals(representation.isImplicitFlowEnabled())
                || Boolean.TRUE.equals(representation.isDirectAccessGrantsEnabled())
                || Boolean.TRUE.equals(representation.getAuthorizationServicesEnabled())
                || hasValues(representation.getRedirectUris())
                || hasValues(representation.getWebOrigins())
                || hasText(representation.getRootUrl())
                || hasText(representation.getBaseUrl())
                || hasText(representation.getAdminUrl())
                || hasText(representation.getSecret())
                || hasValues(representation.getProtocolMappers())
                || !exactSet(representation.getOptionalClientScopes(), OPTIONAL_SCOPES)) {
            throw invalid(INVALID_DETAIL);
        }

        Map<String, String> attributes =
                new HashMap<>(Objects.requireNonNullElse(representation.getAttributes(), Map.of()));
        for (String converterAttribute : CONVERTER_FALSE_ATTRIBUTES) {
            String value = attributes.remove(converterAttribute);
            if (value != null && !Boolean.FALSE.toString().equals(value)) {
                throw invalid(INVALID_DETAIL);
            }
        }
        if (!ALLOWED_ATTRIBUTES.containsAll(attributes.keySet())
                || !FIXED_ATTRIBUTES.entrySet().stream()
                        .allMatch(entry -> entry.getValue().equals(attributes.get(entry.getKey())))
                || !hasText(attributes.get(OIDCConfigAttributes.JWKS_STRING))
                || !hasText(attributes.get(JWT_PUBLIC_KEY))
                || !hasText(attributes.get(JWT_KEY_ID))) {
            throw invalid(INVALID_DETAIL);
        }

        representation.setEnabled(!Boolean.FALSE.equals(representation.isEnabled()));
        representation.setFullScopeAllowed(Boolean.FALSE);
        representation.setConsentRequired(Boolean.FALSE);
        representation.setStandardFlowEnabled(Boolean.FALSE);
        representation.setImplicitFlowEnabled(Boolean.FALSE);
        representation.setDirectAccessGrantsEnabled(Boolean.FALSE);
        representation.setServiceAccountsEnabled(Boolean.TRUE);
        representation.setPublicClient(Boolean.FALSE);
        representation.setBearerOnly(Boolean.FALSE);
        representation.setAuthorizationServicesEnabled(Boolean.FALSE);
        representation.setRedirectUris(List.of());
        representation.setWebOrigins(List.of());
        representation.setDefaultClientScopes(List.of(DEFAULT_SCOPE));
        representation.setOptionalClientScopes(new ArrayList<>(OPTIONAL_SCOPES));
        representation.setProtocolMappers(List.of());
        representation.setAttributes(attributes);
    }

    private void enforceFinalState(ClientModel client) throws ClientPolicyException {
        RealmModel realm = client.getRealm();
        ClientScopeModel defaultScope = findScope(realm, DEFAULT_SCOPE);
        List<ClientScopeModel> optionalScopes = OPTIONAL_SCOPES.stream()
                .map(scope -> findScope(realm, scope))
                .toList();
        if (defaultScope == null || optionalScopes.contains(null)) {
            throw invalid(INVALID_DETAIL);
        }

        new HashSet<>(client.getClientScopes(true).values()).forEach(client::removeClientScope);
        new HashSet<>(client.getClientScopes(false).values()).forEach(client::removeClientScope);
        client.addClientScope(defaultScope, true);
        optionalScopes.forEach(scope -> client.addClientScope(scope, false));
        client.getProtocolMappersStream().toList().forEach(client::removeProtocolMapper);

        UserModel serviceAccount = session.users().getServiceAccount(client);
        RoleModel workloadRole = realm.getRole(WORKLOAD_ROLE);
        if (serviceAccount == null || workloadRole == null) {
            throw invalid(INVALID_DETAIL);
        }
        serviceAccount.getRoleMappingsStream().toList().forEach(serviceAccount::deleteRoleMapping);
        serviceAccount.grantRole(workloadRole);
        validateFinalState(client);
    }

    private static ClientScopeModel findScope(RealmModel realm, String name) {
        return realm.getClientScopesStream()
                .filter(scope -> name.equals(scope.getName()))
                .findFirst()
                .orElse(null);
    }

    private void validateFinalState(ClientModel client) throws ClientPolicyException {
        Map<String, String> attributes = client.getAttributes();
        Set<String> directRoleNames;
        Set<String> effectiveRoleNames;
        UserModel serviceAccount = session.users().getServiceAccount(client);
        if (serviceAccount == null) {
            throw invalid(INVALID_DETAIL);
        }
        directRoleNames = serviceAccount.getRoleMappingsStream()
                .map(RoleModel::getName)
                .collect(Collectors.toUnmodifiableSet());
        effectiveRoleNames = RoleUtils.getDeepUserRoleMappings(serviceAccount).stream()
                .map(RoleModel::getName)
                .collect(Collectors.toUnmodifiableSet());

        if (!isWorkloadClient(client.getClientId())
                || !OIDCLoginProtocol.LOGIN_PROTOCOL.equals(client.getProtocol())
                || !JWTClientAuthenticator.PROVIDER_ID.equals(client.getClientAuthenticatorType())
                || !client.isServiceAccountsEnabled()
                || client.isPublicClient()
                || client.isBearerOnly()
                || client.isStandardFlowEnabled()
                || client.isImplicitFlowEnabled()
                || client.isDirectAccessGrantsEnabled()
                || client.isFullScopeAllowed()
                || hasValues(client.getRedirectUris())
                || hasValues(client.getWebOrigins())
                || hasText(client.getRootUrl())
                || hasText(client.getBaseUrl())
                || hasText(client.getManagementUrl())
                || client.getProtocolMappersStream().findAny().isPresent()
                || !Set.of(DEFAULT_SCOPE).equals(client.getClientScopes(true).keySet())
                || !OPTIONAL_SCOPES.equals(client.getClientScopes(false).keySet())
                || !ALLOWED_ATTRIBUTES.equals(attributes.keySet())
                || !FIXED_ATTRIBUTES.entrySet().stream()
                        .allMatch(entry -> entry.getValue().equals(attributes.get(entry.getKey())))
                || !Set.of(WORKLOAD_ROLE).equals(directRoleNames)
                || !Set.of(WORKLOAD_ROLE).equals(effectiveRoleNames)) {
            throw invalid(INVALID_DETAIL);
        }
    }

    private static void rejectAdminBypass(ClientCRUDContext context)
            throws ClientPolicyException {
        ClientRepresentation proposed = context.getProposedClientRepresentation();
        ClientModel target = context.getTargetClient();
        ClientModel authenticated = context.getAuthenticatedClient();
        if ((authenticated != null
                        && RUNTIME_ADMIN_CLIENT_ID.equals(authenticated.getClientId()))
                || (proposed != null
                        && (isWorkloadClient(proposed.getClientId())
                                || isWorkloadClient(proposed.getName())))
                || (target != null && isWorkloadClient(target.getClientId()))) {
            throw invalid(INVALID_DETAIL);
        }
    }

    private static void requireRuntimeAdmin(ClientCRUDContext context)
            throws ClientPolicyException {
        ClientModel authenticated = context.getAuthenticatedClient();
        if (authenticated == null
                || !RUNTIME_ADMIN_CLIENT_ID.equals(authenticated.getClientId())
                || context.getAuthenticatedUser() == null) {
            throw invalid(INVALID_DETAIL);
        }
    }

    private static void requireBoundRegistrationAccessToken(
            ClientCRUDContext context, ClientModel target) throws ClientPolicyException {
        JsonWebToken token = context.getToken();
        if (token == null
                || !ClientRegistrationTokenUtils.TYPE_REGISTRATION_ACCESS_TOKEN.equals(
                        token.getType())
                || !Objects.equals(target.getRegistrationToken(), token.getId())) {
            throw invalid(CROSS_CELL_DETAIL);
        }
    }

    private static boolean isAdminContext(ClientPolicyContext context) {
        return context instanceof AdminClientRegisterContext
                || context instanceof AdminClientUpdateContext
                || context instanceof AdminClientUpdatedContext
                || context instanceof AdminClientViewContext
                || context instanceof AdminClientUnregisterContext;
    }

    static boolean isWorkloadClient(String clientId) {
        return clientId != null && CLIENT_ID_PATTERN.matcher(clientId).matches();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasValues(Iterable<?> values) {
        return values != null && values.iterator().hasNext();
    }

    private static boolean exactSet(List<String> values, Set<String> expected) {
        return values != null && expected.equals(new HashSet<>(values)) && values.size() == expected.size();
    }

    private static ClientPolicyException invalid(String detail) {
        return new ClientPolicyException(
                OAuthErrorException.INVALID_CLIENT_METADATA,
                detail,
                Response.Status.BAD_REQUEST);
    }
}
