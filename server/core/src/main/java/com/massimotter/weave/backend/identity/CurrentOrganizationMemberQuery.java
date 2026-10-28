package com.massimotter.weave.backend.identity;

import org.springframework.security.oauth2.jwt.Jwt;

/** Live human membership query; saved registration and display names are not admission. */
public interface CurrentOrganizationMemberQuery {
    void requireCurrentMember(Jwt caller, String identityIssuer, String subject);

    class Denied extends RuntimeException {
        public Denied() { super("Current organization membership is required."); }
    }

    class Unavailable extends RuntimeException {
        public Unavailable() { super("Current organization membership is unavailable."); }
    }
}
