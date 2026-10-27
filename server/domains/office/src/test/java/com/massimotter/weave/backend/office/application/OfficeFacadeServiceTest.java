package com.massimotter.weave.backend.office.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.office.api.OfficeLaunchRequest;
import com.massimotter.weave.backend.office.port.OfficeProvider;
import com.massimotter.weave.backend.office.provider.DisabledOfficeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OfficeFacadeServiceTest {
    @Test
    void deniedDocumentCapabilityPreventsProviderLaunch() {
        var policy = mock(WorkspaceCapabilityService.class);
        var provider = mock(OfficeProvider.class);
        doThrow(new ApiErrorException(HttpStatus.FORBIDDEN, "denied", "Denied", java.util.Map.of()))
                .when(policy).requireCapability(null, "documents.edit", "office", "launch");
        var service = new OfficeFacadeService(provider, policy);
        assertThatThrownBy(() -> service.launch(null, new OfficeLaunchRequest("document", "edit")))
                .isInstanceOf(ApiErrorException.class);
        verifyNoInteractions(provider);
    }

    @Test
    void disabledProviderGrantsNoSessionAndStripsMemberDiagnostics() {
        var provider = new DisabledOfficeProvider();
        assertThat(provider.capabilities().providerReadiness()).allSatisfy(status -> {
            assertThat(status.configured()).isFalse();
            assertThat(status.diagnostics()).isEmpty();
        });
        assertThatThrownBy(() -> provider.launch(new OfficeLaunchRequest("document", "edit")))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.code()).isEqualTo("office-provider-not-configured");
                });
    }
}
