package com.massimotter.weave.backend.security.device;

public class DeviceCredentialException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        INVALID
    }

    private final Reason reason;

    public DeviceCredentialException(Reason reason) {
        super(reason == Reason.NOT_FOUND ? "Device credential was not found." : "Device credential is invalid.");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
