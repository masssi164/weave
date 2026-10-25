package com.massimotter.weave.backend.dogfood;

import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;
import com.massimotter.weave.backend.audit.*;
import com.massimotter.weave.backend.security.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.*;
import com.massimotter.weave.backend.service.*;
import com.massimotter.weave.backend.spaces.port.DogfoodSpacePreparationPort;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;

/** Local operator workflow. Managed test setup never creates a human session or product API admission. */
public class DogfoodPreparationService {
    private static final String ISSUER = "urn:weave:dogfood-operator";
    private final KeycloakIdentityAdminClient keycloak;
    private final OrganizationIdentityContextResolver identities;
    private final WorkspaceCapabilityService capabilities;
    private final DeploymentOrganizationProperties organization;
    private final OAuth2ResourceServerProperties resourceServer;
    private final ProvisioningIntentRepository intents;
    private final MemberInvitationService invitations;
    private final IdentityOpaqueReferenceCodec references;
    private final DogfoodSpacePreparationPort spaces;
    private final DogfoodOperatorPlanBinding bindings;
    private Request request;
    private Owner owner;
    private final List<MemberReceipt> progress = new ArrayList<>();
    private boolean created;
    private final List<PreparedMember> preparedMembers = new ArrayList<>();

    public DogfoodPreparationService(KeycloakIdentityAdminClient keycloak, OrganizationIdentityContextResolver identities,
            WorkspaceCapabilityService capabilities, DeploymentOrganizationProperties organization,
            OAuth2ResourceServerProperties resourceServer, ProvisioningIntentRepository intents,
            MemberInvitationService invitations, IdentityOpaqueReferenceCodec references,
            DogfoodSpacePreparationPort spaces, DogfoodOperatorPlanBinding bindings) {
        this.keycloak = keycloak; this.identities = identities; this.capabilities = capabilities;
        this.organization = organization; this.resourceServer = resourceServer; this.intents = intents;
        this.invitations = invitations; this.references = references; this.spaces = spaces;
        this.bindings = bindings;
    }

    public Envelope execute(Request request) {
        validate(request);
        this.request = request;
        owner = null;
        progress.clear();
        created = false;
        preparedMembers.clear();
        for (var member : request.plan().members()) progress.add(empty(member.slot()));
        try {
            owner = currentOwner();
            requirePreviousOwner();
            spaces.verifyOwner(owner.context().organizationId(), owner.context().accountId());
            bindings.bind(request, owner.context().organizationId(), owner.context().accountId(), owner.handle());
            if ("provision-members".equals(request.operation())) {
                provisionManagedMembers();
                return envelope("none");
            }
            // Inspect BOTH targets before either invitation is created.
            var found = new ArrayList<ProvisioningIntent>();
            for (int i = 0; i < 2; i++) found.add(inspect(i));
            if ("invitations".equals(request.operation())) {
                for (int i = 0; i < 2; i++) {
                    if (found.get(i) == null) {
                        var planned = request.plan().members().get(i);
                        created = true;
                        invitations.createDogfoodOperatorInvitation(owner.context().organizationId(), organization.keycloakId(),
                                request.plan().planId(), planned.slot(), URI.create(request.plan().organizationOrigin()).getHost());
                        inspect(i);
                    }
                }
            } else {
                List<DogfoodSpacePreparationPort.Target> targets = java.util.stream.IntStream.range(0, 2)
                        .mapToObj(i -> new DogfoodSpacePreparationPort.Target(progress.get(i).accountRef(), request.previousReceipt() == null
                                ? null : request.previousReceipt().members().get(i).grantVersion())).toList();
                require(targets.stream().allMatch(value -> value.accountRef() != null), "test-member-not-activated");
                var grants = spaces.grantTwo(owner.context().organizationId(), owner.context().accountId(), targets,
                        () -> {
                            Owner fresh = currentOwner();
                            require(fresh.context().accountId().equals(owner.context().accountId()), "owner-unavailable");
                            for (int i = 0; i < 2; i++) inspect(i);
                        }, result -> publishGrantAudit(result));
                for (int i = 0; i < 2; i++) {
                    var old = progress.get(i); var grant = grants.get(i);
                    created |= grant.created();
                    progress.set(i, new MemberReceipt(old.slot(), old.intentRef(), old.invitationHandle(), old.memberHandle(),
                            old.accountRef(), grant.strongEtag(), old.intentState(), grant.created() ? "CREATED" : "VERIFIED"));
                }
            }
            if ("invitations".equals(request.operation())) {
                bindings.observe(request, owner.context().organizationId(), AuditAction.DOGFOOD_PREPARATION_VERIFIED,
                        "dogfood-invitations-" + request.nonce(),
                        Map.of("intentRefs", progress.stream().map(MemberReceipt::intentRef).toList(), "result", "VERIFIED"));
            }
            return envelope("none");
        } catch (Blocked failure) { return envelope(failure.boundary); }
        catch (DogfoodSpacePreparationPort.OwnerConflict failure) { return envelope("owner-space-access-conflict"); }
        catch (DogfoodSpacePreparationPort.Conflict failure) { return envelope("space-access-conflict"); }
        catch (DogfoodSpacePreparationPort.VersionConflict failure) { return envelope("space-version-conflict"); }
        catch (AuditRequiredException failure) { return envelope("audit-unavailable"); }
        catch (org.springframework.dao.DataAccessException failure) { return envelope("persistence-unavailable"); }
        catch (KeycloakIdentityAdminClient.ManagedTestConflict failure) { return envelope(failure.getMessage()); }
        catch (KeycloakIdentityAdminClient.KeycloakAdminException failure) { return envelope("identity-provider-unavailable"); }
        catch (ApiErrorException failure) {
            return envelope("member-invitation-provider-unavailable".equals(failure.code()) ? "identity-provider-unavailable"
                    : "member-invitation-already-pending".equals(failure.code()) ? "invitation-conflict" : "operation-incomplete");
        }
        catch (RuntimeException failure) { return envelope("operation-incomplete"); }
    }

    private List<ProvisioningIntent> operatorHistory(PlannedMember planned) {
        return Stream.concat(Stream.of(ISSUER), Stream.of("urn:weave:dogfood-managed-fixture"))
                .flatMap(issuer -> intents.findByActorAndCorrelation(owner.context().organizationId(), organization.keycloakId(),
                        issuer, actor(request), planned.invitationIdempotencyKey()).stream()).toList();
    }

    private KeycloakIdentityAdminClient.ManagedTestMember managedAuthority(PlannedMember planned, ProvisioningIntent intent) {
        return new KeycloakIdentityAdminClient.ManagedTestMember(organization.keycloakId(), request.plan().planId(),
                planned.slot(), URI.create(request.plan().organizationOrigin()).getHost(),
                intent == null ? java.util.UUID.randomUUID().toString() : intent.intentId().toString(),
                intent == null ? null : intent.appliedSubject(), intent != null && intent.providerInvitationId() != null,
                intent == null ? Instant.now() : intent.createdAt(), request.plan().issuerUrl(),
                intent == null ? KeycloakIdentityAdminClient.ManagedTestProgress.empty()
                        : java.util.Objects.requireNonNull(bindings.managedProgress(request, owner.context().organizationId(),
                                intent.intentId().toString(), planned.slot())));
    }

    private ProvisioningIntent inspectManaged(int index) {
        var planned = request.plan().members().get(index);
        var history = operatorHistory(planned);
        var byEmail = intents.findByEmail(owner.context().organizationId(), organization.keycloakId(), planned.email());
        require(history.size() <= 1 && byEmail.size() == history.size()
                && byEmail.stream().allMatch(value -> history.stream().anyMatch(known -> known.intentId().equals(value.intentId()))),
                "invitation-conflict");
        var previous = request.previousReceipt() == null ? null : request.previousReceipt().members().get(index);
        ProvisioningIntent intent = history.isEmpty() ? null : history.getFirst();
        if (intent == null) {
            require(previous == null || previous.intentRef() == null, "operator-reference-conflict");
            require(keycloak.invitationsForEmail(organization.keycloakId(), planned.email()).isEmpty(), "invitation-conflict");
        } else {
            require(planned.email().equals(intent.invitedEmail()) && "member".equals(intent.requestedRole())
                    && intent.status() != ProvisioningIntentStatus.FAILED, "managed-test-identity-conflict");
            require(previous == null || equalIfKnown(previous.intentRef(), intent.intentId().toString()), "operator-reference-conflict");
            if (intent.providerInvitationId() == null) {
                require("urn:weave:dogfood-managed-fixture".equals(intent.invitedByIssuer()), "invitation-outcome-uncertain");
            } else if (intent.appliedSubject() == null) {
                var invitations = keycloak.invitationsForEmail(organization.keycloakId(), planned.email());
                require(invitations.size() == 1 && intent.providerInvitationId().equals(invitations.getFirst().providerInvitationId())
                        && planned.displayName().equals(invitations.getFirst().displayName()), "invitation-conflict");
                require(!"revoked".equals(invitations.getFirst().lifecycleStatus()), "invitation-revoked");
                require(List.of("pending", "accepted", "expired").contains(invitations.getFirst().lifecycleStatus()),
                        "invitation-outcome-uncertain");
            }
            progress.set(index, new MemberReceipt(planned.slot(), intent.intentId().toString(),
                    intent.providerInvitationId() == null ? null : references.invitation(organization.keycloakId(), intent.providerInvitationId()),
                    null, null, null, intent.status().name(), "NOT_REQUESTED"));
        }
        keycloak.verifyManagedTestMember(managedAuthority(planned, intent));
        return intent;
    }

    private void provisionManagedMembers() {
        // Inspect both identities before writes, including any account with incomplete historical setup.
        var found = new ArrayList<ProvisioningIntent>();
        for (int i = 0; i < 2; i++) found.add(inspectManaged(i));
        for (int i = 0; i < 2; i++) {
            var planned = request.plan().members().get(i);
            var intent = found.get(i);
            if (intent == null) {
                intent = invitations.createDogfoodManagedTestIntent(owner.context().organizationId(), organization.keycloakId(),
                        request.plan().planId(), planned.slot(), URI.create(request.plan().organizationOrigin()).getHost());
                created = true;
            }
            bindings.observe(request, owner.context().organizationId(), AuditAction.DOGFOOD_PREPARATION_AUTHORIZED,
                    "dogfood-managed-setup-" + request.nonce() + "-" + planned.slot(),
                    Map.of("intentRef", intent.intentId().toString(), "slot", planned.slot(), "operation", "managed-initial-setup"));
            String intentRef = intent.intentId().toString();
            var actual = keycloak.provisionManagedTestMember(managedAuthority(planned, intent), request.initialCredentials().get(i).password(),
                    step -> bindings.managedStep(request, owner.context().organizationId(), intentRef, planned.slot(), step));
            created |= actual.created() || actual.initialCredentialCreated();
            var applied = invitations.completeDogfoodManagedTestIntent(intent, actual.subject());
            String account = IdentityReferences.accountId(request.plan().issuerUrl(), actual.subject());
            require(!account.equals(owner.context().accountId()), "managed-test-identity-conflict");
            var previous = request.previousReceipt() == null ? null : request.previousReceipt().members().get(i);
            String handle = references.member(organization.keycloakId(), actual.subject());
            String invitation = applied.providerInvitationId() == null ? null : references.invitation(organization.keycloakId(), applied.providerInvitationId());
            require(previous == null || (equalIfKnown(previous.accountRef(), account) && equalIfKnown(previous.memberHandle(), handle)
                    && equalIfKnown(previous.invitationHandle(), invitation)), "operator-reference-conflict");
            progress.set(i, new MemberReceipt(planned.slot(), applied.intentId().toString(), invitation,
                    handle, account, null, "APPLIED", "NOT_REQUESTED"));
            preparedMembers.add(new PreparedMember(planned.slot(), actual.subject()));
        }
        require(!progress.get(0).accountRef().equals(progress.get(1).accountRef()), "managed-test-identity-conflict");
        var targets = java.util.stream.IntStream.range(0, 2).mapToObj(i -> new DogfoodSpacePreparationPort.Target(
                progress.get(i).accountRef(), request.previousReceipt() == null ? null : request.previousReceipt().members().get(i).grantVersion())).toList();
        var grants = spaces.grantTwo(owner.context().organizationId(), owner.context().accountId(), targets, () -> {
            Owner fresh = currentOwner();
            require(fresh.context().accountId().equals(owner.context().accountId()), "owner-unavailable");
            for (int i = 0; i < 2; i++) {
                var current = keycloak.memberAuthorization(organization.keycloakId(), preparedMembers.get(i).identityRef());
                require(current.enabled() && current.activated() && current.roles().equals(List.of("member"))
                        && current.subject().equals(preparedMembers.get(i).identityRef()), "managed-test-identity-conflict");
            }
        }, this::publishGrantAudit);
        for (int i = 0; i < 2; i++) {
            var old = progress.get(i); var grant = grants.get(i);
            created |= grant.created();
            progress.set(i, new MemberReceipt(old.slot(), old.intentRef(), old.invitationHandle(), old.memberHandle(),
                    old.accountRef(), grant.strongEtag(), old.intentState(), grant.created() ? "CREATED" : "VERIFIED"));
        }
        bindings.observe(request, owner.context().organizationId(), AuditAction.DOGFOOD_PREPARATION_VERIFIED,
                "dogfood-managed-ready-" + request.nonce(), Map.of("intentRefs", progress.stream().map(MemberReceipt::intentRef).toList(),
                        "result", "VERIFIED", "authenticationEvidence", false));
    }

    private ProvisioningIntent inspect(int index) {
        var planned = request.plan().members().get(index);
        List<ProvisioningIntent> history = intents.findByActorAndCorrelation(owner.context().organizationId(),
                organization.keycloakId(), ISSUER, actor(request), planned.invitationIdempotencyKey());
        var emailHistory = intents.findByEmail(owner.context().organizationId(), organization.keycloakId(), planned.email());
        require(history.size() <= 1 && emailHistory.size() == history.size()
                && emailHistory.stream().allMatch(value -> history.stream().anyMatch(known -> known.intentId().equals(value.intentId()))),
                "invitation-conflict");
        var previous = request.previousReceipt() == null ? null : request.previousReceipt().members().get(index);
        var members = keycloak.members(organization.keycloakId()).stream()
                .filter(member -> planned.email().equalsIgnoreCase(member.email())).toList();
        require(members.size() <= 1, "test-member-state-conflict");
        if (history.isEmpty()) {
            require(previous == null || (previous.intentRef() == null && previous.invitationHandle() == null
                    && previous.memberHandle() == null && previous.accountRef() == null), "operator-reference-conflict");
            require("invitations".equals(request.operation()), "invitation-conflict");
            require(members.isEmpty() && keycloak.invitationsForEmail(organization.keycloakId(), planned.email()).isEmpty(),
                    "invitation-conflict");
            return null;
        }
        var intent = history.getFirst();
        require(planned.email().equals(intent.invitedEmail()) && "member".equals(intent.requestedRole()), "invitation-conflict");
        String invitation = intent.providerInvitationId() == null ? null
                : references.invitation(organization.keycloakId(), intent.providerInvitationId());
        progress.set(index, new MemberReceipt(planned.slot(), intent.intentId().toString(), invitation,
                null, null, null, intent.status().name(), "NOT_REQUESTED"));
        require(previous == null || (equalIfKnown(previous.intentRef(), intent.intentId().toString())
                && equalIfKnown(previous.invitationHandle(), invitation)), "operator-reference-conflict");
        require(intent.status() != ProvisioningIntentStatus.FAILED, "invitation-outcome-uncertain");
        require(intent.status() != ProvisioningIntentStatus.EXPIRED, "invitation-expired");
        require(invitation != null, "invitation-outcome-uncertain");
        if (intent.status() == ProvisioningIntentStatus.PENDING) {
            require(members.isEmpty(), "test-member-not-activated");
            require(intent.expiresAt().isAfter(Instant.now()), "invitation-expired");
            var pending = keycloak.invitationsForEmail(organization.keycloakId(), planned.email());
            require(pending.size() == 1 && intent.providerInvitationId().equals(pending.getFirst().providerInvitationId())
                    && planned.displayName().equals(pending.getFirst().displayName()), "invitation-conflict");
            var provider = pending.getFirst();
            require(!"revoked".equals(provider.lifecycleStatus()), "invitation-revoked");
            require("pending".equals(provider.lifecycleStatus()), "invitation-outcome-uncertain");
            require(provider.expiresAt() != null && provider.expiresAt().isAfter(Instant.now()), "invitation-expired");
            require("invitations".equals(request.operation()), "test-member-not-activated");
            return intent;
        }
        require(intent.status() == ProvisioningIntentStatus.APPLIED && members.size() == 1, "test-member-state-conflict");
        var member = members.getFirst();
        require(member.enabled() && member.subject().equals(intent.appliedSubject()) && member.roles().equals(List.of("member"))
                && planned.displayName().equals(member.displayName()), "test-member-state-conflict");
        var live = keycloak.memberAuthorization(organization.keycloakId(), member.subject());
        require(live.enabled() && live.subject().equals(member.subject()) && live.roles().equals(List.of("member")), "test-member-state-conflict");
        require(live.activated(), "test-member-not-activated");
        String account = IdentityReferences.accountId(request.plan().issuerUrl(), member.subject());
        String handle = references.member(organization.keycloakId(), member.subject());
        require(previous == null || (equalIfKnown(previous.memberHandle(), handle) && equalIfKnown(previous.accountRef(), account)),
                "operator-reference-conflict");
        require(!account.equals(owner.context().accountId()), "test-member-state-conflict");
        progress.set(index, new MemberReceipt(planned.slot(), intent.intentId().toString(), invitation, handle,
                account, null, "APPLIED", "NOT_REQUESTED"));
        if ("grants".equals(request.operation())) {
            var proof = request.proof().members().get(index);
            require(proof.accountDigest().equals(digest("weave-account", request.plan().issuerUrl(), account))
                    && proof.organizationDigest().equals(digest("weave-organization", request.plan().issuerUrl(),
                            owner.context().organizationId())), "member-proof-conflict");
        }
        return intent;
    }

    private Owner currentOwner() {
        require(organization.configured() && organization.keycloakId().equals(keycloak.configuredOrganizationId()), "configuration-mismatch");
        var owners = keycloak.members(organization.keycloakId()).stream().filter(member -> member.roles().contains("owner")).toList();
        require(owners.size() == 1 && owners.getFirst().enabled() && owners.getFirst().roles().equals(List.of("owner")), "owner-unavailable");
        var provider = owners.getFirst();
        var live = keycloak.memberAuthorization(organization.keycloakId(), provider.subject());
        require(live.enabled() && live.activated() && live.roles().equals(List.of("owner"))
                && live.subject().equals(provider.subject()), "owner-unavailable");
        String name = provider.displayName();
        require(name != null && !name.isBlank() && name.codePointCount(0, name.length()) <= 200
                && name.codePoints().noneMatch(Character::isISOControl), "owner-unavailable");
        String issuer = resourceServer.getJwt().getIssuerUri();
        require(request.plan().issuerUrl().equals(issuer), "configuration-mismatch");
        var context = new OrganizationIdentityContext(identities.configuredOrganizationId(), issuer, live.subject(),
                IdentityReferences.primaryIdentityKey(issuer, live.subject()), IdentityReferences.accountId(issuer, live.subject()),
                live.roles(), live.groups(), List.of(), Stream.concat(live.roles().stream().map(role -> "role_claim:" + role),
                live.groups().stream().map(group -> "group_claim:" + group)).sorted().toList());
        try { capabilities.requireCapabilityForVerifiedMember(context, "admin.policy.edit", "spaces", "dogfood-prepare-members"); }
        catch (ApiErrorException denied) { throw new Blocked("owner-policy-denied"); }
        return new Owner(context, references.member(organization.keycloakId(), live.subject()), name);
    }

    private void requirePreviousOwner() {
        var p = request.previousReceipt();
        if (p == null) return;
        require(equalIfKnown(p.organizationId(), owner.context().organizationId())
                && equalIfKnown(p.intendedOwnerAccountRef(), owner.context().accountId())
                && equalIfKnown(p.intendedOwnerMemberHandle(), owner.handle()), "operator-reference-conflict");
    }
    private void publishGrantAudit(List<DogfoodSpacePreparationPort.Grant> grants) {
        for (var grant : grants) bindings.observe(request, owner.context().organizationId(), AuditAction.SPACE_MEMBERSHIP_UPDATED,
                "dogfood-grant-" + request.nonce() + "-" + grant.accountRef(),
                Map.of("accountRef", grant.accountRef(), "permissionLevel", "EDIT", "result", "VERIFIED"));
    }
    private static boolean equalIfKnown(String expected, String actual) { return expected == null || expected.equals(actual); }
    private static MemberReceipt empty(String slot) { return new MemberReceipt(slot, null, null, null, null, null, "NOT_ISSUED", "NOT_REQUESTED"); }
    private Envelope envelope(String boundary) {
        boolean passed = "none".equals(boundary);
        if (!passed && owner != null) captureDurableReferences();
        return new Envelope("weave.dogfood-operator-preparation-envelope/v1", new Receipt(
                "weave.dogfood-operator-preparation-receipt/v1", "dogfood-operator", "dogfood-operator", actor(request),
                request.operation(), passed ? "passed" : (created ? "incomplete" : "blocked"),
                passed ? request.operation() + "-ready" : "incomplete", boundary,
                passed ? (created ? "CREATED" : "VERIFIED") : (created ? "PARTIAL" : "BLOCKED"), request.nonce(), request.plan().planId(),
                "dogfood", request.candidateCommit(), request.sourceTree(), request.specificationCommit(), request.plan().organizationOrigin(),
                request.apiBaseUrl(), request.plan().issuerUrl(), owner == null ? null : owner.context().organizationId(), "workspace-default",
                owner == null ? null : owner.context().accountId(), owner == null ? null : owner.handle(), List.copyOf(progress), true, false),
                new PrivateData(owner == null ? null : owner.displayName(),
                        passed && "provision-members".equals(request.operation()) ? List.copyOf(preparedMembers) : null));
    }
    private void captureDurableReferences() {
        for (int i = 0; i < 2; i++) {
            var planned = request.plan().members().get(i);
            try {
                var known = operatorHistory(planned);
                if (known.size() == 1) {
                    var intent = known.getFirst(); var old = progress.get(i);
                    progress.set(i, new MemberReceipt(old.slot(), intent.intentId().toString(),
                            intent.providerInvitationId() == null ? null : references.invitation(organization.keycloakId(), intent.providerInvitationId()),
                            old.memberHandle(), old.accountRef(), old.grantVersion(), intent.status().name(), old.grantOutcome()));
                }
            } catch (RuntimeException unavailable) {
                var old = progress.get(i);
                progress.set(i, new MemberReceipt(old.slot(), old.intentRef(), old.invitationHandle(), old.memberHandle(),
                        old.accountRef(), old.grantVersion(), "UNAVAILABLE", old.grantOutcome()));
            }
        }
    }
    private record Owner(OrganizationIdentityContext context, String handle, String displayName) {}
}
