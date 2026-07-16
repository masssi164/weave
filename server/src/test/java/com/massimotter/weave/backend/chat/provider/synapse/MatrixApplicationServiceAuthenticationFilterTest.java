package com.massimotter.weave.backend.chat.provider.synapse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixApplicationServiceAuthenticationFilterTest {

    private static final String AS_TOKEN = "as-token-value-0123456789";
    private static final String HS_TOKEN = "hs-token-value-0123456789";

    @Test
    void acceptsOnlyConstantTimeHomeserverBearerBoundary() throws Exception {
        MatrixApplicationServiceAuthenticationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT", "/api/internal/chat/matrix/appservice/transactions/opaque");
        request.addHeader("Authorization", "Bearer " + HS_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsWrongOrQueryTokensWithoutReflectingCredentialMaterial() throws Exception {
        MatrixApplicationServiceAuthenticationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT", "/api/internal/chat/matrix/appservice/transactions/opaque");
        request.addHeader("Authorization", "Bearer " + HS_TOKEN);
        request.setParameter("access_token", HS_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("M_FORBIDDEN")
                .doesNotContain(HS_TOKEN, AS_TOKEN);
    }

    private MatrixApplicationServiceAuthenticationFilter filter() {
        MatrixApplicationServiceSecrets secrets = new MatrixApplicationServiceSecrets(
                AS_TOKEN.getBytes(StandardCharsets.UTF_8),
                HS_TOKEN.getBytes(StandardCharsets.UTF_8));
        return new MatrixApplicationServiceAuthenticationFilter(secrets);
    }
}
