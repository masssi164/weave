package com.massimotter.weave.backend.home.application;



import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import java.util.List;
import java.util.Map;
import com.massimotter.weave.backend.home.port.WorkspaceHomeActivitySource;
import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceHomeServiceTest {

    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final WorkspaceHomeActivitySource activity = activitySource();
    private final WorkspaceHomeService service = new WorkspaceHomeService(capabilities, sections(), List.of(activity));
    private final Jwt member = Jwt.withTokenValue("member").header("alg", "none").subject("member-1").build();

    @Test
    void readyCapabilitiesDoNotInventCountsOrCopyDiagnosticStrings() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        install(snapshot(
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
        verify(capabilities).installedSnapshots(member);
    }

    @Test
    void blockedCapabilityDoesNotBlockAuthorizedHomeAndHasOnlyMemberRemediation() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        var blocked = capability(WorkspaceCapabilityReadiness.BLOCKED);
        install(snapshot(
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
        install(snapshot(
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
        install(snapshot(
                capability(WorkspaceCapabilityReadiness.BLOCKED), ready, ready, ready, ready, ready));
        when(activity.recentActivity(member)).thenReturn(List.of());

        assertThat(service.snapshot(member).readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
    }

    private WorkspaceCapabilityStatusResponse capability(WorkspaceCapabilityReadiness readiness) {
        return new WorkspaceCapabilityStatusResponse(true, readiness, WorkspaceCapabilityPolicyState.ALLOWED,
                "Nextcloud", "Set WEAVE_PRIVATE_SECRET and visit https://secret.example", List.of());
    }
    private void install(WorkspaceCapabilitiesResponse snapshot) {
        when(capabilities.snapshot(member)).thenReturn(snapshot);
        when(capabilities.installedSnapshots(member)).thenReturn(Map.of("chat", snapshot.chat(), "files", snapshot.files(),
                "calendar", snapshot.calendar(), "boards", snapshot.boards(), "decisions", snapshot.decisionsEvidence()));
    }

    private WorkspaceCapabilitiesResponse snapshot(WorkspaceCapabilityStatusResponse... states) {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        if (states.length == 12) {
            return new WorkspaceCapabilitiesResponse(states[0], states[1], states[2], states[3], states[4], states[5],
                    states[6], states[7], states[8], states[9], states[10], states[11]);
        }
        return new WorkspaceCapabilitiesResponse(states[0], states[1], states[2], states[3], states[4], ready, ready,
                ready, ready, ready, ready, states[5]);
    }

    private WorkspaceHomeActivitySource activitySource() {
        var source = mock(WorkspaceHomeActivitySource.class);
        when(source.domainId()).thenReturn("files");
        return source;
    }

    /** Fake owner data tests generic composition; production labels remain in each domain. */
    private List<WorkspaceHomeContribution> sections() {
        return List.of(new WorkspaceHomeContribution("files", List.of()),
                contribution("chat", "recent-channels", "Recent channels", "Chat", "weave://home/channels", 0),
                contribution("boards", "open-tasks", "Open tasks", "Tasks", "weave://home/tasks", 1),
                contribution("calendar", "upcoming-meetings", "Upcoming meetings", "Calendar", "weave://home/meetings", 2),
                contribution("decisions", "recent-decisions", "Recent decisions", "Decisions", "weave://home/decisions", 3));
    }

    private WorkspaceHomeContribution contribution(String domain, String key, String title, String label, String route, int order) {
        return new WorkspaceHomeContribution(domain, List.of(new WorkspaceHomeContribution.Section(key, title, label, route, order)));
    }

    @Test
    void aFilesOnlyHomeOmitsOtherSectionsAndDoesNotBecomeDegradedFromTheirAbsence() {
        var ready = capability(WorkspaceCapabilityReadiness.READY);
        when(capabilities.snapshot(member)).thenReturn(new WorkspaceCapabilitiesResponse(
                ready, null, ready, null, null, null, null, null, null, null, null, null));
        when(capabilities.installedSnapshots(member)).thenReturn(Map.of("files", ready));
        var onlyFiles = new WorkspaceHomeService(capabilities,
                List.of(new WorkspaceHomeContribution("files", List.of())), List.of());
        var home = onlyFiles.snapshot(member);
        assertThat(home.readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(home.sections()).extracting(section -> section.key()).containsExactly("workspace-health");
        assertThat(home.actions()).isEmpty();
    }

    @Test
    void duplicateOrMissingOwnershipCannotBeHiddenByHomeComposition() {
        var declaration = new WorkspaceHomeContribution("files", List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WorkspaceHomeService(capabilities,
                List.of(declaration, declaration), List.of())).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WorkspaceHomeService(capabilities,
                List.of(), List.of(activity))).isInstanceOf(IllegalArgumentException.class);
        when(capabilities.installedSnapshots(member)).thenReturn(Map.of());
        when(capabilities.snapshot(member)).thenReturn(snapshot(capability(WorkspaceCapabilityReadiness.READY),
                capability(WorkspaceCapabilityReadiness.READY), capability(WorkspaceCapabilityReadiness.READY),
                capability(WorkspaceCapabilityReadiness.READY), capability(WorkspaceCapabilityReadiness.READY),
                capability(WorkspaceCapabilityReadiness.READY)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new WorkspaceHomeService(capabilities,
                List.of(declaration), List.of()).snapshot(member)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires installed capability");
    }
}
