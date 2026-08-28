package com.massimotter.weave.backend.runner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimHttpResponse;
import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimPreference;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunnerClaimHttpSemanticsTest {

    @Test
    void absentPreferUsesTheDocumentedDefault() {
        ClaimPreference preference = RunnerClaimHttpSemantics.parsePrefer(List.of());

        assertEquals(25, preference.waitSeconds());
        assertEquals("wait=25", preference.appliedHeader());
    }

    @Test
    void exactWaitPreferenceAcceptsBothProtocolBoundaries() {
        assertEquals(0, RunnerClaimHttpSemantics.parsePrefer(List.of("wait=0")).waitSeconds());
        assertEquals(30, RunnerClaimHttpSemantics.parsePrefer(List.of("wait=30")).waitSeconds());
    }

    @Test
    void duplicateOrNonCanonicalPreferencesFailClosed() {
        List<List<String>> invalid = List.of(
                List.of("wait=1", "wait=2"),
                List.of(" wait=1"),
                List.of("wait=1 "),
                List.of("wait=01"),
                List.of("wait=-1"),
                List.of("wait=31"),
                List.of("respond-async"),
                List.of("wait=1, respond-async"),
                List.of("WAIT=1"));

        for (List<String> values : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RunnerClaimHttpSemantics.parsePrefer(values),
                    () -> "accepted invalid Prefer values: " + values);
        }
    }

    @Test
    void leaseResponseIsNonCacheableAndReportsTheAppliedWait() {
        ClaimPreference preference = new ClaimPreference(7);
        ClaimHttpResponse<String> response =
                RunnerClaimHttpSemantics.respond(Optional.of("lease"), preference);

        assertEquals(200, response.status());
        assertEquals("no-store", response.headers().get("Cache-Control"));
        assertEquals("wait=7", response.headers().get("Preference-Applied"));
        assertFalse(response.headers().containsKey("Retry-After"));
        assertEquals(Optional.of("lease"), response.body());
    }

    @Test
    void emptyResponseIsNonCacheableAndCarriesRetryAdvice() {
        ClaimPreference preference = new ClaimPreference(25);
        ClaimHttpResponse<String> response =
                RunnerClaimHttpSemantics.respond(Optional.empty(), preference);

        assertEquals(204, response.status());
        assertEquals("no-store", response.headers().get("Cache-Control"));
        assertEquals("wait=25", response.headers().get("Preference-Applied"));
        assertEquals("1", response.headers().get("Retry-After"));
        assertTrue(response.body().isEmpty());
    }
}
