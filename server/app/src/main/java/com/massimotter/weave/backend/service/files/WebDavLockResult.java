package com.massimotter.weave.backend.service.files;

public record WebDavLockResult(String path, String token, int timeoutSeconds) {
}
