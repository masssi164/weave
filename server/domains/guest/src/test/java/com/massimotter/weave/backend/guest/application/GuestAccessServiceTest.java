package com.massimotter.weave.backend.guest.application;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.guest.api.GuestInvitationRequest;
import com.massimotter.weave.backend.guest.config.GuestAccessProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestAccessServiceTest {
    @Test
    void defaultDeniedInvitationNeverGrantsMemberOrGuestAccess() {
        var service = new GuestAccessService(new GuestAccessProperties(false));
        assertThatThrownBy(() -> service.invite(new GuestInvitationRequest("guest@example.invalid", "Guest", List.of("room.read"))))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> assertThat(error.code()).isEqualTo("guest-access-disabled"));
    }

    @Test
    void explicitFuturePolicyKeepsInvitationPendingAndRestrictsCapabilities() {
        var service = new GuestAccessService(new GuestAccessProperties(true));
        var result = service.invite(new GuestInvitationRequest("guest@example.invalid", "Guest",
                List.of("room.read", "admin.manage", "room.read", "file.read")));
        assertThat(result.state()).isEqualTo("pending");
        assertThat(result.grantedCapabilities()).containsExactly("file.read", "room.read");
        assertThat(result.explicitPolicyRequired()).isTrue();
        assertThat(result.internalUserMerged()).isFalse();
    }
}
