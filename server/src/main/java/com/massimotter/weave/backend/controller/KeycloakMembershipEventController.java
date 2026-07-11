package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.service.KeycloakMembershipEventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KeycloakMembershipEventController {
    private final KeycloakMembershipEventService service;
    public KeycloakMembershipEventController(KeycloakMembershipEventService service) { this.service=service; }

    @PostMapping(path="/api/internal/keycloak/events", consumes="application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@RequestBody byte[] body,
            @RequestHeader("X-Weave-Event-Id") String eventId,
            @RequestHeader("X-Weave-Event-Timestamp") String timestamp,
            @RequestHeader("X-Weave-Event-Signature") String signature) {
        service.accept(body, eventId, timestamp, signature);
    }
}
