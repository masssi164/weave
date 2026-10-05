package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WorkspaceHomeServiceTest {

    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final WorkspaceHomeRecentActivityService activity = mock(WorkspaceHomeRecentActivityService.class);
    private final WorkspaceHomeService service = new WorkspaceHomeService(capabilities, activity);
    private final Jwt member = Jwt.withTokenValue("member").header("alg", "none").subject("member-1").build();

    @Test
    void readyCapabilitiesDoNotInventCountsOrCopyDiagnosticStrings() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        when(capabilities.snapshot(member)).thenReturn(new WorkspaceCapabilitiesResponse(
                ready, ready, ready, ready, ready, ready));
        when(activity.recentActivity(member)).thenReturn(List.of());

        var home = service.snapshot(member);

        assertThat(home.version()).isEqualTo(3);
        assertThat(home.readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(home.sections()).hasSize(5).allSatisfy(section -> assertThat(section.itemCount()).isNull());
        assertThat(home.sections().getFirst().summary()).isEqualTo("Chat is available.");
        assertThat(home.recentActivity()).isEmpty();
        assertThat(home.toString()).doesNotContain("WEAVE_", "Nextcloud", "secret.example", "operator-action", "backend");
        verify(capabilities).snapshot(member);
        verify(activity).recentActivity(member);
        verifyNoMoreInteractions(capabilities, activity);
    }

    @Test
    void blockedCapabilityDoesNotBlockAuthorizedHomeAndHasOnlyMemberRemediation() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        var blocked = capability(WorkspaceCapabilityReadiness.BLOCKED);
        when(capabilities.snapshot(member)).thenReturn(new WorkspaceCapabilitiesResponse(
                ready, blocked, ready, ready, ready, ready));
        when(activity.recentActivity(member)).thenReturn(List.of());

        var home = service.snapshot(member);

        assertThat(home.readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(home.sections().getFirst().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(home.sections().getFirst().summary()).isEqualTo(
                "Chat is not available for your account. Contact your organization administrator.");
        assertThat(home.actions()).isNotEmpty().allSatisfy(action -> {
            assertThat(action.productRoute()).startsWith("weave://");
            assertThat(action.reason()).contains("organization administrator");
        });
        assertThat(home.sections()).allSatisfy(section -> assertThat(section.itemCount()).isNull());
        assertThat(home.toString()).doesNotContain("WEAVE_", "Nextcloud", "secret.example", "operator-action");
    }

    @Test
    void readyChatAndFilesCannotPromoteAnUnauthorizedDecisionCapability() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        var blocked = capability(WorkspaceCapabilityReadiness.BLOCKED);
        when(capabilities.snapshot(member)).thenReturn(new WorkspaceCapabilitiesResponse(
                ready, ready, ready, ready, ready, ready, ready, blocked, ready, ready, ready, ready));
        when(activity.recentActivity(member)).thenReturn(List.of());

        var home = service.snapshot(member);

        assertThat(home.sections().get(3).key()).isEqualTo("recent-decisions");
        assertThat(home.sections().get(3).readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(home.readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
    }

    @Test
    void unavailableShellIsNotPromotedByReadyCapabilities() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        when(capabilities.snapshot(member)).thenReturn(new WorkspaceCapabilitiesResponse(
                capability(WorkspaceCapabilityReadiness.BLOCKED), ready, ready, ready, ready, ready));
        when(activity.recentActivity(member)).thenReturn(List.of());

        assertThat(service.snapshot(member).readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
    }

    private WorkspaceCapabilityStatusResponse capability(WorkspaceCapabilityReadiness readiness) {
        return new WorkspaceCapabilityStatusResponse(true, readiness, WorkspaceCapabilityPolicyState.ALLOWED,
                "Nextcloud", "Set WEAVE_PRIVATE_SECRET and visit https://secret.example", List.of());
    }
}
