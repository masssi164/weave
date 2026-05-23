package com.massimotter.weave.backend.model.calendar;

import jakarta.validation.constraints.Size;

public record CalendarSetupCredentialRequest(
        @Size(max = 128) String label,
        @Size(max = 32) String clientType) {
}
