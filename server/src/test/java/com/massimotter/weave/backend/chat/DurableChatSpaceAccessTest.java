package com.massimotter.weave.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.chat.domain.ChatActorRef;
import com.massimotter.weave.backend.chat.domain.ChatRequestContext;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class DurableChatSpaceAccessTest {
    private final SpaceAccessPort spaces = mock(SpaceAccessPort.class);
    private final DurableChatSpaceAccess access = new DurableChatSpaceAccess(spaces);

    @Test
    void derivesOnlyTheImmutableIssuerSubjectAccountAndCurrentSpacePermission() {
        ChatRequestContext context = new ChatRequestContext("tenant-a", "space-b",
                "https://auth.weave.test/realms/a", new ChatActorRef("user:subject-123"));
        String account = IdentityReferences.accountId(context.identityIssuer(), "subject-123");
        when(spaces.allows("tenant-a", "space-b", account, SpaceAccessPort.Permission.EDIT))
                .thenReturn(true);

        assertThat(access.allows(context, ContextPermission.EDIT)).isTrue();
        verify(spaces).allows("tenant-a", "space-b", account, SpaceAccessPort.Permission.EDIT);
        assertThat(access.allows(context, ContextPermission.ADMIN)).isFalse();
        when(spaces.visibleSpaceRefs("tenant-a", account, "", 100))
                .thenReturn(List.of("space-b"));
        assertThat(access.visibleSpaces(context, "", 100)).containsExactly("space-b");
    }

    @Test
    void nonHumanOrMalformedActorNeverQueriesSpaceMembership() {
        ChatRequestContext workload = new ChatRequestContext("tenant-a", "space-b",
                "https://auth.weave.test/realms/a", new ChatActorRef("service:bot"));
        ChatRequestContext malformed = new ChatRequestContext("tenant-a", "space-b",
                "https://auth.weave.test/realms/a", new ChatActorRef("user:bad subject"));

        assertThat(access.allows(workload, ContextPermission.VIEW)).isFalse();
        assertThat(access.visibleSpaces(workload, "", 100)).isEmpty();
        assertThat(access.allows(malformed, ContextPermission.VIEW)).isFalse();
        verifyNoInteractions(spaces);
    }
}
