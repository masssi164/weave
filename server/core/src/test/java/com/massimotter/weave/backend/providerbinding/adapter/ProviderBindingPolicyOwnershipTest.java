package com.massimotter.weave.backend.providerbinding.adapter;

import com.massimotter.weave.backend.providerbinding.port.ProviderBindingTransitionPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProviderBindingPolicyOwnershipTest {
    @Test
    void rejectsActivationOfAnUninstalledDomainBeforeWriting() {
        var bindings = mock(ProviderBindingJpaRepository.class);
        var mappings = mock(ProviderObjectMappingJpaRepository.class);
        when(bindings.lockActive(anyString(), anyString(), any())).thenReturn(Optional.empty());
        var repository = new JpaProviderBindingRepository(bindings, mappings, List.of());

        assertThatThrownBy(() -> repository.activate("org:test", "omitted", 0,
                "adapter", "profile:test", Instant.EPOCH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No provider binding policy installed for domain: omitted");
        verify(bindings, never()).saveAndFlush(any());
        verifyNoInteractions(mappings);
    }

    @Test
    void rejectsDuplicateDomainPolicyOwnershipAtConstruction() {
        var policy = mock(ProviderBindingTransitionPolicy.class);
        when(policy.domain()).thenReturn("example");
        assertThatThrownBy(() -> new JpaProviderBindingRepository(
                mock(ProviderBindingJpaRepository.class), mock(ProviderObjectMappingJpaRepository.class),
                List.of(policy, policy)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate key example");
    }
}
