package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MatrixFacadeClientStateServiceDeviceBindingTest {

    @Test
    void conflictingDeviceClaimOrHeaderFailsBeforeIdentityAndStateAccess() {
        MatrixProtocolCoreService protocol = mock(MatrixProtocolCoreService.class);
        MatrixFacadeClientStateStore store = mock(MatrixFacadeClientStateStore.class);
        OrganizationIdentityContextResolver resolver = mock(OrganizationIdentityContextResolver.class);
        MatrixFacadeClientStateService service = new MatrixFacadeClientStateService(
                protocol, store,
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                resolver);
        Jwt conflictingClaim = Jwt.withTokenValue("matrix-token")
                .header("alg", "RS256")
                .subject("member-1")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", "weave:workspace urn:matrix:client:api:* "
                        + "urn:matrix:client:device:SCOPEDDEVICE123")
                .claim("device_id", "OTHERDEVICE123")
                .build();
        Jwt scopedToken = Jwt.withTokenValue("matrix-token")
                .header("alg", "RS256")
                .subject("member-1")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", "weave:workspace urn:matrix:client:api:* "
                        + "urn:matrix:client:device:SCOPEDDEVICE123")
                .build();

        assertThatThrownBy(() -> service.register(conflictingClaim, null))
                .isInstanceOfSatisfying(MatrixProtocolException.class,
                        error -> assertThat(error.errcode()).isEqualTo("M_UNKNOWN_TOKEN"));
        assertThatThrownBy(() -> service.register(scopedToken, "OTHERDEVICE123"))
                .isInstanceOfSatisfying(MatrixProtocolException.class,
                        error -> assertThat(error.errcode()).isEqualTo("M_UNKNOWN_TOKEN"));
        assertThatThrownBy(() -> service.register(scopedToken, " SCOPEDDEVICE123 "))
                .isInstanceOfSatisfying(MatrixProtocolException.class,
                        error -> assertThat(error.errcode()).isEqualTo("M_UNKNOWN_TOKEN"));
        verifyNoInteractions(protocol, store, resolver);
    }
}
