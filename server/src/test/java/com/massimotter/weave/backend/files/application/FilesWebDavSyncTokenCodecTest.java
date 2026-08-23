package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesWebDavSyncTokenCodec.InvalidSyncTokenException;
import com.massimotter.weave.backend.files.application.FilesWebDavSyncTokenCodec.SyncTokenState;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FilesWebDavSyncTokenCodecTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.US_ASCII);

    private final FilesWebDavSyncTokenCodec codec = new FilesWebDavSyncTokenCodec(KEY);

    @Test
    void roundTripsOneDeterministicValidUriBoundToCanonicalCollectionAndResetEpoch() {
        SyncTokenState state = new SyncTokenState(
                "org-a",
                "space-a",
                "file-collection-a",
                "stream-generation-a",
                41);

        String first = codec.encode(state);
        String second = codec.encode(state);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith(FilesWebDavSyncTokenCodec.TOKEN_PREFIX);
        assertThat(URI.create(first).isAbsolute()).isTrue();
        assertThat(codec.decode(first)).isEqualTo(state);
        assertThat(codec.decode(first).matches(
                        "org-a",
                        "space-a",
                        "file-collection-a",
                        "stream-generation-a"))
                .isTrue();
    }

    @Test
    void permitsRevisionZeroForAnEmptyCollectionState() {
        SyncTokenState state = new SyncTokenState(
                "org-a",
                "space-a",
                "file-collection-a",
                "stream-generation-a",
                0);

        assertThat(codec.decode(codec.encode(state))).isEqualTo(state);
    }

    @Test
    void scopeMatchingRejectsAnotherCollectionSpaceOrganizationOrResetEpoch() {
        SyncTokenState state = codec.decode(codec.encode(new SyncTokenState(
                "org-a",
                "space-a",
                "file-collection-a",
                "stream-generation-a",
                8)));

        assertThat(state.matches("org-b", "space-a", "file-collection-a", "stream-generation-a"))
                .isFalse();
        assertThat(state.matches("org-a", "space-b", "file-collection-a", "stream-generation-a"))
                .isFalse();
        assertThat(state.matches("org-a", "space-a", "file-collection-b", "stream-generation-a"))
                .isFalse();
        assertThat(state.matches("org-a", "space-a", "file-collection-a", "stream-generation-b"))
                .isFalse();
    }

    @Test
    void rejectsTamperingMalformedUrisAndAnotherTokenNamespaceWithoutEchoingInput() {
        String token = codec.encode(new SyncTokenState(
                "org-a",
                "space-a",
                "file-collection-a",
                "stream-generation-a",
                17));
        int changedIndex = FilesWebDavSyncTokenCodec.TOKEN_PREFIX.length() + 2;
        char replacement = token.charAt(changedIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, changedIndex)
                + replacement
                + token.substring(changedIndex + 1);

        assertInvalid(tampered);
        assertInvalid("files-change-cursor/v1.not-a-uri");
        assertInvalid("urn:weave:files:sync:v2:" + token.substring(
                FilesWebDavSyncTokenCodec.TOKEN_PREFIX.length()));
        assertInvalid(FilesWebDavSyncTokenCodec.TOKEN_PREFIX + "not-an-envelope");
        assertInvalid(null);
    }

    @Test
    void refusesNegativeRevisionsAndInsufficientSigningMaterial() {
        assertThatThrownBy(() -> new SyncTokenState(
                        "org-a",
                        "space-a",
                        "file-collection-a",
                        "stream-generation-a",
                        -1))
                .isInstanceOf(InvalidSyncTokenException.class);
        assertThatThrownBy(() -> new FilesWebDavSyncTokenCodec(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> codec.decode(token))
                .isInstanceOfSatisfying(InvalidSyncTokenException.class, failure ->
                        assertThat(failure.getMessage()).doesNotContain(String.valueOf(token)));
    }
}
