package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.spaces.api.DefaultSpaceBootstrapController;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.spaces.bootstrap.DefaultSpaceBootstrapCredential;
import com.massimotter.weave.backend.spaces.api.DefaultSpaceBootstrapResponse;
import com.massimotter.weave.backend.spaces.application.DefaultSpaceBootstrapService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultSpaceBootstrapControllerTest {
    private final DefaultSpaceBootstrapCredential credential = mock(DefaultSpaceBootstrapCredential.class);
    private final DefaultSpaceBootstrapService service = mock(DefaultSpaceBootstrapService.class);
    private final DefaultSpaceBootstrapController controller = new DefaultSpaceBootstrapController(credential, service);

    @Test void invalidCredentialDoesNotReachSetup() {
        assertThatThrownBy(() -> controller.prepare("invalid")).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("default-space-bootstrap-unauthorized"));
        verifyNoInteractions(service);
        verify(credential, never()).consumeAfterSuccess();
    }

    @Test void unreadableSecretDoesNotReachSetup() {
        when(credential.matches("supplied")).thenThrow(new IllegalStateException("private detail"));
        assertThatThrownBy(() -> controller.prepare("supplied")).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("default-space-bootstrap-unavailable"));
        verifyNoInteractions(service);
    }

    @Test void successfulSetupConsumesAuthority() {
        when(credential.matches("supplied")).thenReturn(true);
        var expected = DefaultSpaceBootstrapResponse.passed("CREATED");
        when(service.prepare()).thenReturn(expected);
        assertThat(controller.prepare("supplied")).isEqualTo(expected);
        var order = inOrder(service, credential);
        order.verify(service).prepare();
        order.verify(credential).consumeAfterSuccess();
    }

    @Test void failedSetupDoesNotConsumeAuthorityOrReturnSuccess() {
        when(credential.matches("supplied")).thenReturn(true);
        when(service.prepare()).thenThrow(new IllegalStateException("setup failed"));
        assertThatThrownBy(() -> controller.prepare("supplied")).isInstanceOf(IllegalStateException.class);
        verify(credential, never()).consumeAfterSuccess();
    }
}
