package com.massimotter.weave.backend.identity.realm;

public class KeycloakRealmAdminClientException extends RuntimeException {

    public KeycloakRealmAdminClientException(String message) {
        super(message);
    }

    public KeycloakRealmAdminClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
