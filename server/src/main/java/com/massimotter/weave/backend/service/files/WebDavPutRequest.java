package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.port.ReplayableFileContent.StreamFactory;
import java.util.List;
import java.util.Objects;

/** Raw WebDAV PUT framing plus a body factory that remains unopened until admission succeeds. */
public record WebDavPutRequest(
        List<String> contentLengthFields,
        List<String> contentTypeFields,
        List<String> contentEncodingFields,
        List<String> transferEncodingFields,
        StreamFactory requestBody) {

    public WebDavPutRequest {
        contentLengthFields = immutable(contentLengthFields);
        contentTypeFields = immutable(contentTypeFields);
        contentEncodingFields = immutable(contentEncodingFields);
        transferEncodingFields = immutable(transferEncodingFields);
        requestBody = Objects.requireNonNull(requestBody, "requestBody must not be null");
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
