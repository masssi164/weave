package com.massimotter.weave.backend.portability.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.profile.port.ProductProfileOverrideRepository;
import com.massimotter.weave.backend.portability.api.ProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.portability.port.MigrationRunEvidenceRepository;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Security/validation runs without an App, sibling runtime, database or provider SDK. */
class ProviderReplacementDryRunServiceTest {
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final ProviderCategoryCatalog categories = mock(ProviderCategoryCatalog.class);
    private final ProviderCapabilityContracts contracts = mock(ProviderCapabilityContracts.class);
    private final ProviderRegistry registry = mock(ProviderRegistry.class);
    private final ProviderSelectionRepository selections = mock(ProviderSelectionRepository.class);
    private final ProductProfileOverrideRepository profiles = mock(ProductProfileOverrideRepository.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private final MigrationRunEvidenceRepository evidence = mock(MigrationRunEvidenceRepository.class);
    private final OrganizationIdentityContextResolver identities = mock(OrganizationIdentityContextResolver.class);
    private final Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("member").build();
    private final ProviderReplacementDryRunService service = new ProviderReplacementDryRunService(
            categories, contracts, registry, capabilities, selections, profiles, audit, evidence, identities);

    @Test
    void permissionFailureCannotProbeProvidersOrWriteEvidence() {
        doThrow(new IllegalStateException("denied")).when(capabilities).requireCapability(
                jwt, "admin.provider.configure", "admin-control-plane", "provider-replacement-dry-run");
        assertThatThrownBy(() -> service.dryRunProviderReplacement(request("files"), jwt)).hasMessage("denied");
        verifyNoInteractions(categories, contracts, registry, selections, profiles, audit, evidence, identities);
    }

    @Test
    void unknownCategoryCannotCreateEvidenceOrAdvertiseReadiness() {
        when(categories.category("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.dryRunProviderReplacement(request("unknown"), jwt))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("provider-category-unknown"));
        verifyNoInteractions(contracts, registry, selections, profiles, audit, evidence, identities);
    }

    @Test
    void missingRequestFailsBeforeProviderAndPersistenceAccess() {
        assertThatThrownBy(() -> service.dryRunProviderReplacement(null, jwt))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("provider-replacement-invalid"));
        verifyNoInteractions(categories, contracts, registry, selections, profiles, audit, evidence, identities);
    }

    private ProviderReplacementDryRunRequest request(String category) {
        return new ProviderReplacementDryRunRequest(category, "current", "target",
                "recommended_self_hosted_default", "secretref://weave/provider/current", "weave-domain",
                List.of(), true, Map.of(), "review");
    }
}
