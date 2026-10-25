package com.massimotter.weave.backend.capability;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.config.*;
import com.massimotter.weave.backend.capability.port.*;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;

class WorkspaceCapabilityOwnershipTest {
    @Test
    void absentDomainsAreAbsentAndGrantNothingEvenToAdministrators() {
        var policy = policy(List.of());
        assertThat(policy.installedDefinitions()).isEmpty();
        assertThat(policy.installedSnapshots(jwt("admin"))).isEmpty();
        assertThat(policy.settings("example")).isEmpty();
        assertThat(policy.snapshot(jwt("admin")).chat()).isNull();
        assertThat(policy.grantedCapabilities(jwt("admin"))).contains("admin.policy.edit").doesNotContain("example.read");
        assertThatThrownBy(() -> policy.requireCapability(jwt("admin"), "example.read", "example", "read"))
                .hasMessageContaining("blocked");
        assertThat(policy.releaseReadinessChecks()).isEmpty();
    }
    @Test
    void installedDisabledPolicyDiffersFromAbsentPolicy() {
        var policy = policy(List.of(contribution("example",false)));
        assertThat(policy.installedSnapshots(jwt("member"))).containsOnlyKeys("example");
        assertThat(policy.settings("example")).isPresent();
        assertThat(policy.grantedCapabilities(jwt("member"))).contains("example.read");
        assertThat(policy.installedSnapshots(jwt("member")).get("example").enabled()).isFalse();
        assertThatThrownBy(() -> policy.requireCapability(jwt("member"),"example.read","example","read"))
                .hasMessageContaining("disabled");
    }
    @Test
    void installedReadinessDoesNotCreateAMemberGrant() {
        var policy = policy(List.of(contribution("example",true)));
        assertThat(policy.installedSnapshots(null).get("example").readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(policy.installedSnapshots(jwt("guest")).get("example").readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(policy.grantedCapabilities(jwt("guest"))).doesNotContain("example.read");
        assertThatThrownBy(() -> policy.requireCapability(jwt("guest"),"example.read","example","read"))
                .hasMessageContaining("blocked");
    }
    @Test
    void duplicateDomainOwnershipFailsImmediately() {
        assertThatThrownBy(() -> policy(List.of(contribution("example",true),contribution("example",false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate workspace capability domain");
    }
    @Test
    void absentDomainCannotBeReintroducedByADeviceClaim() {
        var token = Jwt.withTokenValue("device").header("alg","none").subject("device")
                .claim("weave_auth_method","device_credential").claim("weave_capabilities",List.of("example.read")).build();
        assertThatThrownBy(() -> policy(List.of()).requireCapability(token,"example.read","example","read"))
                .hasMessageContaining("disabled");
    }
    @Test
    void aDomainCannotGrantAnotherDomainsOrSharedCapabilities() {
        assertThatThrownBy(() -> new WorkspaceCapabilityDefinition("example", "example",
                new CapabilitySettings(true, null, null), List.of("example.read"),
                List.of("admin.policy.edit"), List.of(), List.of(), Map.of(), null, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("declaring domain");
    }

    private WorkspaceCapabilityService policy(List<WorkspaceCapabilityContribution> contributions) {
        var resource = new OAuth2ResourceServerProperties();
        resource.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return new WorkspaceCapabilityService(resource,new WeaveSecurityProperties("weave-app","weave-app"),
                new WorkspaceCapabilityProperties(null,null),OrganizationIdentityContextResolver.defaults(),contributions);
    }
    private WorkspaceCapabilityContribution contribution(String id,boolean enabled) {
        var settings = new CapabilitySettings(enabled,null,WorkspaceCapabilityReadiness.READY);
        return new WorkspaceCapabilityContribution(new WorkspaceCapabilityDefinition(id,id,settings,List.of("example.read"),
                List.of("example.read"),List.of("example.read"),List.of(),Map.of(),null,List.of(),10),
                shell -> CapabilityAvailability.standalone(settings,WorkspaceCapabilityReadiness.READY,"Example is ready."));
    }
    private Jwt jwt(String role) {
        return Jwt.withTokenValue("member").header("alg","none").issuer("https://auth.weave.test/realms/weave").subject("member")
                .claim("organization",HumanJwtTestSupport.organizationWithRole(role)).build();
    }
}
