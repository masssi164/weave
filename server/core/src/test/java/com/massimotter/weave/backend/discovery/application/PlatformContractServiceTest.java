package com.massimotter.weave.backend.discovery.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.discovery.port.MatrixPlatformContribution;
import com.massimotter.weave.backend.discovery.port.PlatformDiagnosticContribution;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformContractServiceTest {
    @Test
    void omittedChatKeepsRequiredProtocolsAndUsableConfigAndStatus() {
        var service = service(List.of(), List.of());
        assertThat(service.config().protocols()).isNotNull();
        assertThat(service.config().protocols().matrixClientServerBaseUrl()).isNull();
        var status = service.status("absent");
        assertThat(status.matrix()).isNull();
        assertThat(status.checks()).extracting(PlatformStatusResponse.DiagnosticCheck::key)
                .containsExactly("backend", "auth");
    }

    @Test
    void omittedDiagnosticsRemainAbsentAndChecksRetainStableOrdering() {
        var matrix = mock(MatrixPlatformContribution.class);
        when(matrix.status(any())).thenReturn(new PlatformStatusResponse.MatrixStatus(
                "up", "ready", "Matrix ready", null, false, false, null, null));
        var later = diagnostic("later", 5);
        var earlier = diagnostic("earlier", 3);
        var status = service(List.of(matrix), List.of(later, earlier)).status("installed");
        assertThat(status.files()).isNull();
        assertThat(status.calendar()).isNull();
        assertThat(status.nextcloud()).isNull();
        assertThat(status.checks()).extracting(PlatformStatusResponse.DiagnosticCheck::key)
                .containsExactly("backend", "auth", "matrix", "earlier", "later");
        assertThat(status.actions()).containsExactly("Review dependency");
    }

    @Test
    void duplicateAndReservedContributionIdentitiesAreRejected() {
        var matrix = mock(MatrixPlatformContribution.class);
        assertThatThrownBy(() -> service(List.of(matrix, matrix), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate Matrix");
        assertThatThrownBy(() -> service(List.of(), List.of(diagnostic("files", 3), diagnostic("files", 4))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> service(List.of(), List.of(diagnostic("auth", 3))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }

    @Test
    void unsafePublicOriginIsRejectedWithoutLeakingInput() {
        var issuer = new OAuth2ResourceServerProperties();
        issuer.getJwt().setIssuerUri("https://auth.example/realms/weave");
        var service = new PlatformContractService(issuer,
                new PlatformContractProperties("https://user:secret@example", null, null, null, null, null, null, null),
                new WeaveSecurityProperties("weave-app", "weave-app"), new WorkspaceCapabilityProperties(null, null),
                mock(WorkspaceCapabilityService.class), List.of(), List.of());
        assertThatThrownBy(service::config).isInstanceOf(ApiErrorException.class)
                .hasMessageNotContaining("secret");
    }

    private PlatformContractService service(
            List<MatrixPlatformContribution> matrix, List<PlatformDiagnosticContribution> diagnostics) {
        var issuer = new OAuth2ResourceServerProperties();
        issuer.getJwt().setIssuerUri("https://auth.example/realms/weave");
        return new PlatformContractService(issuer,
                new PlatformContractProperties(null, null, null, null, null, null, null, null),
                new WeaveSecurityProperties("weave-app", "weave-app"), new WorkspaceCapabilityProperties(null, null),
                mock(WorkspaceCapabilityService.class), matrix, diagnostics);
    }

    private PlatformDiagnosticContribution diagnostic(String id, int order) {
        var contribution = mock(PlatformDiagnosticContribution.class);
        when(contribution.id()).thenReturn(id);
        when(contribution.order()).thenReturn(order);
        when(contribution.label()).thenReturn(id);
        when(contribution.status(any())).thenReturn(new PlatformStatusResponse.DiagnosticStatus(
                "degraded", "degraded", "Dependency not ready", "Review dependency"));
        return contribution;
    }
}
