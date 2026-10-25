package com.massimotter.weave.backend.dogfood;

import com.massimotter.weave.backend.identity.invitation.domain.ProvisioningIntentStatus;

import com.massimotter.weave.backend.identity.invitation.domain.ProvisioningIntent;

import com.massimotter.weave.backend.identity.invitation.application.MemberInvitationService;

import com.massimotter.weave.backend.identity.keycloak.IdentityInvitationProperties;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.security.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;
import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocolTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.massimotter.weave.backend.audit.*;
import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.*;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.*;
import com.massimotter.weave.backend.service.*;
import com.massimotter.weave.backend.spaces.port.DogfoodSpacePreparationPort;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;

class DogfoodPreparationServiceTest {
    private final KeycloakIdentityAdminClient keycloak = mock(KeycloakIdentityAdminClient.class);
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final OrganizationIdentityContextResolver identities = mock(OrganizationIdentityContextResolver.class);
    private final IdentityOpaqueReferenceCodec references = mock(IdentityOpaqueReferenceCodec.class);
    private final DogfoodSpacePreparationPort spaces = mock(DogfoodSpacePreparationPort.class);
    private final DogfoodOperatorPlanBinding bindings = mock(DogfoodOperatorPlanBinding.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private final InMemoryProvisioningIntentRepository intents = new InMemoryProvisioningIntentRepository();
    private final Map<String, ProviderInvitation> providerInvitations = new HashMap<>();
    private final List<ProviderMember> members = new ArrayList<>();
    private DogfoodPreparationService service;

    @BeforeEach void setup() {
        var properties = new IdentityInvitationProperties();
        properties.keycloak().setOrganizationId("provider-org");
        var resource = new OAuth2ResourceServerProperties(); resource.getJwt().setIssuerUri(ISSUER);
        when(identities.configuredOrganizationId()).thenReturn("org");
        when(bindings.managedProgress(any(), anyString(), anyString(), anyString())).thenReturn(ManagedTestProgress.empty());
        when(keycloak.configuredOrganizationId()).thenReturn("provider-org");
        members.add(new ProviderMember("real-owner", "owner@example.test", "Actual owner", List.of("owner"), List.of(), true));
        when(keycloak.members("provider-org")).thenAnswer(invocation -> List.copyOf(members));
        when(keycloak.memberAuthorization("provider-org", "real-owner")).thenReturn(
                new MemberAuthorization("real-owner", true, true, List.of("owner"), List.of("/owners")));
        when(references.member(eq("provider-org"), anyString())).thenAnswer(i -> "mem_" + (i.getArgument(1).equals("real-owner") ? "o" :
                i.getArgument(1).equals("subject-A") ? "a" : "b").repeat(32));
        when(references.invitation(eq("provider-org"), anyString())).thenAnswer(i -> "inv_" +
                (i.getArgument(1).equals("provider-A") ? "a" : "b").repeat(32));
        when(keycloak.invitationsForEmail(eq("provider-org"), anyString())).thenAnswer(i -> {
            ProviderInvitation invitation = providerInvitations.get(i.getArgument(1));
            return invitation == null ? List.of() : List.of(invitation);
        });
        when(keycloak.issue(eq("provider-org"), anyString(), anyString())).thenAnswer(i -> {
            String email = i.getArgument(1); String name = i.getArgument(2);
            String slot = email.contains("-a@") ? "A" : "B";
            var invitation = new ProviderInvitation("provider-" + slot, email, name, "pending",
                    Instant.now().plusSeconds(86400), Instant.now());
            providerInvitations.put(email, invitation); return invitation;
        });
        var invitationService = new MemberInvitationService(intents, keycloak, properties, references, audit, identities);
        service = new DogfoodPreparationService(keycloak, identities, capabilities,
                new DeploymentOrganizationProperties("provider-org", "weave"), resource, intents, invitationService,
                references, spaces, bindings);
    }

    @Test void createsExactlyTwoNormalInvitationsWithDistinctOperatorAttributionAndPrivateOwnerName() {
        var result = service.execute(request("invitations"));
        assertThat(result.receipt().status()).isEqualTo("passed");
        assertThat(result.receipt().stage()).isEqualTo("invitations-ready");
        assertThat(result.receipt().outcome()).isEqualTo("CREATED");
        assertThat(result.privateData().ownerDisplayName()).isEqualTo("Actual owner");
        assertThat(JSON.writeValueAsString(result.receipt())).doesNotContain("Actual owner", "owner@example.test", "subject-", "real-owner");
        verify(keycloak, times(2)).issue(eq("provider-org"), anyString(), anyString());
        for (int i = 0; i < 2; i++) {
            var receipt = result.receipt().members().get(i);
            ProvisioningIntent intent = intents.findById(UUID.fromString(receipt.intentRef())).orElseThrow();
            assertThat(intent.invitedByIssuer()).isEqualTo("urn:weave:dogfood-operator");
            assertThat(intent.invitedBySubject()).isEqualTo(actor(request("invitations")));
            assertThat(intent.requestedRole()).isEqualTo("member");
            assertThat(receipt.intentState()).isEqualTo("PENDING");
            assertThat(receipt.accountRef()).isNull();
        }
        verify(spaces, never()).grantTwo(any(), any(), any(), any(), any());
        assertNoIdentityWrites();
    }

    @Test void exactPendingIntentResumeDoesNotResendOrCreateAnotherInvitation() {
        var first = service.execute(request("invitations"));
        var second = service.execute(withPrevious(request("invitations"), first.receipt()));
        assertThat(second.receipt().status()).isEqualTo("passed");
        assertThat(second.receipt().outcome()).isEqualTo("VERIFIED");
        assertThat(second.receipt().members()).isEqualTo(first.receipt().members());
        verify(keycloak, times(2)).issue(anyString(), anyString(), anyString());
    }

    @Test void appliedActivatedMembersRemainReusableAfterInvitationExpiry() {
        seedApplied(true);
        var result = service.execute(request("invitations"));
        assertThat(result.receipt().status()).isEqualTo("passed");
        assertThat(result.receipt().members()).allMatch(m -> "APPLIED".equals(m.intentState()) && m.accountRef() != null);
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    }

    @Test void expiredPendingIntentAndRevokedInvitationCannotBeReissued() {
        var first = service.execute(request("invitations"));
        var id = UUID.fromString(first.receipt().members().getFirst().intentRef());
        intents.save(intents.findById(id).orElseThrow().expired(Instant.now()));
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("invitation-expired");
        verify(keycloak, times(2)).issue(anyString(), anyString(), anyString());
    }

    @Test void earlierHumanOrFailedUncorrelatedIntentCannotBeRelabeledAsOperatorAuthority() {
        var planned = request("invitations").plan().members().getFirst();
        Instant now = Instant.now();
        intents.save(new ProvisioningIntent(UUID.randomUUID(), "org", "provider-org", planned.email(), "a".repeat(64),
                "member", null, ISSUER, "actual-admin", planned.invitationIdempotencyKey(), ProvisioningIntentStatus.FAILED,
                null, "provider-uncertain", now.plusSeconds(86400), now, now));
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("invitation-conflict");
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    }

    @Test void currentRevokedProviderInvitationCannotBeReissued() {
        service.execute(request("invitations"));
        var plan = request("invitations").plan().members().getFirst();
        var old = providerInvitations.get(plan.email());
        providerInvitations.put(plan.email(), new ProviderInvitation(old.providerInvitationId(), old.email(), old.displayName(),
                "revoked", old.expiresAt(), old.createdAt()));
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("invitation-revoked");
        verify(keycloak, times(2)).issue(anyString(), anyString(), anyString());
    }

    @Test void unrelatedConfiguredOrganizationMemberBlocksBothInvitations() {
        var planned = request("invitations").plan().members().get(1);
        members.add(new ProviderMember("unrelated", planned.email(), planned.displayName(), List.of("member"), List.of(), true));
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("invitation-conflict");
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    }

    @Test void unactivatedOwnerAndPolicyDenialBlockBeforeAnyInvitation() {
        when(keycloak.memberAuthorization("provider-org", "real-owner")).thenReturn(
                new MemberAuthorization("real-owner", true, false, List.of("owner"), List.of("/owners")));
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("owner-unavailable");
        when(keycloak.memberAuthorization("provider-org", "real-owner")).thenReturn(
                new MemberAuthorization("real-owner", true, true, List.of("owner"), List.of("/owners")));
        doThrow(new ApiErrorException(HttpStatus.FORBIDDEN, "policy-denied", "private", Map.of()))
                .when(capabilities).requireCapabilityForVerifiedMember(any(), anyString(), anyString(), anyString());
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("owner-policy-denied");
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    }

    @Test void ambiguousOwnerAndMissingExistingSpaceBlockWithoutRepair() {
        members.add(new ProviderMember("second-owner", "second@example.test", "Second", List.of("owner"), List.of(), true));
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("owner-unavailable");
        members.removeLast();
        doThrow(new DogfoodSpacePreparationPort.OwnerConflict()).when(spaces).verifyOwner(anyString(), anyString());
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("owner-space-access-conflict");
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    }

    @Test void grantRequiresAppliedIntentActivatedMemberAndOwnApplicationDigests() {
        seedApplied(false);
        var valid = validGrantRequest();
        var badProof = new Request(valid.schemaVersion(), valid.operation(), valid.environment(), valid.nonce(), valid.candidateCommit(),
                valid.sourceTree(), valid.specificationCommit(), valid.apiBaseUrl(), valid.plan(), valid.previousReceipt(), request("grants").proof());
        assertThat(service.execute(badProof).receipt().boundary()).isEqualTo("member-proof-conflict");
        when(keycloak.memberAuthorization("provider-org", "subject-A")).thenReturn(
                new MemberAuthorization("subject-A", true, false, List.of("member"), List.of("/members")));
        assertThat(service.execute(valid).receipt().boundary()).isEqualTo("test-member-not-activated");
        verify(spaces, never()).grantTwo(any(), any(), any(), any(), any());
    }

    @Test void grantRechecksRealIdentityUnderPortLockAndAuditsOperatorNeverOwner() {
        seedApplied(false);
        when(spaces.grantTwo(anyString(), anyString(), anyList(), any(), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(3)).run();
            List<DogfoodSpacePreparationPort.Target> targets = invocation.getArgument(2);
            var grants = targets.stream().map(target -> new DogfoodSpacePreparationPort.Grant(target.accountRef(), "\"sm-0\"", true)).toList();
            java.util.function.Consumer<List<DogfoodSpacePreparationPort.Grant>> auditWrite = invocation.getArgument(4);
            auditWrite.accept(grants); return grants;
        });
        var result = service.execute(validGrantRequest());
        assertThat(result.receipt().status()).isEqualTo("passed");
        assertThat(result.receipt().members()).allMatch(m -> "CREATED".equals(m.grantOutcome()) && "\"sm-0\"".equals(m.grantVersion()));
        verify(keycloak, times(3)).memberAuthorization("provider-org", "subject-A");
        verify(bindings, times(2)).observe(any(), eq("org"), eq(AuditAction.SPACE_MEMBERSHIP_UPDATED),
                startsWith("dogfood-grant-"), anyMap());
        verifyNoInteractions(audit);
        assertNoIdentityWrites();
    }

    @Test void changedOwnerReceiptAndConflictingIntentReferencesAreRejected() {
        var first = service.execute(request("invitations"));
        var json = JSON.valueToTree(first.receipt()).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) json).put("intendedOwnerAccountRef", "acct_" + "0".repeat(32));
        var conflicting = JSON.treeToValue(json, Receipt.class);
        assertThat(service.execute(withPrevious(request("invitations"), conflicting)).receipt().boundary()).isEqualTo("operator-reference-conflict");
        verify(keycloak, times(2)).issue(anyString(), anyString(), anyString());
    }

    @Test void partialProviderFailureRetainsBothDurableIntentsAndCannotCreateOnRetry() {
        when(keycloak.issue(eq("provider-org"), contains("-b@"), anyString())).thenThrow(new KeycloakAdminException(503, "private-provider-body"));
        var result = service.execute(request("invitations"));
        assertThat(result.receipt().status()).isEqualTo("incomplete");
        assertThat(result.receipt().members()).allMatch(m -> m.intentRef() != null);
        assertThat(result.receipt().members().get(1).intentState()).isEqualTo("FAILED");
        assertThat(JSON.writeValueAsString(result)).doesNotContain("private-provider-body", "weave-smoke-");
        assertThat(service.execute(withPrevious(request("invitations"), result.receipt())).receipt().boundary())
                .isEqualTo("invitation-outcome-uncertain");
        verify(keycloak, times(2)).issue(anyString(), anyString(), anyString());
    }

    @Test void auditFailureCannotAuthorizeInvitations() {
        doThrow(new AuditRequiredException("private-db-message")).when(bindings).bind(any(), anyString(), anyString(), anyString());
        assertThat(service.execute(request("invitations")).receipt().boundary()).isEqualTo("audit-unavailable");
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    }

    private void managedProvider() {
        when(keycloak.provisionManagedTestMember(any(), anyString(), any())).thenAnswer(invocation -> {
            ManagedTestMember authority = invocation.getArgument(0);
            String subject = authority.existingSubject() == null ? "subject-" + authority.slot() : authority.existingSubject();
            boolean existed = members.stream().anyMatch(member -> member.subject().equals(subject));
            if (!existed) members.add(new ProviderMember(subject, authority.email(), authority.displayName(), List.of("member"), List.of(), true));
            when(keycloak.memberAuthorization("provider-org", subject)).thenReturn(
                    new MemberAuthorization(subject, true, true, List.of("member"), List.of("/members")));
            return new ManagedTestIdentity(subject, !existed, !existed);
        });
        when(spaces.grantTwo(anyString(), anyString(), anyList(), any(), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(3)).run();
            List<DogfoodSpacePreparationPort.Target> targets = invocation.getArgument(2);
            var grants = targets.stream().map(target -> new DogfoodSpacePreparationPort.Grant(target.accountRef(), "\"sm-0\"", true)).toList();
            java.util.function.Consumer<List<DogfoodSpacePreparationPort.Grant>> publish = invocation.getArgument(4);
            publish.accept(grants); return grants;
        });
    }

    @Test void preparesTwoRealManagedMembersWithoutInvitationMailOrClaimingAuthentication() {
        managedProvider();
        var result = service.execute(managedRequest());
        assertThat(result.receipt().status()).isEqualTo("passed");
        assertThat(result.receipt().stage()).isEqualTo("provision-members-ready");
        assertThat(result.receipt().members()).allMatch(member -> member.accountRef() != null
                && member.grantVersion().equals("\"sm-0\"") && member.invitationHandle() == null);
        assertThat(result.privateData().members()).extracting(PreparedMember::identityRef).containsExactly("subject-A", "subject-B");
        assertThat(JSON.writeValueAsString(result.receipt())).doesNotContain("subject-A", "subject-B", "private-initial", "Actual owner");
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
        verify(keycloak, times(2)).provisionManagedTestMember(any(), anyString(), any());
        verify(bindings).observe(any(), eq("org"), eq(AuditAction.DOGFOOD_PREPARATION_VERIFIED),
                startsWith("dogfood-managed-ready-"), argThat(payload -> Boolean.FALSE.equals(payload.get("authenticationEvidence"))));
    }

    @Test void expiredInitialSetupPreservesExactExistingSubjectsAndIntents() {
        seedApplied(true);
        var initialIntents = intents.findByEmail("org", "provider-org", managedRequest().plan().members().getFirst().email());
        var pending = initialIntents.getFirst();
        intents.save(new ProvisioningIntent(pending.intentId(), pending.tenantId(), pending.organizationId(), pending.invitedEmail(),
                pending.invitedEmailSha256(), pending.requestedRole(), pending.providerInvitationId(), pending.invitedByIssuer(),
                pending.invitedBySubject(), pending.auditCorrelation(), ProvisioningIntentStatus.EXPIRED, null, null,
                pending.expiresAt(), pending.createdAt(), pending.updatedAt()));
        providerInvitations.put(pending.invitedEmail(), new ProviderInvitation(pending.providerInvitationId(), pending.invitedEmail(),
                managedRequest().plan().members().getFirst().displayName(), "expired", pending.expiresAt(), pending.createdAt()));
        managedProvider();
        var result = service.execute(managedRequest());
        assertThat(result.receipt().status()).isEqualTo("passed");
        assertThat(result.receipt().members().getFirst().intentRef()).isEqualTo(initialIntents.getFirst().intentId().toString());
        assertThat(result.privateData().members().getFirst().identityRef()).isEqualTo("subject-A");
        verify(keycloak).provisionManagedTestMember(argThat(authority -> "A".equals(authority.slot())
                && authority.existingSubject() == null && authority.invitationOwned()), anyString(), any());
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
        assertThat(members).hasSize(3);
    }

    @Test void conflictingSecondManagedIdentityBlocksBothBeforeProviderMutation() {
        doThrow(new ManagedTestConflict("managed-test-identity-conflict")).when(keycloak)
                .verifyManagedTestMember(argThat(authority -> "B".equals(authority.slot())));
        var result = service.execute(managedRequest());
        assertThat(result.receipt().boundary()).isEqualTo("managed-test-identity-conflict");
        verify(keycloak, never()).provisionManagedTestMember(any(), anyString(), any());
        verify(keycloak, never()).issue(anyString(), anyString(), anyString());
        verify(spaces, never()).grantTwo(any(), any(), any(), any(), any());
    }

    @Test void revokedOrConflictingSpaceGrantRemainsIncompleteAfterManagedSetup() {
        managedProvider();
        doThrow(new DogfoodSpacePreparationPort.Conflict()).when(spaces).grantTwo(anyString(), anyString(), anyList(), any(), any());
        var result = service.execute(managedRequest());
        assertThat(result.receipt().status()).isEqualTo("incomplete");
        assertThat(result.receipt().boundary()).isEqualTo("space-access-conflict");
        assertThat(result.receipt().members()).allMatch(member -> member.accountRef() != null && member.grantVersion() == null);
        assertThat(result.privateData().members()).isNull();
    }

    private void seedApplied(boolean expired) {
        var request = request("invitations");
        for (var planned : request.plan().members()) {
            String subject = "subject-" + planned.slot();
            members.add(new ProviderMember(subject, planned.email(), planned.displayName(), List.of("member"), List.of(), true));
            when(keycloak.memberAuthorization("provider-org", subject)).thenReturn(
                    new MemberAuthorization(subject, true, true, List.of("member"), List.of("/members")));
            Instant now = Instant.now();
            intents.save(new ProvisioningIntent(UUID.randomUUID(), "org", "provider-org", planned.email(), "a".repeat(64),
                    "member", "provider-" + planned.slot(), "urn:weave:dogfood-operator", actor(request), planned.invitationIdempotencyKey(),
                    ProvisioningIntentStatus.APPLIED, subject, null, now.plusSeconds(expired ? -60 : 86400), now.minusSeconds(3600), now));
        }
    }
    private Request validGrantRequest() {
        Request r = request("grants");
        Proof proof = new Proof(r.proof().schemaVersion(), PLAN, r.nonce(), List.of("A", "B").stream().map(slot -> new MemberProof(slot,
                digest("weave-account", ISSUER, IdentityReferences.accountId(ISSUER, "subject-" + slot)), digest("weave-organization", ISSUER, "org"))).toList());
        return new Request(r.schemaVersion(), r.operation(), r.environment(), r.nonce(), r.candidateCommit(), r.sourceTree(),
                r.specificationCommit(), r.apiBaseUrl(), r.plan(), service.execute(request("invitations")).receipt(), proof);
    }
    private static Request withPrevious(Request r, Receipt previous) {
        return new Request(r.schemaVersion(), r.operation(), r.environment(), r.nonce(), r.candidateCommit(), r.sourceTree(),
                r.specificationCommit(), r.apiBaseUrl(), r.plan(), previous, r.proof());
    }
    private void assertNoIdentityWrites() {
        verify(keycloak, never()).applyRole(anyString(), anyString());
        verify(keycloak, never()).resend(anyString(), anyString());
        verify(keycloak, never()).revoke(anyString(), anyString());
    }
}
