package com.massimotter.weave.backend.identity.invitation;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class KeycloakManagedTestMemberTest {
  private static final String ROOT = "https://identity.internal/admin/realms/weave";
  private static final String PLAN = "1234567890abcdef1234567890abcdef";
  private static final String INTENT = "11111111-1111-1111-1111-111111111111";
  private static final String PASSWORD = "private-test-initial-password-never-reset";
  private final ObjectMapper json = new ObjectMapper();

  private final class Provider {
    final AtomicReference<ObjectNode> user = new AtomicReference<>();
    final AtomicBoolean password = new AtomicBoolean();
    final AtomicBoolean member = new AtomicBoolean(true);
    final AtomicBoolean role = new AtomicBoolean(true);
    final AtomicInteger passwordWrites = new AtomicInteger();
    final AtomicInteger userCreates = new AtomicInteger();
    final AtomicInteger userUpdates = new AtomicInteger();
    final MockRestServiceServer http;
    final KeycloakIdentityAdminClient adapter;

    Provider() {
      IdentityInvitationProperties properties = new IdentityInvitationProperties();
      properties.keycloak().setBaseUrl(URI.create("https://identity.internal"));
      properties.keycloak().setRealm("weave");
      properties.keycloak().setOrganizationId("org");
      RestClient.Builder builder = RestClient.builder().baseUrl(properties.keycloak().baseUrl());
      http = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
      adapter = new KeycloakIdentityAdminClient(properties, json, builder.build());
      user.set((ObjectNode) json.readTree("""
          {"id":"subject-A","username":"weave-smoke-%s-a@example.test","email":"weave-smoke-%s-a@example.test",
           "firstName":"Weave","lastName":"test member A 12345678","enabled":true,"emailVerified":true,
           "requiredActions":[],"createdTimestamp":1735689600000}
          """.formatted(PLAN, PLAN)));
      http.expect(ExpectedCount.between(0, 20), requestTo(ROOT + "/users?email=weave-smoke-" + PLAN + "-a@example.test&exact=true&first=0&max=2"))
          .andExpect(method(HttpMethod.GET)).andRespond(request -> withSuccess(user.get() == null ? "[]" : "[" + user.get() + "]", MediaType.APPLICATION_JSON).createResponse(request));
      http.expect(ExpectedCount.between(0, 20), requestTo(ROOT + "/users/subject-A"))
          .andExpect(method(HttpMethod.GET)).andRespond(request -> withSuccess(user.get().toString(), MediaType.APPLICATION_JSON).createResponse(request));
      http.expect(ExpectedCount.between(0, 20), requestTo(ROOT + "/organizations/org/members/subject-A"))
          .andExpect(method(HttpMethod.GET)).andRespond(request -> member.get()
              ? withSuccess("{\"id\":\"subject-A\"}", MediaType.APPLICATION_JSON).createResponse(request)
              : withStatus(HttpStatus.NOT_FOUND).createResponse(request));
      http.expect(ExpectedCount.between(0, 20), requestTo(ROOT + "/organizations/org/members/subject-A/groups?briefRepresentation=true"))
          .andExpect(method(HttpMethod.GET)).andRespond(request -> withSuccess(role.get() ? "[{\"path\":\"/members\"}]" : "[]", MediaType.APPLICATION_JSON).createResponse(request));
      http.expect(ExpectedCount.between(0, 20), requestTo(ROOT + "/users/subject-A/credentials"))
          .andExpect(method(HttpMethod.GET)).andRespond(request -> withSuccess(password.get() ? "[{\"id\":\"existing-password\",\"type\":\"password\"}]" : "[]", MediaType.APPLICATION_JSON).createResponse(request));
      http.expect(ExpectedCount.between(0, 3), requestTo(ROOT + "/users/subject-A"))
          .andExpect(method(HttpMethod.PUT)).andRespond(request -> {
            var update = (ObjectNode) json.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertThat(update.has("enabled")).isFalse();
            assertThat(update.has("email")).isFalse();
            assertThat(update.has("attributes")).as("No undeclared provider attributes may be required").isFalse();
            update.properties().forEach(entry -> user.get().set(entry.getKey(), entry.getValue()));
            userUpdates.incrementAndGet();
            return withStatus(HttpStatus.NO_CONTENT).createResponse(request);
          });
      http.expect(ExpectedCount.between(0, 1), requestTo(ROOT + "/users/subject-A/reset-password"))
          .andExpect(method(HttpMethod.PUT)).andRespond(request -> {
            assertThat(password.get()).as("An accepted credential may never be reset").isFalse();
            var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertThat(body.path("value").asString()).isEqualTo(PASSWORD);
            assertThat(body.path("temporary").asBoolean()).isFalse();
            password.set(true); passwordWrites.incrementAndGet();
            return withStatus(HttpStatus.NO_CONTENT).createResponse(request);
          });
      http.expect(ExpectedCount.between(0, 1), requestTo(ROOT + "/users"))
          .andExpect(method(HttpMethod.POST)).andRespond(request -> {
            assertThat(user.get()).isNull();
            user.set((ObjectNode) json.readTree(((MockClientHttpRequest) request).getBodyAsString()));
            assertThat(user.get().has("attributes")).as("Strict user profiles ignore undeclared attributes").isFalse();
            user.get().put("id", "subject-A").put("createdTimestamp", 1735689600000L);
            userCreates.incrementAndGet();
            return withStatus(HttpStatus.CREATED).createResponse(request);
          });
      http.expect(ExpectedCount.between(0, 1), requestTo(ROOT + "/organizations/org/members"))
          .andExpect(method(HttpMethod.POST)).andExpect(content().string("\"subject-A\""))
          .andRespond(request -> { member.set(true); return withStatus(HttpStatus.CREATED).createResponse(request); });
      http.expect(ExpectedCount.between(0, 1), requestTo(ROOT + "/organizations/org/groups?search=members&exact=true"))
          .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("[{\"id\":\"member-group\",\"path\":\"/members\"}]", MediaType.APPLICATION_JSON));
      http.expect(ExpectedCount.between(0, 1), requestTo(ROOT + "/organizations/org/groups/member-group/members/subject-A"))
          .andExpect(method(HttpMethod.PUT)).andRespond(request -> { role.set(true); return withStatus(HttpStatus.NO_CONTENT).createResponse(request); });
    }
  }

  private KeycloakIdentityAdminClient.ManagedTestMember authority(String subject, boolean invitation) {
    return new KeycloakIdentityAdminClient.ManagedTestMember("org", PLAN, "A", "example.test", INTENT,
        subject, invitation, Instant.parse("2024-12-31T00:00:00Z"), "https://auth.example.test/realms/weave",
        KeycloakIdentityAdminClient.ManagedTestProgress.empty());
  }

  @Test void existingAcceptedCredentialIsReusedWithoutCredentialOrUserWrites() {
    var provider = new Provider(); provider.password.set(true);
    var result = provider.adapter.provisionManagedTestMember(authority("subject-A", true), PASSWORD, step -> {});
    assertThat(result.subject()).isEqualTo("subject-A");
    assertThat(result.initialCredentialCreated()).isFalse();
    assertThat(provider.passwordWrites).hasValue(0);
    assertThat(provider.userUpdates).hasValue(0);
    assertThat(provider.userCreates).hasValue(0);
    provider.http.verify();
  }

  @Test void correlatedExpiredInitialSetupCompletesTheSameIdentityAndResumesWithoutReset() {
    var provider = new Provider();
    provider.user.get().putArray("requiredActions").add("UPDATE_PASSWORD");
    var result = provider.adapter.provisionManagedTestMember(authority(null, true), PASSWORD, step -> {});
    assertThat(result.subject()).isEqualTo("subject-A");
    assertThat(result.created()).isFalse();
    assertThat(result.initialCredentialCreated()).isTrue();
    assertThat(provider.passwordWrites).hasValue(1);
    assertThat(provider.userCreates).hasValue(0);
    provider.adapter.provisionManagedTestMember(authority("subject-A", true), PASSWORD, step -> {});
    assertThat(provider.passwordWrites).hasValue(1);
    provider.http.verify();
  }

  @Test void activatedInvitationCannotRecreateAnAbsentPasswordBeforeAppliedReconciliation() {
    var provider = new Provider();
    var pending = authority(null, true);
    var auditWrites = new AtomicInteger();
    assertThatThrownBy(() -> provider.adapter.verifyManagedTestMember(pending))
        .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-credential-conflict");
    assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(pending, PASSWORD, step -> auditWrites.incrementAndGet()))
        .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-credential-conflict");
    assertThat(provider.passwordWrites).hasValue(0);
    assertThat(provider.userUpdates).hasValue(0);
    assertThat(provider.userCreates).hasValue(0);
    assertThat(auditWrites).hasValue(0);
    provider.http.verify();
  }

  @Test void activatedInvitationCannotRestoreRemovedMemberRoleBeforeAppliedReconciliation() {
    var provider = new Provider(); provider.password.set(true); provider.role.set(false);
    var pending = authority(null, true);
    var auditWrites = new AtomicInteger();
    assertThatThrownBy(() -> provider.adapter.verifyManagedTestMember(pending))
        .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-identity-conflict");
    assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(pending, PASSWORD, step -> auditWrites.incrementAndGet()))
        .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-identity-conflict");
    assertThat(provider.role).isFalse();
    assertThat(provider.passwordWrites).hasValue(0);
    assertThat(provider.userUpdates).hasValue(0);
    assertThat(provider.userCreates).hasValue(0);
    assertThat(auditWrites).hasValue(0);
    provider.http.verify();
  }

  @Test void activatedInvitationWithCurrentMemberRolePreservesItsAcceptedCredential() {
    var provider = new Provider(); provider.password.set(true);
    var result = provider.adapter.provisionManagedTestMember(authority(null, true), PASSWORD, step -> {});
    assertThat(result.subject()).isEqualTo("subject-A");
    assertThat(result.initialCredentialCreated()).isFalse();
    assertThat(provider.passwordWrites).hasValue(0);
    assertThat(provider.userUpdates).hasValue(0);
    assertThat(provider.userCreates).hasValue(0);
    provider.http.verify();
  }

  @Test void newManagedIdentityWorksWithStrictUserProfileWithoutCustomAttributes() {
    var provider = new Provider(); provider.user.set(null); provider.member.set(false); provider.role.set(false);
    var result = provider.adapter.provisionManagedTestMember(authority(null, false), PASSWORD, step -> {});
    assertThat(result.created()).isTrue();
    assertThat(provider.userCreates).hasValue(1);
    assertThat(provider.passwordWrites).hasValue(1);
    assertThat(provider.member).isTrue();
    assertThat(provider.role).isTrue();
    provider.http.verify();
  }

  @Test void matchingAddressWithoutServerIdentityOwnershipCannotBeAdopted() {
    var provider = new Provider();
    assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(authority(null, false), PASSWORD, step -> {}))
        .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-identity-conflict");
    assertThat(provider.userCreates).hasValue(0);
    assertThat(provider.userUpdates).hasValue(0);
    assertThat(provider.passwordWrites).hasValue(0);
    provider.http.verify();
  }

  @Test void retainedManagedTagsCannotRestoreRevokedMembershipOrRemovedRole() {
    for (boolean organizationRevoked : new boolean[]{true, false}) {
      var provider = new Provider(); provider.password.set(true);
      var attributes = provider.user.get().putObject("attributes");
      attributes.putArray("weave.dogfood.plan").add(PLAN);
      attributes.putArray("weave.dogfood.slot").add("A");
      attributes.putArray("weave.dogfood.intent").add(INTENT);
      if (organizationRevoked) provider.member.set(false); else provider.role.set(false);
      assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(authority("subject-A", false), PASSWORD, step -> {}))
          .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-identity-conflict");
      assertThat(provider.member.get()).isEqualTo(!organizationRevoked);
      assertThat(provider.role.get()).isEqualTo(organizationRevoked);
      assertThat(provider.userUpdates).hasValue(0);
      assertThat(provider.passwordWrites).hasValue(0);
      provider.http.verify();
    }
  }

  @Test void completedManagedFixtureCannotRepairDeletedPasswordOrRequiredReset() {
    for (boolean deleted : new boolean[]{true, false}) {
      var provider = new Provider(); provider.password.set(!deleted);
      var attributes = provider.user.get().putObject("attributes");
      attributes.putArray("weave.dogfood.plan").add(PLAN);
      attributes.putArray("weave.dogfood.slot").add("A");
      attributes.putArray("weave.dogfood.intent").add(INTENT);
      if (!deleted) provider.user.get().putArray("requiredActions").add("UPDATE_PASSWORD");
      assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(authority("subject-A", false), PASSWORD, step -> {}))
          .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-credential-conflict");
      assertThat(provider.userUpdates).hasValue(0);
      assertThat(provider.passwordWrites).hasValue(0);
      provider.http.verify();
    }
  }

  @Test void durableInitialProgressCannotUndoLaterAccessOrCredentialRevocation() {
    for (String revoked : Set.of("membership", "role", "credential", "activation")) {
      var provider = new Provider(); provider.password.set(true);
      if (revoked.equals("membership")) provider.member.set(false);
      if (revoked.equals("role")) provider.role.set(false);
      if (revoked.equals("credential")) provider.password.set(false);
      if (revoked.equals("activation")) provider.user.get().putArray("requiredActions").add("UPDATE_PASSWORD");
      var initial = authority(null, false);
      var progress = new KeycloakIdentityAdminClient.ManagedTestProgress(initial.account("subject-A"), KeycloakIdentityAdminClient.MANAGED_TEST_STEPS);
      var resumed = new KeycloakIdentityAdminClient.ManagedTestMember(initial.organizationId(), initial.planId(), initial.slot(),
          initial.organizationHost(), initial.intentRef(), null, false, initial.intentCreatedAt(), initial.issuer(), progress);
      assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(resumed, PASSWORD, step -> {}))
          .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class);
      assertThat(provider.userUpdates).hasValue(0);
      assertThat(provider.passwordWrites).hasValue(0);
      provider.http.verify();
    }
  }

  @Test void missingDurableSubjectAuditBlocksInitialCredentials() {
    var provider = new Provider();
    provider.user.get().putArray("requiredActions").add("UPDATE_PASSWORD");
    assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(authority(null, true), PASSWORD,
        step -> { throw new IllegalStateException("audit-unavailable"); })).hasMessage("audit-unavailable");
    assertThat(provider.userUpdates).hasValue(0);
    assertThat(provider.passwordWrites).hasValue(0);
    provider.http.verify();
  }

  @Test void acceptedHumanCredentialAndMfaActionsCannotBeRepairedAsInitialSetup() {
    for (String action : new String[]{"UPDATE_PASSWORD", "CONFIGURE_TOTP"}) {
      var provider = new Provider(); provider.password.set(true);
      provider.user.get().putArray("requiredActions").add(action);
      assertThatThrownBy(() -> provider.adapter.provisionManagedTestMember(authority("subject-A", true), PASSWORD, step -> {}))
          .isInstanceOf(KeycloakIdentityAdminClient.ManagedTestConflict.class).hasMessage("managed-test-credential-conflict");
      assertThat(provider.userUpdates).hasValue(0);
      assertThat(provider.passwordWrites).hasValue(0);
      provider.http.verify();
    }
  }
}
