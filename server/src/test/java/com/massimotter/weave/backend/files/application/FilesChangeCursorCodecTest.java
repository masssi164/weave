package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesChangeCursorCodec.CursorState;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class FilesChangeCursorCodecTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.US_ASCII);

    private final FilesChangeCursorCodec codec = new FilesChangeCursorCodec(KEY);

    @Test
    void roundTripsTheCompleteV1ScopeAndPageBoundary() {
        CursorState state = new CursorState("org-a", "space-a", 41, 17, 25);

        String token = codec.encode(state);

        assertThat(token).startsWith(FilesChangeCursorCodec.TOKEN_PREFIX);
        assertThat(codec.decode(token)).isEqualTo(state);
    }

    @Test
    void rejectsTamperingMalformedEnvelopesAndUnknownVersionsWithoutEchoingTheToken() {
        String token = codec.encode(new CursorState("org-a", "space-a", 41, 17, 25));
        int changedIndex = FilesChangeCursorCodec.TOKEN_PREFIX.length() + 2;
        char replacement = token.charAt(changedIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, changedIndex)
                + replacement
                + token.substring(changedIndex + 1);

        assertInvalid(tampered);
        assertInvalid("files-change-cursor/v2." + token.substring(FilesChangeCursorCodec.TOKEN_PREFIX.length()));
        assertInvalid(FilesChangeCursorCodec.TOKEN_PREFIX + "not-an-envelope");
        assertInvalid(null);
    }

    @Test
    void refusesToIssueAContinuationAtOrPastItsCapturedHighWater() {
        assertThatThrownBy(() -> new CursorState("org-a", "space-a", 5, 5, 2))
                .isInstanceOf(FilesChangeReadException.class);
        assertThatThrownBy(() -> new CursorState("org-a", "space-a", 5, 6, 2))
                .isInstanceOf(FilesChangeReadException.class);
    }

    @Test
    void rejectsAuthenticPayloadsWithAnUnknownPayloadVersionOrLastRevisionAboveHighWater()
            throws Exception {
        assertInvalid(authenticToken(2, 5, 2, 2));
        assertInvalid(authenticToken(1, 5, 6, 2));
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> codec.decode(token))
                .isInstanceOfSatisfying(FilesChangeReadException.class, failure -> {
                    assertThat(failure.code())
                            .isEqualTo(FilesChangeReadException.Code.INVALID_CONTINUATION);
                    assertThat(failure.getMessage()).doesNotContain(String.valueOf(token));
                });
    }

    private static String authenticToken(int version, long highWater, long last, int limit)
            throws Exception {
        byte[] payload;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(version);
            writeText(output, "org-a");
            writeText(output, "space-a");
            output.writeLong(highWater);
            output.writeLong(last);
            output.writeInt(limit);
            payload = bytes.toByteArray();
        }
        Mac derivation = Mac.getInstance("HmacSHA256");
        derivation.init(new SecretKeySpec(KEY, "HmacSHA256"));
        byte[] signingKey = derivation.doFinal(
                "weave/files-change-cursor/v1/signing-key".getBytes(StandardCharsets.US_ASCII));
        Mac signature = Mac.getInstance("HmacSHA256");
        signature.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        signature.update(FilesChangeCursorCodec.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII));
        byte[] tag = signature.doFinal(payload);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return FilesChangeCursorCodec.TOKEN_PREFIX
                + encoder.encodeToString(payload)
                + "."
                + encoder.encodeToString(tag);
    }

    private static void writeText(DataOutputStream output, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
