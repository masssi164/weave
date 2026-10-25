package com.massimotter.weave.backend.security.device;

public record IssuedDeviceCredential(DeviceCredential credential, String secret) {
    @Override
    public String toString() { return "IssuedDeviceCredential[redacted]"; }
}
