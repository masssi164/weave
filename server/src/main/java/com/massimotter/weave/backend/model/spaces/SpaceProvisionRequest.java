package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicit Admin setup intent; account IDs are opaque issuer-and-subject-derived references. */
public record SpaceProvisionRequest(
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:_-]{0,254}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Chosen stable Weave Space reference in the current organization.")
        String spaceRef,
        @Size(max = 100)
        @Schema(description = "Member account references granted VIEW. The acting administrator receives ADMIN.")
        List<@Pattern(regexp = "acct_[0-9a-f]{32}") String> memberAccountRefs) {
    public SpaceProvisionRequest {
        memberAccountRefs = memberAccountRefs == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(memberAccountRefs));
    }
}
