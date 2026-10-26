package com.massimotter.weave.backend.security.http;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.*;

class PublicHttpRouteTest {
    @Test
    void preservesExactMethodsAndRejectsDuplicateOrOverlappingDeclarations() {
        var get = new PublicHttpRoute("chat", "/_matrix/client/versions", HttpMethod.GET);
        assertThat(PublicHttpRoute.validated(List.of(get))).containsExactly(get);
        assertThat(get.method()).isEqualTo(HttpMethod.GET);
        assertThatThrownBy(() -> PublicHttpRoute.validated(List.of(get, get)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicHttpRoute.validated(List.of(get,
                new PublicHttpRoute("another", get.path(), null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PublicHttpRoute.validated(List.of())).isEmpty();
    }

    @Test
    void rejectsBroadOrAmbiguousPathPatterns() {
        for (String path : List.of("/api/**", "/api/{id}", "/api?query", "relative", "/api#fragment")) {
            assertThatThrownBy(() -> new PublicHttpRoute("owner", path, HttpMethod.GET))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
