package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MatrixApplicationServiceSecurityConfigurationTest {

    @Test
    void applicationServiceAuthenticationFilterIsOwnedOnlyByItsSecurityChain() {
        MatrixApplicationServiceSecurityConfiguration configuration =
                new MatrixApplicationServiceSecurityConfiguration();
        var filter = configuration.matrixApplicationServiceAuthenticationFilter(
                mock(MatrixApplicationServiceSecrets.class));
        var registration = configuration.matrixApplicationServiceAuthenticationFilterRegistration(filter);

        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isSameAs(filter);
    }
}
