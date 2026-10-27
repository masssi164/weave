package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration.ProfileGrant;
import java.util.List;

/** Existing chat capability vocabulary for shared Admin simulation and profile presentation. */
public final class ChatAdminMetadata {
    private ChatAdminMetadata() {}

    public static AdminCapabilityDeclaration capabilities() {
        return new AdminCapabilityDeclaration("chat", 10,
                List.of("chat.read", "chat.send"), List.of("chat.read", "chat.send"), List.of(), List.of("chat.read", "chat.send"),
                List.of(new ProfileGrant("workspace-admin", 10, List.of("chat.read", "chat.send")), new ProfileGrant("member-default", 10, List.of("chat.read", "chat.send"))));
    }
}
