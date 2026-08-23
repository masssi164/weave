package com.massimotter.weave.backend.controller.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.controller.protocol.WebDavIfHeader.Evaluation;
import com.massimotter.weave.backend.controller.protocol.WebDavIfHeader.Header;
import com.massimotter.weave.backend.controller.protocol.WebDavIfHeader.InvalidIfHeaderException;
import com.massimotter.weave.backend.controller.protocol.WebDavIfHeader.StateResolver;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebDavIfHeaderTest {

    private static final String LOCK = "opaquelocktoken:11111111-1111-1111-1111-111111111111";
    private static final String SYNC = "urn:weave:files:sync:v1:opaque.signature";

    @Test
    void noTagListsAreDisjunctionsAndConditionsInsideAListAreConjunctions() {
        Header header = WebDavIfHeader.parse("(<" + LOCK + "> [\"v1\"]) ([\"v2\"])");

        Evaluation first = WebDavIfHeader.evaluate(header, "/dav/files/a.txt", resolver(
                Map.of("/dav/files/a.txt", Set.of(LOCK)),
                Map.of("/dav/files/a.txt", Set.of("\"v1\""))));
        assertThat(first.satisfied()).isTrue();
        assertThat(first.submitted(LOCK)).isTrue();

        Evaluation second = WebDavIfHeader.evaluate(header, "/dav/files/a.txt", resolver(
                Map.of(),
                Map.of("/dav/files/a.txt", Set.of("\"v2\""))));
        assertThat(second.satisfied()).isTrue();

        Evaluation neither = WebDavIfHeader.evaluate(header, "/dav/files/a.txt", resolver(Map.of(), Map.of()));
        assertThat(neither.satisfied()).isFalse();
    }

    @Test
    void taggedProductionsApplyListsToTheirResourceAndTheWholeHeaderIsADisjunction() {
        Header header = WebDavIfHeader.parse(
                "</dav/files/Team> (<" + SYNC + "> [\"old\"]) ([\"current\"]) "
                        + "</dav/files/Other> (<" + LOCK + ">)");

        Evaluation evaluation = WebDavIfHeader.evaluate(header, "/dav/files/ignored", resolver(
                Map.of("/dav/files/Other", Set.of(LOCK)),
                Map.of()));

        assertThat(header.tagged()).isTrue();
        assertThat(header.productions()).hasSize(2);
        assertThat(evaluation.satisfied()).isTrue();
        assertThat(evaluation.submittedStateTokens()).containsExactlyInAnyOrder(SYNC, LOCK);
    }

    @Test
    void notNegatesOnlyTheFollowingCondition() {
        Header header = WebDavIfHeader.parse("(Not <" + LOCK + "> <" + SYNC + ">)");

        assertThat(WebDavIfHeader.evaluate(header, "/dav/files/Team", resolver(
                        Map.of("/dav/files/Team", Set.of(SYNC)), Map.of())).satisfied())
                .isTrue();
        assertThat(WebDavIfHeader.evaluate(header, "/dav/files/Team", resolver(
                        Map.of("/dav/files/Team", Set.of(LOCK, SYNC)), Map.of())).satisfied())
                .isFalse();
    }

    @Test
    void stateTokensCountAsSubmittedEvenWhenNegatedOrInAFailingList() {
        Header header = WebDavIfHeader.parse("(Not <" + SYNC + "> [\"missing\"])");

        Evaluation evaluation = WebDavIfHeader.evaluate(
                header, "/dav/files/Team", resolver(Map.of(), Map.of()));

        assertThat(evaluation.satisfied()).isFalse();
        assertThat(evaluation.submitted(SYNC)).isTrue();
    }

    @Test
    void unmappedResourcesSimplyHaveNoMatchingState() {
        Header positive = WebDavIfHeader.parse("</missing> (<" + SYNC + ">)");
        Header negative = WebDavIfHeader.parse("</missing> (Not <" + SYNC + ">)");

        assertThat(WebDavIfHeader.evaluate(positive, "/ignored", resolver(Map.of(), Map.of())).satisfied())
                .isFalse();
        assertThat(WebDavIfHeader.evaluate(negative, "/ignored", resolver(Map.of(), Map.of())).satisfied())
                .isTrue();
    }

    @Test
    void parsesWeakEtagsQuotedPairsRelativeTagsAndCaseInsensitiveNot() {
        Header header = WebDavIfHeader.parse(
                "</dav/files/Team> (nOt <" + SYNC + "> [W/\"a\\\"b\"])");

        assertThat(header.productions().getFirst().resourceTag()).isEqualTo("/dav/files/Team");
        assertThat(header.productions().getFirst().lists().getFirst().conditions()).hasSize(2);
    }

    @Test
    void rejectsMixedFormsEmptyListsNonUriStateTokensAndWhitespaceInsideCodedUrls() {
        for (String invalid : new String[] {
            "",
            "()",
            "</a>",
            "(<not absolute>)",
            "(<relative-token>)",
            "</a> (<" + LOCK + ">) ([\"v1\"])",
            "</a> (<" + LOCK + ">) </b>",
            "([ W/\"v1\"])",
            "([\"unterminated])",
            "(Not)",
            "(<" + LOCK + ">)\r\n ([\"v1\"])",
        }) {
            assertThatThrownBy(() -> WebDavIfHeader.parse(invalid))
                    .as(invalid)
                    .isInstanceOf(InvalidIfHeaderException.class);
        }
    }

    @Test
    void rejectsOversizedHeadersWithoutEchoingTheirContents() {
        String oversized = "(" + ("<" + LOCK + ">").repeat(900) + ")";

        assertThatThrownBy(() -> WebDavIfHeader.parse(oversized))
                .isInstanceOfSatisfying(InvalidIfHeaderException.class, failure ->
                        assertThat(failure.getMessage()).doesNotContain(LOCK));
    }

    private static StateResolver resolver(
            Map<String, Set<String>> tokens,
            Map<String, Set<String>> etags) {
        return new StateResolver() {
            @Override
            public boolean matchesStateToken(String resourceReference, String stateToken) {
                return tokens.getOrDefault(resourceReference, Set.of()).contains(stateToken);
            }

            @Override
            public boolean matchesEntityTag(String resourceReference, String entityTag) {
                return etags.getOrDefault(resourceReference, Set.of()).contains(entityTag);
            }
        };
    }
}
