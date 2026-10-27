package com.massimotter.weave.backend.files.api.dav;

public record WebDavLockResult(String path, String token, int timeoutSeconds) {
}
