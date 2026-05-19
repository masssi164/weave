package com.massimotter.weave.backend.audit;

public enum AuditAction {
    CONNECTOR_WRITE_ATTEMPTED("connector.write.attempted"),
    ASSISTANT_WRITE_ATTEMPTED("assistant.write.attempted"),
    CONSENT_GRANTED("consent.granted"),
    CONSENT_REVOKED("consent.revoked");

    private final String wireName;

    AuditAction(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
