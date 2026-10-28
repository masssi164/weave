package com.massimotter.weave.backend.dogfood;

import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class DogfoodPreparationProtocolTest {
    static final String PLAN = "1234567890abcdef1234567890abcdef";
    static final String ISSUER = "https://auth.example.test/realms/weave";
    static Request request(String operation) {
        return new Request("weave.dogfood-operator-preparation-request/v1", operation, "dogfood", "b".repeat(32),
                "c".repeat(40), "d".repeat(40), "e".repeat(40), "https://api.example.test/api",
                new Plan("weave.dogfood-admin-preparation-plan/v1", PLAN, "b".repeat(32), "https://example.test",
                        "https://api.example.test", ISSUER, "workspace-default", List.of(member("A"), member("B"))), "grants".equals(operation) ? priorReceipt() : null,
                "grants".equals(operation) ? new Proof("weave.dogfood-admin-preparation-proof/v1", PLAN, "b".repeat(32), List.of(
                        new MemberProof("A", "a".repeat(64), "c".repeat(64)), new MemberProof("B", "b".repeat(64), "c".repeat(64)))) : null);
    }
    private static Receipt priorReceipt() {
        return new Receipt("weave.dogfood-operator-preparation-receipt/v1", "dogfood-operator", "dogfood-operator",
                "operator:dogfood-prepare:" + PLAN, "invitations", "passed", "invitations-ready", "none", "CREATED",
                "a".repeat(32), PLAN, "dogfood", "c".repeat(40), "d".repeat(40), "e".repeat(40),
                "https://example.test", "https://api.example.test/api", ISSUER, "org", "workspace-default",
                "acct_" + "0".repeat(32), "mem_" + "o".repeat(32), List.of(
                new MemberReceipt("A", "11111111-1111-1111-1111-111111111111", "inv_" + "a".repeat(32), null, null, null, "PENDING", "NOT_REQUESTED"),
                new MemberReceipt("B", "22222222-2222-2222-2222-222222222222", "inv_" + "b".repeat(32), null, null, null, "PENDING", "NOT_REQUESTED")), true, false);
    }
    private static PlannedMember member(String slot) {
        return new PlannedMember(slot, "weave-smoke-" + PLAN + "-" + slot.toLowerCase() + "@example.test",
                "Weave test member " + slot + " 12345678", "member", "EDIT", "dogfood-" + PLAN + "-" + slot);
    }
    private static Request parse(String value) throws Exception {
        return read(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
    static Request managedRequest() {
        var original = request("invitations");
        return new Request("weave.dogfood-operator-preparation-request/v2", "provision-members", original.environment(),
                original.nonce(), original.candidateCommit(), original.sourceTree(), original.specificationCommit(),
                original.apiBaseUrl(), original.plan(), null, null, List.of(
                    new InitialCredential("A", "private-initial-member-A-credential"),
                    new InitialCredential("B", "private-initial-member-B-credential")));
    }
    static Request verificationRequest(Receipt previous) {
        var r = request("invitations");
        return new Request("weave.dogfood-operator-preparation-request/v3", "verify-members", r.environment(),
                r.nonce(), r.candidateCommit(), r.sourceTree(), r.specificationCommit(), r.apiBaseUrl(), r.plan(), previous, null);
    }

    @Test void readOnlyVerificationRequiresCompleteManagedAuthorityAndCannotAcceptCredentials() throws Exception {
        var node = (tools.jackson.databind.node.ObjectNode) JSON.valueToTree(priorReceipt());
        node.put("operation", "provision-members").put("stage", "provision-members-ready");
        for (int i = 0; i < 2; i++) {
            var member = (tools.jackson.databind.node.ObjectNode) node.path("members").get(i);
            member.putNull("invitationHandle").put("intentState", "APPLIED").put("grantOutcome", "VERIFIED")
                    .put("memberHandle", "mem_" + (i == 0 ? "a" : "b").repeat(32))
                    .put("accountRef", "acct_" + (i == 0 ? "a" : "b").repeat(32)).put("grantVersion", "\"sm-3\"");
        }
        var request = verificationRequest(JSON.treeToValue(node, Receipt.class));
        String json = JSON.writeValueAsString(request);
        assertThat(parse(json)).isEqualTo(request);
        assertThat(json).doesNotContain("initialCredentials");
        for (String invalid : List.of(JSON.writeValueAsString(verificationRequest(null)),
                JSON.writeValueAsString(verificationRequest(priorReceipt())), json.replace("/v3", "/v1"),
                json.replaceFirst("\\{", "{\"initialCredentials\":null,"),
                json.replace("\\\"sm-3\\\"", "\\\"stale\\\""))) {
            assertThatThrownBy(() -> parse(invalid)).isInstanceOf(Exception.class);
        }
    }
    @Test void onlyExplicitV2ManagedOperationAdmitsTwoDistinctPrivateInitialCredentials() throws Exception {
        var request = managedRequest();
        assertThat(parse(JSON.writeValueAsString(request))).isEqualTo(request);
        assertThat(request.initialCredentials().toString()).doesNotContain("private-initial");
        String json = JSON.writeValueAsString(request);
        for (String invalid : List.of(json.replace("/v2", "/v1"), json.replace("provision-members", "invitations"),
                json.replace("private-initial-member-B-credential", "private-initial-member-A-credential"),
                json.replace("private-initial-member-A-credential", "short"))) {
            assertThatThrownBy(() -> parse(invalid)).isInstanceOf(Exception.class);
        }
    }

    @Test void exactClosedRequestRoundTrips() throws Exception {
        assertThat(parse(JSON.writeValueAsString(request("invitations")))).isEqualTo(request("invitations"));
        assertThat(parse(JSON.writeValueAsString(request("grants")))).isEqualTo(request("grants"));
    }
    @Test void grantsRequiresPassedCorrectPhaseReceiptWhileInvitationResumeAllowsPartialReceipt() {
        var valid = JSON.valueToTree(request("grants"));
        for (String field : List.of("status", "stage", "operation")) {
            var wrong = valid.deepCopy();
            ((tools.jackson.databind.node.ObjectNode) wrong.path("previousReceipt")).put(field,
                    "status".equals(field) ? "incomplete" : "stage".equals(field) ? "incomplete" : "grants");
            assertThatThrownBy(() -> parse(JSON.writeValueAsString(wrong))).isInstanceOf(Exception.class);
        }
        var absent = valid.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) absent).putNull("previousReceipt");
        assertThatThrownBy(() -> parse(JSON.writeValueAsString(absent))).isInstanceOf(Exception.class);
        var partial = valid.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) partial).put("operation", "invitations").putNull("proof");
        ((tools.jackson.databind.node.ObjectNode) partial.path("previousReceipt")).put("status", "incomplete").put("stage", "incomplete")
                .put("boundary", "invitation-outcome-uncertain").put("outcome", "PARTIAL");
        assertThatCode(() -> parse(JSON.writeValueAsString(partial))).doesNotThrowAnyException();
    }
    @Test void passedGrantReceiptCannotDropAppliedIdentityOrStrongVersionFence() {
        var malformed = JSON.valueToTree(request("grants"));
        var receipt = (tools.jackson.databind.node.ObjectNode) malformed.path("previousReceipt");
        receipt.put("operation", "grants").put("stage", "grants-ready");
        assertThatThrownBy(() -> parse(JSON.writeValueAsString(malformed))).isInstanceOf(Exception.class);
        for (int i = 0; i < 2; i++) {
            var member = (tools.jackson.databind.node.ObjectNode) receipt.path("members").get(i);
            member.put("intentState", "APPLIED").put("grantOutcome", "VERIFIED")
                    .put("memberHandle", "mem_" + (i == 0 ? "a" : "b").repeat(32))
                    .put("accountRef", "acct_" + (i == 0 ? "a" : "b").repeat(32));
        }
        assertThatThrownBy(() -> parse(JSON.writeValueAsString(malformed))).isInstanceOf(Exception.class);
        for (var member : receipt.path("members")) ((tools.jackson.databind.node.ObjectNode) member).put("grantVersion", "\"sm-0\"");
        assertThatCode(() -> parse(JSON.writeValueAsString(malformed))).doesNotThrowAnyException();
        for (String field : List.of("intentState", "grantOutcome", "grantVersion", "memberHandle", "accountRef")) {
            var missing = malformed.deepCopy();
            var member = (tools.jackson.databind.node.ObjectNode) missing.path("previousReceipt").path("members").get(0);
            if ("intentState".equals(field)) member.put(field, "PENDING");
            else if ("grantOutcome".equals(field)) member.put(field, "NOT_REQUESTED");
            else member.putNull(field);
            assertThatThrownBy(() -> parse(JSON.writeValueAsString(missing))).isInstanceOf(Exception.class);
        }
        receipt.put("outcome", "PARTIAL");
        assertThatThrownBy(() -> parse(JSON.writeValueAsString(malformed))).isInstanceOf(Exception.class);
    }
    @Test void rejectsDuplicateUnknownMissingTrailingAndOversizedInput() {
        String json = JSON.writeValueAsString(request("invitations"));
        for (String malformed : List.of(json.replaceFirst("\\{", "{\"operation\":\"grants\","),
                json.replaceFirst("\\{", "{\"password\":\"never\","), json + " {}", json.replace(",\"proof\":null", ""),
                json.replace("\"role\":\"member\"", "\"role\":\"owner\""), " ".repeat(65_537))) {
            assertThatThrownBy(() -> parse(malformed)).isInstanceOf(Exception.class);
        }
    }
    @Test void rejectsArbitraryAddressNameKeyAndTopology() {
        String json = JSON.writeValueAsString(request("invitations"));
        for (String malformed : List.of(json.replace("weave-smoke-", "ordinary-"), json.replace("Weave test member A", "Other member"),
                json.replace("dogfood-" + PLAN + "-A", "arbitrary-key"), json.replace("https://example.test", "http://example.test"),
                json.replace("workspace-default", "other-space"))) {
            assertThatThrownBy(() -> parse(malformed)).isInstanceOf(Exception.class);
        }
    }
    static MockEnvironment environment(Request r) {
        var environment = new MockEnvironment().withProperty("WEAVE_RELEASE_POSTURE", "dogfood")
                .withProperty("weave.candidate.commit", r.candidateCommit())
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate").withProperty("spring.flyway.enabled", "false")
                .withProperty("weave.platform.public-base-url", r.plan().organizationOrigin())
                .withProperty("weave.platform.api-base-url", r.apiBaseUrl())
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", r.plan().issuerUrl())
                .withProperty("weave.security.primary-organization.keycloak-id", "provider-org")
                .withProperty("weave.identity.invitations.keycloak.organization-id", "provider-org")
                .withProperty("weave.context.authorization.default-tenant-id", "org");
        environment.getPropertySources().addLast(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "systemEnvironment", java.util.Map.of("WEAVE_RELEASE_POSTURE", "dogfood", "WEAVE_CANDIDATE_COMMIT", r.candidateCommit())));
        return environment;
    }
    @Test void preRefreshRequiresExplicitDogfoodCandidateExactConfigAndValidationOnly() {
        var r = request("invitations");
        DogfoodPreparationPreflight.validate(environment(r), r);
        for (var change : List.of(new String[]{"WEAVE_RELEASE_POSTURE", "stable"}, new String[]{"WEAVE_RELEASE_POSTURE", ""},
                new String[]{"weave.candidate.commit", "0".repeat(40)}, new String[]{"spring.flyway.enabled", "true"},
                new String[]{"spring.jpa.hibernate.ddl-auto", "update"}, new String[]{"spring.jpa.generate-ddl", "true"},
                new String[]{"spring.sql.init.mode", "always"},
                new String[]{"spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "create"},
                new String[]{"spring.jpa.properties.hibernate.hbm2ddl.auto", "create-drop"},
                new String[]{"spring.main.sources", "com.massimotter.weave.backend.WeaveBackendApplication"},
                new String[]{"spring.main.web-application-type", "servlet"},
                new String[]{"weave.platform.api-base-url", "https://foreign.test/api"},
                new String[]{"weave.identity.invitations.keycloak.organization-id", "foreign"})) {
            var configured = environment(r).withProperty(change[0], change[1]);
            if ("WEAVE_RELEASE_POSTURE".equals(change[0])) configured.getPropertySources().replace("systemEnvironment",
                    new org.springframework.core.env.SystemEnvironmentPropertySource("systemEnvironment", java.util.Map.of(
                            "WEAVE_RELEASE_POSTURE", change[1], "WEAVE_CANDIDATE_COMMIT", r.candidateCommit())));
            assertThatThrownBy(() -> DogfoodPreparationPreflight.validate(configured, r)).isInstanceOf(Blocked.class);
        }
    }
    @Test void yamlSystemPropertiesOrDefaultsCannotSupplyExplicitInheritedDogfoodAuthority() {
        var r = request("invitations");
        var env = environment(r);
        env.getPropertySources().remove("systemEnvironment");
        assertThatThrownBy(() -> DogfoodPreparationPreflight.validate(env, r)).isInstanceOfSatisfying(Blocked.class,
                failure -> assertThat(failure.boundary).isEqualTo("dogfood-posture-required"));
        env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("systemEnvironment", java.util.Map.of(
                "WEAVE_RELEASE_POSTURE", "dogfood", "WEAVE_CANDIDATE_COMMIT", r.candidateCommit())));
        assertThatThrownBy(() -> DogfoodPreparationPreflight.validate(env, r)).isInstanceOf(Blocked.class);
    }
    @Test void nullPrimitiveReceiptFlagsAreRejected() {
        var tree = JSON.valueToTree(request("grants"));
        ((tools.jackson.databind.node.ObjectNode) tree.path("previousReceipt")).putNull("containsSecretValues");
        assertThatThrownBy(() -> parse(JSON.writeValueAsString(tree))).isInstanceOf(Exception.class);
    }
    @Test void unsafeDdlIsRejectedByInitializerBeforeAnyApplicationBeanCanRun() {
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        new ApplicationContextRunner().withInitializer(context -> {
            context.getEnvironment().getPropertySources().addLast(new org.springframework.core.env.SystemEnvironmentPropertySource(
                    "systemEnvironment", java.util.Map.of("WEAVE_RELEASE_POSTURE", "dogfood", "WEAVE_CANDIDATE_COMMIT", "c".repeat(40))));
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "reviewed-test", java.util.Map.of("WEAVE_RELEASE_POSTURE", "dogfood", "weave.candidate.commit", "c".repeat(40),
                            "spring.jpa.hibernate.ddl-auto", "create", "spring.flyway.enabled", "false")));
            new DogfoodPreparationPreflight(request("invitations")).initialize(context);
        }).withBean("forbiddenSchemaInitializer", Object.class, () -> { called.set(true); return new Object(); }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isInstanceOf(Blocked.class);
            assertThat(called).isFalse();
        });
    }
    @Test void ordinaryContextDoesNotLoadOperatorGraphOrAnyOperationBean() {
        new ApplicationContextRunner().withUserConfiguration(DogfoodPreparationConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DogfoodPreparationService.class)
                    .doesNotHaveBean(DogfoodOperatorPlanBinding.class).doesNotHaveBean("provisioningIntents");
            assertThat(context.getBeanDefinitionCount()).isLessThan(15);
        });
    }
    @Test void ordinaryWebContextCannotActivateOperatorGraphEvenWithAccidentalOperatorProfile() {
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dogfood-operator-cli"))
                .withUserConfiguration(DogfoodPreparationConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(DogfoodPreparationService.class)
                            .doesNotHaveBean(DogfoodOperatorPlanBinding.class).doesNotHaveBean("provisioningIntents");
                });
    }
    @Test void invalidInputProducesOnlyRedactedTypedFailureAndNonzeroExit() {
        var output = new java.io.ByteArrayOutputStream();
        int code = DogfoodPreparationCli.run(new String[0], new ByteArrayInputStream("{\"password\":\"private-value\"}".getBytes(StandardCharsets.UTF_8)),
                new java.io.PrintStream(output), new java.util.concurrent.atomic.AtomicReference<>());
        assertThat(code).isNotZero();
        String result = output.toString(StandardCharsets.UTF_8);
        assertThat(result).startsWith(MARKER).contains("\"boundary\":\"invalid-request\"")
                .doesNotContain("private-value", "password", "Exception");
        assertThat(JSON.readTree(result.substring(MARKER.length())).path("receipt").path("members").isEmpty()).isTrue();
    }
    @Test void hardDeadlineTerminatesEvenWhenOutputIsBlockedAndDoesNotPublishTwice() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var terminated = new java.util.concurrent.CountDownLatch(1);
        var blocked = new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int value) {
                entered.countDown();
                try { release.await(2, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        var claimed = new java.util.concurrent.atomic.AtomicBoolean();
        DogfoodPreparationCli.deadline(100, blocked, new java.util.concurrent.atomic.AtomicReference<>(request("invitations")),
                claimed, terminated::countDown);
        try {
            assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(terminated.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(claimed).isTrue();
        } finally { release.countDown(); }
        var output = new java.io.ByteArrayOutputStream();
        var stopped = new java.util.concurrent.CountDownLatch(1);
        DogfoodPreparationCli.deadline(20, new java.io.PrintStream(output), new java.util.concurrent.atomic.AtomicReference<>(),
                new java.util.concurrent.atomic.AtomicBoolean(true), stopped::countDown);
        assertThat(stopped.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(output.size()).isZero();
    }
    @Test void digestUsesExactExistingIssuerBoundCompactArray() throws Exception {
        String input = "[\"weave-account\",\"" + ISSUER + "\",\"acct_" + "a".repeat(32) + "\"]";
        assertThat(digest("weave-account", ISSUER, "acct_" + "a".repeat(32))).isEqualTo(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))));
    }
}
