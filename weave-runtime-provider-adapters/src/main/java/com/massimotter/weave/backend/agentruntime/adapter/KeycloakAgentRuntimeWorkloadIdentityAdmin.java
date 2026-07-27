package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Keycloak 26.7 Admin REST anti-corruption boundary for the owned {@code weaver-cell-*} namespace. */
public final class KeycloakAgentRuntimeWorkloadIdentityAdmin
        implements RuntimeWorkloadIdentityAdmin, RuntimeWorkloadIdentityInventory {
    static final String CLIENT_AUTHENTICATOR_PRIVATE_KEY_JWT = "client-jwt";
    static final String CLIENT_ID_MAPPER_NAME = "weave-runtime-client-id";
    static final String WORKLOAD_ROLE_MAPPER_NAME = "weave-runtime-realm-role";
    static final String OWNER_ATTRIBUTE = "weave.arc.owner";
    static final String MANAGED_ATTRIBUTE = "weave.arc.managed";
    static final String SCHEMA_ATTRIBUTE = "weave.arc.schema";
    static final String ORGANIZATION_ATTRIBUTE = "weave.arc.organization";
    static final String PERSON_ATTRIBUTE = "weave.arc.person";
    static final String CELL_ATTRIBUTE = "weave.arc.cell";
    static final String MANAGED_VALUE = "agent-runtime-control";
    static final String SCHEMA_VALUE = "weave.arc.keycloak-client/v1";
    private static final Pattern SHA256_FINGERPRINT = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final int INVENTORY_PAGE_SIZE = 100;
    private static final int INVENTORY_MAX_CLIENTS = 10_000;

    private final Settings settings;
    private final RuntimeWorkloadCredentialStore credentials;
    private final KeycloakAdminAccessTokenProvider accessTokens;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public KeycloakAgentRuntimeWorkloadIdentityAdmin(
            Settings settings,
            RuntimeWorkloadCredentialStore credentials,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper objectMapper) {
        this(settings, credentials, accessTokens, objectMapper,
                HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
    }

    KeycloakAgentRuntimeWorkloadIdentityAdmin(
            Settings settings,
            RuntimeWorkloadCredentialStore credentials,
            KeycloakAdminAccessTokenProvider accessTokens,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.mapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command) {
        Objects.requireNonNull(command, "command");
        requireNamespace(command.clientId());
        String owner = RuntimeWorkloadOwnership.ownerFingerprint(
                command.organizationRef(), command.personRef(), command.cellRef(), command.clientId());
        Optional<Client> existing = findClient(command.clientId());
        RuntimeWorkloadCredentialState credential;
        if (existing.isPresent()) {
            ObjectNode representation = getClient(existing.orElseThrow().uuid());
            requireOwned(representation, command, owner);
            try {
                credential = credentials.find(command.clientId())
                        .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                                "The managed Keycloak workload client has no current SecretRef material"));
                requireCredential(credential, owner, command.authenticationMethod(), null);
            } catch (RuntimeException inconsistentCredential) {
                setEnabled(existing.orElseThrow().uuid(), false);
                throw inconsistentCredential;
            }
        } else {
            credential = credentials.find(command.clientId()).orElseGet(() -> credentials.create(
                    new RuntimeWorkloadCredentialStore.CreateCredentialCommand(
                            command.clientId(), owner, command.authenticationMethod())));
            requireCredential(credential, owner, command.authenticationMethod(), null);
            createClient(command, owner, credential, false);
            existing = findClient(command.clientId());
        }

        Client client = existing.orElseThrow(() -> new RuntimeWorkloadIdentityException(
                "The Keycloak workload client was not created deterministically"));
        ObjectNode representation = getClient(client.uuid());
        requireOwned(representation, command, owner);
        boolean enabledDuringReconciliation = representation.path("enabled").asBoolean(false);
        reconcileClient(client.uuid(), command, owner, credential, enabledDuringReconciliation);
        String subject = reconcileServiceAccount(client.uuid());
        if (!enabledDuringReconciliation) {
            setEnabled(client.uuid(), true);
        }
        verifyClient(client.uuid(), command, owner, credential, true, subject);
        return new RuntimeWorkloadBinding(
                settings.issuer().toString(), subject, command.clientId(),
                command.authenticationMethod(), credential.credentialRef());
    }

    @Override
    public RuntimeWorkloadBinding reconcileBinding(ReconcileBindingCommand command) {
        Objects.requireNonNull(command, "command");
        BoundClient bound = requireBoundClient(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding(), false);
        RuntimeWorkloadCredentialState credential;
        try {
            credential = credentials.find(command.binding().clientId())
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The managed Keycloak workload client has no current SecretRef material"));
            requireCredential(credential, bound.ownerFingerprint(), command.binding().authenticationMethod(),
                    command.binding().credentialRef());
        } catch (RuntimeException inconsistentCredential) {
            setEnabled(bound.client().uuid(), false);
            throw inconsistentCredential;
        }
        EnsureBindingCommand ensure = ensureCommand(
                command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding(), command.auditRef());
        boolean enabledDuringReconciliation = getClient(bound.client().uuid())
                .path("enabled").asBoolean(false);
        reconcileClient(
                bound.client().uuid(), ensure, bound.ownerFingerprint(), credential,
                enabledDuringReconciliation);
        String subject = reconcileServiceAccount(bound.client().uuid());
        if (!command.binding().subject().equals(subject)) {
            setEnabled(bound.client().uuid(), false);
            throw new RuntimeWorkloadIdentityException(
                    "The immutable workload service-account subject changed");
        }
        if (!enabledDuringReconciliation) {
            setEnabled(bound.client().uuid(), true);
        }
        verifyClient(bound.client().uuid(), ensure, bound.ownerFingerprint(), credential, true, subject);
        return command.binding();
    }

    @Override
    public void requireCurrentBinding(CurrentBindingCommand command) {
        Objects.requireNonNull(command, "command");
        BoundClient bound = requireBoundClient(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding(), true);
        EnsureBindingCommand ensure = ensureCommand(
                command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding(), command.auditRef());
        RuntimeWorkloadCredentialState credential = credentials.find(command.binding().clientId())
                .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                        "The enabled workload client has no current SecretRef material"));
        requireCredential(
                credential,
                bound.ownerFingerprint(),
                command.binding().authenticationMethod(),
                command.binding().credentialRef());
        ObjectNode current = getClient(bound.client().uuid());
        ObjectNode desired = desiredClient(ensure, bound.ownerFingerprint(), credential, true);
        if (clientSettingsDiffer(current, desired)) {
            throw new RuntimeWorkloadIdentityException("The current workload client settings have drifted");
        }
        verifyProtocolMappers(bound.client().uuid(), command.binding().clientId());
        verifyScopes(bound.client().uuid());
        verifyServiceAccountRoles(command.binding().subject());
    }

    @Override
    public RuntimeWorkloadBinding rotateBinding(RotateBindingCommand command) {
        Objects.requireNonNull(command, "command");
        BoundClient bound = requireBoundClient(command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding(), true);
        RuntimeWorkloadCredentialStore.RotateCredentialCommand rotation =
                new RuntimeWorkloadCredentialStore.RotateCredentialCommand(
                        command.binding().clientId(), bound.ownerFingerprint(), command.rotationRef());
        RuntimeWorkloadCredentialState prepared = credentials.prepareRotation(rotation);
        requireCredential(prepared, bound.ownerFingerprint(), command.binding().authenticationMethod(),
                command.binding().credentialRef());
        EnsureBindingCommand ensure = ensureCommand(command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding(), command.auditRef());
        reconcileClient(bound.client().uuid(), ensure, bound.ownerFingerprint(), prepared, true);
        RuntimeWorkloadCredentialState activated = credentials.activateRotation(rotation);
        requireCredential(activated, bound.ownerFingerprint(), command.binding().authenticationMethod(),
                command.binding().credentialRef());
        verifyClient(bound.client().uuid(), ensure, bound.ownerFingerprint(), activated, true,
                command.binding().subject());
        return command.binding();
    }

    @Override
    public RuntimeWorkloadBinding retirePreviousCredential(RetireCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        BoundClient bound = requireBoundClient(command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding(), true);
        RuntimeWorkloadCredentialStore.RetireCredentialCommand retirement =
                new RuntimeWorkloadCredentialStore.RetireCredentialCommand(
                        command.binding().clientId(), bound.ownerFingerprint(), command.rotationRef());
        RuntimeWorkloadCredentialState activeOnly = credentials.prepareRetirement(retirement);
        EnsureBindingCommand ensure = ensureCommand(command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding(), command.auditRef());
        reconcileClient(bound.client().uuid(), ensure, bound.ownerFingerprint(), activeOnly, true);
        RuntimeWorkloadCredentialState retired = credentials.completeRetirement(retirement);
        verifyClient(bound.client().uuid(), ensure, bound.ownerFingerprint(), retired, true,
                command.binding().subject());
        return command.binding();
    }

    @Override
    public void disableBinding(DisableBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = RuntimeWorkloadOwnership.ownerFingerprint(
                command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding().clientId());
        Optional<Client> client = findClient(command.binding().clientId());
        if (client.isPresent()) {
            BoundClient bound = requireBoundClient(command.organizationRef(), command.personRef(), command.cellRef(),
                    command.binding(), false);
            setEnabled(bound.client().uuid(), false);
            ObjectNode verified = getClient(bound.client().uuid());
            if (verified.path("enabled").asBoolean(true)) {
                throw new RuntimeWorkloadIdentityException("The Keycloak workload client did not become disabled");
            }
        }
        credentials.delete(new RuntimeWorkloadCredentialStore.DeleteCredentialCommand(
                command.binding().clientId(), owner));
    }

    @Override
    public void deleteBinding(DeleteBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = RuntimeWorkloadOwnership.ownerFingerprint(
                command.organizationRef(), command.personRef(), command.cellRef(),
                command.binding().clientId());
        Optional<Client> client = findClient(command.binding().clientId());
        if (client.isPresent()) {
            BoundClient bound = requireBoundClient(command.organizationRef(), command.personRef(), command.cellRef(),
                    command.binding(), false);
            setEnabled(bound.client().uuid(), false);
            response("DELETE", adminPath("/clients/" + path(bound.client().uuid())), null, Set.of(204));
            if (findClient(command.binding().clientId()).isPresent()) {
                throw new RuntimeWorkloadIdentityException("The Keycloak workload client deletion did not converge");
            }
        }
        credentials.delete(new RuntimeWorkloadCredentialStore.DeleteCredentialCommand(
                command.binding().clientId(), owner));
    }

    @Override
    public Snapshot scan() {
        List<ClientObservation> observations = new ArrayList<>();
        for (int first = 0; first < INVENTORY_MAX_CLIENTS; first += INVENTORY_PAGE_SIZE) {
            ArrayNode page = array(response(
                    "GET",
                    adminPath("/clients?first=" + first + "&max=" + INVENTORY_PAGE_SIZE
                            + "&viewableOnly=false"),
                    null,
                    Set.of(200)));
            for (JsonNode candidate : page) {
                String clientId = candidate.path("clientId").asText("");
                if (!clientId.startsWith("weaver-cell-")) {
                    continue;
                }
                requireNamespace(clientId);
                String providerRef = requiredText(candidate, "id");
                observations.add(observeClient(providerRef, getClient(providerRef)));
            }
            if (page.size() < INVENTORY_PAGE_SIZE) {
                return snapshot(observations);
            }
        }
        throw new RuntimeWorkloadIdentityException(
                "The reserved Keycloak workload-client inventory exceeds its safe bound");
    }

    @Override
    public void quarantineManaged(QuarantineManagedCommand command) {
        Objects.requireNonNull(command, "command");
        ObjectNode representation = getClient(command.providerRef());
        requireNamespace(command.clientId());
        if (!command.clientId().equals(representation.path("clientId").asText())) {
            throw new RuntimeWorkloadIdentityException(
                    "The Keycloak workload quarantine target changed before mutation");
        }
        JsonNode attributes = representation.path("attributes");
        if (managementState(attributes) != ManagementState.MANAGED
                || !command.ownerFingerprint().equals(attributes.path(OWNER_ATTRIBUTE).asText())) {
            throw new RuntimeWorkloadIdentityException(
                    "An unowned or changed workload client cannot be quarantined automatically");
        }
        setEnabled(command.providerRef(), false);
        if (getClient(command.providerRef()).path("enabled").asBoolean(true)) {
            throw new RuntimeWorkloadIdentityException(
                    "The managed Keycloak workload client did not become quarantined");
        }
    }

    private ClientObservation observeClient(String providerRef, ObjectNode representation) {
        String clientId = requiredText(representation, "clientId");
        requireNamespace(clientId);
        JsonNode attributes = representation.path("attributes");
        ManagementState management = managementState(attributes);
        boolean serviceAccountsEnabled = representation.path("serviceAccountsEnabled").asBoolean(false);
        String subject = null;
        if (serviceAccountsEnabled) {
            Response serviceAccount = response(
                    "GET",
                    adminPath("/clients/" + path(providerRef) + "/service-account-user"),
                    null,
                    Set.of(200, 404));
            if (serviceAccount.status() == 200) {
                subject = requiredText(json(serviceAccount), "id");
            }
        }
        return new ClientObservation(
                providerRef,
                clientId,
                representation.path("enabled").asBoolean(false),
                management,
                optionalText(attributes, OWNER_ATTRIBUTE),
                optionalText(attributes, ORGANIZATION_ATTRIBUTE),
                optionalText(attributes, PERSON_ATTRIBUTE),
                optionalText(attributes, CELL_ATTRIBUTE),
                serviceAccountsEnabled,
                subject,
                representation.path("clientAuthenticatorType").asText("unknown"),
                acceptedKeyIds(attributes));
    }

    private Snapshot snapshot(List<ClientObservation> observations) {
        List<ClientObservation> ordered = observations.stream()
                .sorted(java.util.Comparator.comparing(ClientObservation::clientId)
                        .thenComparing(ClientObservation::providerRef))
                .toList();
        ArrayNode projection = mapper.createArrayNode();
        for (ClientObservation observation : ordered) {
            ObjectNode item = mapper.createObjectNode();
            item.put("providerRef", observation.providerRef());
            item.put("clientId", observation.clientId());
            item.put("enabled", observation.enabled());
            item.put("managementState", observation.managementState().name());
            item.put("ownerFingerprint", observation.ownerFingerprint());
            item.put("organizationFingerprint", observation.organizationFingerprint());
            item.put("personFingerprint", observation.personFingerprint());
            item.put("cellFingerprint", observation.cellFingerprint());
            item.put("serviceAccountsEnabled", observation.serviceAccountsEnabled());
            item.put("serviceAccountSubject", observation.serviceAccountSubject());
            item.put("authenticationMethod", observation.authenticationMethod());
            ArrayNode kids = item.putArray("acceptedKeyIds");
            observation.acceptedKeyIds().stream().sorted().forEach(kids::add);
            projection.add(item);
        }
        try {
            return new Snapshot(
                    RuntimeWorkloadOwnership.fingerprint(mapper.writeValueAsString(projection)),
                    ordered);
        } catch (JacksonException exception) {
            throw new RuntimeWorkloadIdentityException(
                    "Unable to derive the support-safe workload inventory revision", exception);
        }
    }

    private Set<String> acceptedKeyIds(JsonNode attributes) {
        String encoded = attributes.path("jwks.string").asText("");
        if (encoded.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode keys = mapper.readTree(encoded).path("keys");
            if (!keys.isArray()) {
                return Set.of();
            }
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (JsonNode key : keys) {
                String kid = key.path("kid").asText("");
                if (kid.isBlank() || !result.add(kid)) {
                    return Set.of();
                }
            }
            return Set.copyOf(result);
        } catch (JacksonException invalidPublicKeySet) {
            return Set.of();
        }
    }

    private static ManagementState managementState(JsonNode attributes) {
        if (!MANAGED_VALUE.equals(attributes.path(MANAGED_ATTRIBUTE).asText())) {
            return ManagementState.UNOWNED;
        }
        if (!SCHEMA_VALUE.equals(attributes.path(SCHEMA_ATTRIBUTE).asText())
                || !fingerprint(attributes.path(OWNER_ATTRIBUTE).asText())
                || !fingerprint(attributes.path(ORGANIZATION_ATTRIBUTE).asText())
                || !fingerprint(attributes.path(PERSON_ATTRIBUTE).asText())
                || !fingerprint(attributes.path(CELL_ATTRIBUTE).asText())) {
            return ManagementState.MALFORMED;
        }
        return ManagementState.MANAGED;
    }

    private static boolean fingerprint(String value) {
        return value != null && SHA256_FINGERPRINT.matcher(value).matches();
    }

    private static String optionalText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private void createClient(
            EnsureBindingCommand command,
            String owner,
            RuntimeWorkloadCredentialState credential,
            boolean enabled) {
        ObjectNode desired = desiredClient(command, owner, credential, enabled);
        Response response = response("POST", adminPath("/clients"), desired, Set.of(201, 409));
        if (response.status() == 409) {
            Client concurrent = findClient(command.clientId())
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The Keycloak workload client creation conflicted without a resolvable client"));
            requireOwned(getClient(concurrent.uuid()), command, owner);
        }
    }

    private void reconcileClient(
            String clientUuid,
            EnsureBindingCommand command,
            String owner,
            RuntimeWorkloadCredentialState credential,
            boolean enabled) {
        ObjectNode current = getClient(clientUuid);
        requireOwned(current, command, owner);
        ObjectNode desired = desiredClient(command, owner, credential, enabled);
        if (clientSettingsDiffer(current, desired)) {
            response("PUT", adminPath("/clients/" + path(clientUuid)), desired, Set.of(204));
        }
        reconcileProtocolMappers(clientUuid, command.clientId());
        reconcileScopes(clientUuid);
    }

    private String reconcileServiceAccount(String clientUuid) {
        String subject = requiredText(json(response(
                "GET", adminPath("/clients/" + path(clientUuid) + "/service-account-user"), null, Set.of(200))), "id");
        ObjectNode workloadRole = realmRole();
        ArrayNode directRealmRoles = array(response(
                "GET", adminPath("/users/" + path(subject) + "/role-mappings/realm"), null, Set.of(200)));
        ArrayNode removeRealmRoles = mapper.createArrayNode();
        boolean hasWorkloadRole = false;
        for (JsonNode role : directRealmRoles) {
            if (settings.workloadRole().equals(role.path("name").asText())) {
                hasWorkloadRole = true;
            } else {
                removeRealmRoles.add(role);
            }
        }
        if (!removeRealmRoles.isEmpty()) {
            response("DELETE", adminPath("/users/" + path(subject) + "/role-mappings/realm"),
                    removeRealmRoles, Set.of(204));
        }
        if (!hasWorkloadRole) {
            ArrayNode role = mapper.createArrayNode().add(workloadRole);
            response("POST", adminPath("/users/" + path(subject) + "/role-mappings/realm"), role, Set.of(204));
        }
        removeClientRoleMappings(subject);
        verifyServiceAccountRoles(subject);
        return subject;
    }

    private void removeClientRoleMappings(String subject) {
        JsonNode mappings = json(response(
                "GET", adminPath("/users/" + path(subject) + "/role-mappings"), null, Set.of(200)));
        JsonNode clientMappings = mappings.path("clientMappings");
        if (clientMappings.isObject()) {
            clientMappings.properties().forEach(entry -> {
                JsonNode mapping = entry.getValue();
                String clientUuid = requiredText(mapping, "id");
                JsonNode roles = mapping.path("mappings");
                if (roles.isArray() && !roles.isEmpty()) {
                    response("DELETE", adminPath("/users/" + path(subject) + "/role-mappings/clients/"
                            + path(clientUuid)), roles, Set.of(204));
                }
            });
        }
    }

    private void verifyServiceAccountRoles(String subject) {
        ArrayNode realmRoles = array(response(
                "GET", adminPath("/users/" + path(subject) + "/role-mappings/realm"), null, Set.of(200)));
        List<String> names = new ArrayList<>();
        realmRoles.forEach(role -> names.add(role.path("name").asText()));
        if (!names.equals(List.of(settings.workloadRole()))) {
            throw new RuntimeWorkloadIdentityException("The workload service account realm roles are not exact");
        }
        JsonNode mappings = json(response(
                "GET", adminPath("/users/" + path(subject) + "/role-mappings"), null, Set.of(200)));
        JsonNode clientMappings = mappings.path("clientMappings");
        if (clientMappings.isObject() && clientMappings.size() != 0) {
            throw new RuntimeWorkloadIdentityException("The workload service account retains forbidden client roles");
        }
    }

    private ObjectNode realmRole() {
        ObjectNode role = object(response(
                "GET", adminPath("/roles/" + path(settings.workloadRole())), null, Set.of(200)));
        if (!settings.workloadRole().equals(role.path("name").asText()) || role.path("id").asText().isBlank()) {
            throw new RuntimeWorkloadIdentityException("The fixed workload realm role is unavailable");
        }
        return role;
    }

    private void reconcileScopes(String clientUuid) {
        Map<String, String> desiredScopeIds = resolveClientScopes();
        reconcileScopeSet(clientUuid, "default-client-scopes", settings.defaultClientScopes(), desiredScopeIds);
        reconcileScopeSet(clientUuid, "optional-client-scopes", settings.optionalClientScopes(), desiredScopeIds);
        verifyScopes(clientUuid);
    }

    private void reconcileScopeSet(
            String clientUuid,
            String endpoint,
            List<String> desiredNames,
            Map<String, String> desiredScopeIds) {
        ArrayNode current = array(response("GET",
                adminPath("/clients/" + path(clientUuid) + "/" + endpoint), null, Set.of(200)));
        Set<String> present = new HashSet<>();
        for (JsonNode scope : current) {
            String name = requiredText(scope, "name");
            String id = requiredText(scope, "id");
            if (desiredNames.contains(name)
                    && id.equals(desiredScopeIds.get(name))
                    && present.add(name)) {
                continue;
            }
            response("DELETE", adminPath("/clients/" + path(clientUuid) + "/" + endpoint + "/"
                    + path(id)), null, Set.of(204));
        }
        for (String desiredName : desiredNames) {
            if (!present.contains(desiredName)) {
                response("PUT", adminPath("/clients/" + path(clientUuid) + "/" + endpoint + "/"
                        + path(desiredScopeIds.get(desiredName))), null, Set.of(204));
            }
        }
    }

    private Map<String, String> resolveClientScopes() {
        Set<String> desiredNames = new LinkedHashSet<>(settings.defaultClientScopes());
        desiredNames.addAll(settings.optionalClientScopes());
        ArrayNode scopes = array(response("GET", adminPath("/client-scopes"), null, Set.of(200)));
        Map<String, List<String>> candidates = new LinkedHashMap<>();
        for (JsonNode scope : scopes) {
            String name = scope.path("name").asText();
            if (desiredNames.contains(name)) {
                candidates.computeIfAbsent(name, ignored -> new ArrayList<>()).add(requiredText(scope, "id"));
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : desiredNames) {
            List<String> matches = candidates.getOrDefault(name, List.of());
            if (matches.size() != 1) {
                throw new RuntimeWorkloadIdentityException("A fixed workload client scope is unavailable or ambiguous");
            }
            result.put(name, matches.getFirst());
        }
        return result;
    }

    private void verifyScopes(String clientUuid) {
        ArrayNode defaults = array(response(
                "GET", adminPath("/clients/" + path(clientUuid) + "/default-client-scopes"), null, Set.of(200)));
        ArrayNode optional = array(response(
                "GET", adminPath("/clients/" + path(clientUuid) + "/optional-client-scopes"), null, Set.of(200)));
        Set<String> defaultNames = new LinkedHashSet<>();
        defaults.forEach(scope -> defaultNames.add(scope.path("name").asText()));
        Set<String> optionalNames = new LinkedHashSet<>();
        optional.forEach(scope -> optionalNames.add(scope.path("name").asText()));
        if (!defaultNames.equals(new LinkedHashSet<>(settings.defaultClientScopes()))
                || !optionalNames.equals(new LinkedHashSet<>(settings.optionalClientScopes()))) {
            throw new RuntimeWorkloadIdentityException("The current workload client scopes have drifted");
        }
    }

    private void reconcileProtocolMappers(String clientUuid, String clientId) {
        ArrayNode mappers = array(response("GET",
                adminPath("/clients/" + path(clientUuid) + "/protocol-mappers/models"), null, Set.of(200)));
        Map<String, ObjectNode> expected = Map.of(
                CLIENT_ID_MAPPER_NAME, clientIdMapper(clientId),
                WORKLOAD_ROLE_MAPPER_NAME, workloadRoleMapper());
        Set<String> retained = new HashSet<>();
        for (JsonNode existing : mappers) {
            String id = requiredText(existing, "id");
            String name = existing.path("name").asText();
            ObjectNode desired = expected.get(name);
            if (desired != null && retained.add(name)) {
                if (!mapperMatches(existing, desired)) {
                    ObjectNode update = desired.deepCopy();
                    update.put("id", id);
                    response("PUT", adminPath("/clients/" + path(clientUuid) + "/protocol-mappers/models/"
                            + path(id)), update, Set.of(204));
                }
            } else {
                response("DELETE", adminPath("/clients/" + path(clientUuid) + "/protocol-mappers/models/"
                        + path(id)), null, Set.of(204));
            }
        }
        for (Map.Entry<String, ObjectNode> entry : expected.entrySet()) {
            if (!retained.contains(entry.getKey())) {
                response("POST", adminPath("/clients/" + path(clientUuid) + "/protocol-mappers/models"),
                        entry.getValue(), Set.of(201));
            }
        }
        ArrayNode verified = array(response("GET",
                adminPath("/clients/" + path(clientUuid) + "/protocol-mappers/models"), null, Set.of(200)));
        if (verified.size() != expected.size()) {
            throw new RuntimeWorkloadIdentityException("The workload client claim mapper is not exact");
        }
        Set<String> verifiedNames = new HashSet<>();
        for (JsonNode mapper : verified) {
            String name = mapper.path("name").asText();
            if (!verifiedNames.add(name) || !expected.containsKey(name)
                    || !mapperMatches(mapper, expected.get(name))) {
                throw new RuntimeWorkloadIdentityException("The workload client claim mapper is not exact");
            }
        }
    }

    private void verifyProtocolMappers(String clientUuid, String clientId) {
        Map<String, ObjectNode> expected = Map.of(
                CLIENT_ID_MAPPER_NAME, clientIdMapper(clientId),
                WORKLOAD_ROLE_MAPPER_NAME, workloadRoleMapper());
        ArrayNode current = array(response(
                "GET", adminPath("/clients/" + path(clientUuid) + "/protocol-mappers/models"), null, Set.of(200)));
        if (current.size() != expected.size()) {
            throw new RuntimeWorkloadIdentityException("The current workload client claim mappers have drifted");
        }
        Set<String> names = new HashSet<>();
        for (JsonNode mapper : current) {
            String name = mapper.path("name").asText();
            if (!names.add(name) || !expected.containsKey(name)
                    || !mapperMatches(mapper, expected.get(name))) {
                throw new RuntimeWorkloadIdentityException("The current workload client claim mappers have drifted");
            }
        }
    }

    private ObjectNode clientIdMapper(String clientId) {
        ObjectNode config = mapper.createObjectNode();
        config.put("claim.name", "client_id");
        config.put("claim.value", clientId);
        config.put("jsonType.label", "String");
        config.put("access.token.claim", "true");
        config.put("id.token.claim", "false");
        config.put("userinfo.token.claim", "false");
        config.put("introspection.token.claim", "false");
        ObjectNode expected = mapper.createObjectNode();
        expected.put("name", CLIENT_ID_MAPPER_NAME);
        expected.put("protocol", "openid-connect");
        expected.put("protocolMapper", "oidc-hardcoded-claim-mapper");
        expected.put("consentRequired", false);
        expected.set("config", config);
        return expected;
    }

    private ObjectNode workloadRoleMapper() {
        ObjectNode config = mapper.createObjectNode();
        config.put("claim.name", "realm_access.roles");
        config.put("jsonType.label", "String");
        config.put("multivalued", "true");
        config.put("access.token.claim", "true");
        config.put("id.token.claim", "false");
        config.put("userinfo.token.claim", "false");
        config.put("introspection.token.claim", "false");
        ObjectNode expected = mapper.createObjectNode();
        expected.put("name", WORKLOAD_ROLE_MAPPER_NAME);
        expected.put("protocol", "openid-connect");
        expected.put("protocolMapper", "oidc-usermodel-realm-role-mapper");
        expected.put("consentRequired", false);
        expected.set("config", config);
        return expected;
    }

    private static boolean mapperMatches(JsonNode actual, JsonNode expected) {
        return expected.path("name").equals(actual.path("name"))
                && expected.path("protocol").equals(actual.path("protocol"))
                && expected.path("protocolMapper").equals(actual.path("protocolMapper"))
                && expected.path("consentRequired").equals(actual.path("consentRequired"))
                && expected.path("config").equals(actual.path("config"));
    }

    private void verifyClient(
            String clientUuid,
            EnsureBindingCommand command,
            String owner,
            RuntimeWorkloadCredentialState credential,
            boolean enabled,
            String expectedSubject) {
        ObjectNode verified = getClient(clientUuid);
        requireOwned(verified, command, owner);
        ObjectNode desired = desiredClient(command, owner, credential, enabled);
        if (clientSettingsDiffer(verified, desired)) {
            throw new RuntimeWorkloadIdentityException("The Keycloak workload client settings did not converge");
        }
        String subject = requiredText(json(response(
                "GET", adminPath("/clients/" + path(clientUuid) + "/service-account-user"), null, Set.of(200))), "id");
        if (!subject.equals(expectedSubject)) {
            throw new RuntimeWorkloadIdentityException("The immutable workload service-account subject changed");
        }
    }

    private BoundClient requireBoundClient(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding,
            boolean requireEnabled) {
        requireNamespace(binding.clientId());
        if (!settings.issuer().toString().equals(binding.issuer())) {
            throw new RuntimeWorkloadIdentityException("The workload issuer does not match the configured Keycloak realm");
        }
        String owner = RuntimeWorkloadOwnership.ownerFingerprint(
                organizationRef, personRef, cellRef, binding.clientId());
        Client client = findClient(binding.clientId())
                .orElseThrow(() -> new RuntimeWorkloadIdentityException("The managed Keycloak workload client is unavailable"));
        EnsureBindingCommand ensure = ensureCommand(organizationRef, personRef, cellRef, binding, "audit:binding-check");
        ObjectNode representation = getClient(client.uuid());
        requireOwned(representation, ensure, owner);
        if (requireEnabled && !representation.path("enabled").asBoolean(false)) {
            throw new RuntimeWorkloadIdentityException("The managed Keycloak workload client is disabled");
        }
        String subject = requiredText(json(response(
                "GET", adminPath("/clients/" + path(client.uuid()) + "/service-account-user"), null, Set.of(200))), "id");
        if (!binding.subject().equals(subject)) {
            setEnabled(client.uuid(), false);
            throw new RuntimeWorkloadIdentityException("The immutable workload service-account subject is inconsistent");
        }
        try {
            Optional<RuntimeWorkloadCredentialState> credential = credentials.find(binding.clientId());
            if (credential.isPresent()) {
                requireCredential(
                        credential.orElseThrow(), owner, binding.authenticationMethod(), binding.credentialRef());
            } else if (requireEnabled) {
                setEnabled(client.uuid(), false);
                throw new RuntimeWorkloadIdentityException("The enabled workload client has no SecretRef material");
            }
        } catch (RuntimeException inconsistentCredential) {
            if (representation.path("enabled").asBoolean(false)) {
                setEnabled(client.uuid(), false);
            }
            throw inconsistentCredential;
        }
        return new BoundClient(client, owner);
    }

    private void setEnabled(String clientUuid, boolean enabled) {
        ObjectNode current = getClient(clientUuid);
        if (current.path("enabled").asBoolean(false) == enabled) {
            return;
        }
        current.put("enabled", enabled);
        current.remove("secret");
        response("PUT", adminPath("/clients/" + path(clientUuid)), current, Set.of(204));
    }

    private ObjectNode desiredClient(
            EnsureBindingCommand command,
            String owner,
            RuntimeWorkloadCredentialState credential,
            boolean enabled) {
        ObjectNode attributes = mapper.createObjectNode();
        ownershipAttributes(command, owner).forEach(attributes::put);
        attributes.put("access.token.lifespan", String.valueOf(settings.accessTokenLifespanSeconds()));
        attributes.put("token.endpoint.auth.signing.alg", FileRuntimeWorkloadCredentialStore.ALGORITHM);
        attributes.put("token.endpoint.auth.signing.max.exp", "60");
        attributes.put("use.jwks.url", "false");
        attributes.put("use.jwks.string", "true");
        attributes.put("jwks.string", credential.publicJwks());
        attributes.put("use.refresh.tokens", "false");
        attributes.put("client_credentials.use_refresh_token", "false");
        attributes.put("oauth2.device.authorization.grant.enabled", "false");
        attributes.put("oidc.ciba.grant.enabled", "false");
        attributes.put("standard.token.exchange.enabled", "false");
        attributes.put("standard.token.exchange.enableRefreshRequestedTokenType", "false");
        attributes.put("access.token.header.type.rfc9068", "true");

        ObjectNode desired = mapper.createObjectNode();
        desired.put("clientId", command.clientId());
        desired.put("name", "Weaver runtime cell");
        desired.put("description", "Managed per-cell workload identity; no member authority.");
        desired.put("enabled", enabled);
        desired.put("protocol", "openid-connect");
        desired.put("publicClient", false);
        desired.put("bearerOnly", false);
        desired.put("consentRequired", false);
        desired.put("standardFlowEnabled", false);
        desired.put("implicitFlowEnabled", false);
        desired.put("directAccessGrantsEnabled", false);
        desired.put("serviceAccountsEnabled", true);
        desired.put("authorizationServicesEnabled", false);
        desired.put("fullScopeAllowed", false);
        desired.put("frontchannelLogout", false);
        desired.put("alwaysDisplayInConsole", false);
        desired.put("clientAuthenticatorType", CLIENT_AUTHENTICATOR_PRIVATE_KEY_JWT);
        desired.set("attributes", attributes);
        desired.set("redirectUris", mapper.createArrayNode());
        desired.set("webOrigins", mapper.createArrayNode());
        ArrayNode defaultScopes = mapper.createArrayNode();
        settings.defaultClientScopes().forEach(defaultScopes::add);
        desired.set("defaultClientScopes", defaultScopes);
        ArrayNode optional = mapper.createArrayNode();
        settings.optionalClientScopes().forEach(optional::add);
        desired.set("optionalClientScopes", optional);
        desired.set("protocolMappers", mapper.createArrayNode()
                .add(clientIdMapper(command.clientId()))
                .add(workloadRoleMapper()));
        return desired;
    }

    private static boolean clientSettingsDiffer(JsonNode actual, JsonNode desired) {
        for (String field : List.of(
                "clientId", "name", "description", "protocol", "clientAuthenticatorType",
                "redirectUris", "webOrigins")) {
            if (!desired.path(field).equals(actual.path(field))) {
                return true;
            }
        }
        for (String field : List.of(
                "enabled", "publicClient", "bearerOnly", "consentRequired", "standardFlowEnabled",
                "implicitFlowEnabled", "directAccessGrantsEnabled", "serviceAccountsEnabled",
                "authorizationServicesEnabled", "fullScopeAllowed", "frontchannelLogout",
                "alwaysDisplayInConsole")) {
            if (desired.path(field).asBoolean(false) != actual.path(field).asBoolean(false)) {
                return true;
            }
        }
        JsonNode actualAttributes = actual.path("attributes");
        JsonNode desiredAttributes = desired.path("attributes");
        if (!actualAttributes.isObject()) {
            return true;
        }
        for (Map.Entry<String, JsonNode> expected : desiredAttributes.properties()) {
            if (!expected.getValue().equals(actualAttributes.path(expected.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private void requireOwned(ObjectNode representation, EnsureBindingCommand command, String owner) {
        if (!command.clientId().equals(representation.path("clientId").asText())) {
            throw new RuntimeWorkloadIdentityException("The Keycloak workload client identifier is inconsistent");
        }
        JsonNode attributes = representation.path("attributes");
        for (Map.Entry<String, String> marker : ownershipAttributes(command, owner).entrySet()) {
            if (!marker.getValue().equals(attributes.path(marker.getKey()).asText())) {
                throw new RuntimeWorkloadIdentityException(
                        "The Keycloak workload client is unowned, ambiguous, or cross-bound");
            }
        }
    }

    private static Map<String, String> ownershipAttributes(EnsureBindingCommand command, String owner) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(MANAGED_ATTRIBUTE, MANAGED_VALUE);
        result.put(SCHEMA_ATTRIBUTE, SCHEMA_VALUE);
        result.put(OWNER_ATTRIBUTE, owner);
        result.put(ORGANIZATION_ATTRIBUTE, RuntimeWorkloadOwnership.fingerprint(command.organizationRef()));
        result.put(PERSON_ATTRIBUTE, RuntimeWorkloadOwnership.fingerprint(command.personRef()));
        result.put(CELL_ATTRIBUTE, RuntimeWorkloadOwnership.fingerprint(command.cellRef()));
        return result;
    }

    private Optional<Client> findClient(String clientId) {
        requireNamespace(clientId);
        ArrayNode candidates = array(response("GET", adminPath("/clients?clientId=" + query(clientId)
                + "&search=true&first=0&max=20&viewableOnly=false"), null, Set.of(200)));
        List<Client> exact = new ArrayList<>();
        for (JsonNode candidate : candidates) {
            if (clientId.equals(candidate.path("clientId").asText())) {
                exact.add(new Client(requiredText(candidate, "id"), clientId));
            }
        }
        if (exact.size() > 1) {
            throw new RuntimeWorkloadIdentityException("The Keycloak workload client is ambiguous");
        }
        return exact.stream().findFirst();
    }

    private ObjectNode getClient(String clientUuid) {
        return object(response("GET", adminPath("/clients/" + path(clientUuid)), null, Set.of(200)));
    }

    private Response response(String method, String path, JsonNode body, Set<Integer> expectedStatuses) {
        String firstToken = accessTokens.accessToken();
        Response first = send(method, path, body, firstToken);
        if (first.status() != 401) {
            requireStatus(first, expectedStatuses);
            return first;
        }
        accessTokens.invalidate(firstToken);
        Response retry = send(method, path, body, accessTokens.accessToken());
        requireStatus(retry, expectedStatuses);
        return retry;
    }

    private Response send(String method, String path, JsonNode body, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeWorkloadIdentityException("The Keycloak administration token is unavailable");
        }
        byte[] requestBody = null;
        try {
            HttpRequest.BodyPublisher publisher;
            if (body == null) {
                publisher = HttpRequest.BodyPublishers.noBody();
            } else {
                requestBody = mapper.writeValueAsBytes(body);
                publisher = HttpRequest.BodyPublishers.ofByteArray(requestBody);
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(settings.adminBaseUrl().resolve(path))
                    .timeout(settings.timeout())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken);
            if (body != null) {
                request.header("Content-Type", "application/json");
            }
            HttpResponse<byte[]> response = httpClient.send(
                    request.method(method, publisher).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body());
        } catch (JacksonException exception) {
            throw new RuntimeWorkloadIdentityException("Unable to encode the Keycloak administration request", exception);
        } catch (IOException exception) {
            throw new RuntimeWorkloadIdentityException("Keycloak workload administration is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeWorkloadIdentityException("Keycloak workload administration was interrupted", exception);
        } finally {
            if (requestBody != null) {
                Arrays.fill(requestBody, (byte) 0);
            }
        }
    }

    private static void requireStatus(Response response, Set<Integer> expectedStatuses) {
        if (!expectedStatuses.contains(response.status())) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak workload administration failed with sanitized status " + response.status());
        }
    }

    private JsonNode json(Response response) {
        try {
            if (response.body().length == 0) {
                return mapper.nullNode();
            }
            return mapper.readTree(response.body());
        } catch (JacksonException exception) {
            throw new RuntimeWorkloadIdentityException("Keycloak returned an invalid administration response", exception);
        }
    }

    private ObjectNode object(Response response) {
        JsonNode value = json(response);
        if (!(value instanceof ObjectNode object)) {
            throw new RuntimeWorkloadIdentityException("Keycloak returned an invalid object response");
        }
        return object;
    }

    private ArrayNode array(Response response) {
        JsonNode value = json(response);
        if (!(value instanceof ArrayNode array)) {
            throw new RuntimeWorkloadIdentityException("Keycloak returned an invalid list response");
        }
        return array;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new RuntimeWorkloadIdentityException("Keycloak returned an incomplete administration projection");
        }
        return value;
    }

    private static void requireCredential(
            RuntimeWorkloadCredentialState credential,
            String owner,
            RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod,
            String expectedRef) {
        if (!owner.equals(credential.ownerFingerprint())
                || authenticationMethod != credential.authenticationMethod()
                || (expectedRef != null && !expectedRef.equals(credential.credentialRef()))) {
            throw new RuntimeWorkloadIdentityException("The workload SecretRef is cross-bound or inconsistent");
        }
    }

    private static EnsureBindingCommand ensureCommand(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding,
            String auditRef) {
        return new EnsureBindingCommand(
                organizationRef, personRef, cellRef, binding.clientId(), binding.authenticationMethod(), auditRef);
    }

    private String adminPath(String suffix) {
        return "/admin/realms/" + path(settings.realm()) + suffix;
    }

    private static String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(String value) {
        return path(value);
    }

    private static void requireNamespace(String clientId) {
        if (clientId == null || !clientId.matches("weaver-cell-[A-Za-z0-9_-]+")) {
            throw new RuntimeWorkloadIdentityException("Keycloak workload administration is restricted to weaver-cell-* clients");
        }
    }

    public record Settings(
            URI adminBaseUrl,
            URI issuer,
            String realm,
            Duration timeout,
            String workloadRole,
            List<String> defaultClientScopes,
            List<String> optionalClientScopes,
            int accessTokenLifespanSeconds) {
        public Settings {
            requireHttp(adminBaseUrl, "adminBaseUrl", false);
            requireHttp(issuer, "issuer", true);
            if (realm == null || realm.isBlank() || realm.contains("/")) {
                throw new IllegalArgumentException("realm is required");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (!"weaver-runtime".equals(workloadRole)) {
                throw new IllegalArgumentException("workloadRole must be weaver-runtime");
            }
            defaultClientScopes = defaultClientScopes == null ? List.of() : List.copyOf(defaultClientScopes);
            if (defaultClientScopes.size() != 1
                    || !defaultClientScopes.contains("weaver-runtime-workload")) {
                throw new IllegalArgumentException(
                        "defaultClientScopes must contain only weaver-runtime-workload");
            }
            optionalClientScopes = optionalClientScopes == null ? List.of() : List.copyOf(optionalClientScopes);
            if (optionalClientScopes.isEmpty()
                    || optionalClientScopes.stream().anyMatch(value -> value == null || value.isBlank())
                    || new HashSet<>(optionalClientScopes).size() != optionalClientScopes.size()
                    || !optionalClientScopes.containsAll(
                            List.of("agent-runtime.profile.read", "mcp.tools", "files.read"))) {
                throw new IllegalArgumentException(
                        "optionalClientScopes must be unique and contain agent-runtime.profile.read, mcp.tools, and files.read");
            }
            if (!java.util.Collections.disjoint(defaultClientScopes, optionalClientScopes)) {
                throw new IllegalArgumentException("default and optional client scopes must not overlap");
            }
            if (accessTokenLifespanSeconds < 5 || accessTokenLifespanSeconds > 300) {
                throw new IllegalArgumentException("workload access-token lifespan must be between 5 and 300 seconds");
            }
        }

        private static void requireHttp(URI uri, String field, boolean httpsOnly) {
            if (uri == null
                    || uri.getHost() == null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                        || (!httpsOnly && "http".equalsIgnoreCase(uri.getScheme())))) {
                throw new IllegalArgumentException(field + " must be an absolute "
                        + (httpsOnly ? "HTTPS" : "HTTP(S)") + " URI");
            }
        }
    }

    private record Client(String uuid, String clientId) {}

    private record BoundClient(Client client, String ownerFingerprint) {}

    private record Response(int status, byte[] body) {
        Response {
            body = body == null ? new byte[0] : body;
        }
    }
}
