package com.massimotter.weave.backend.files.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class ReplayableFileContentTest {

    private static final String EMPTY_DIGEST =
            "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void preservesOneExactSyntacticallyValidMediaType() {
        var content = new ReplayableFileContent(
                0,
                EMPTY_DIGEST,
                "Text/Plain; Charset=\"UTF-8\"; profile=example",
                () -> new ByteArrayInputStream(new byte[0]));

        assertEquals(
                "Text/Plain; Charset=\"UTF-8\"; profile=example",
                content.mediaType());
    }

    @Test
    void preservesQuotedObsTextAcceptedByTheHttpBoundary() {
        var content = new ReplayableFileContent(
                0,
                EMPTY_DIGEST,
                "text/plain; title=\"café\"",
                () -> new ByteArrayInputStream(new byte[0]));

        assertEquals("text/plain; title=\"café\"", content.mediaType());
    }

    @Test
    void rejectsValuesThatAreNotMediaTypes() {
        for (String invalid : new String[] {
                "not a media type",
                "text",
                "text/",
                "/plain",
                "text/plain; charset",
                "text/plain; charset=\"unterminated",
                "text/plain; title=\"\u0100\"",
                "text/plain\r\nInjected: value"
        }) {
            assertThrows(IllegalArgumentException.class, () -> new ReplayableFileContent(
                            0,
                            EMPTY_DIGEST,
                            invalid,
                            () -> new ByteArrayInputStream(new byte[0])));
        }
    }
}
