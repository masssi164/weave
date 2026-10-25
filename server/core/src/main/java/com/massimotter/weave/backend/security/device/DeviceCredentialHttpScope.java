package com.massimotter.weave.backend.security.device;

/** A domain-owned path boundary for shared device credential authentication. */
public record DeviceCredentialHttpScope(String domain, String pathPrefix) {
    public DeviceCredentialHttpScope {
        new DeviceCredentialDomain(domain);
        if (pathPrefix == null || !pathPrefix.startsWith("/") || pathPrefix.endsWith("/")
                || pathPrefix.contains("?") || pathPrefix.contains("#")) {
            throw new IllegalArgumentException("device credential path must be an absolute path prefix");
        }
    }

    public boolean matches(String path) {
        return path != null && (path.equals(pathPrefix) || path.startsWith(pathPrefix + "/"));
    }
}
