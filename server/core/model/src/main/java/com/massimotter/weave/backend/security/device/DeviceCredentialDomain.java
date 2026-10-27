package com.massimotter.weave.backend.security.device;

/** Installed domain authorized to issue and validate shared adaptive-hash device credentials. */
public record DeviceCredentialDomain(String id) {
    public DeviceCredentialDomain {
        if (id == null || !id.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("device credential domain must be a stable identifier");
        }
    }
}
