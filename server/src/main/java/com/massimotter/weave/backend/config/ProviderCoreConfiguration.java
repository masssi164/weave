package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesStreamingCapabilityProfile;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderPort;
import com.massimotter.weave.backend.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.provider.RuntimeProviderStatus;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.provider.StaticProviderPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderCoreConfiguration {

    @Bean
    ProviderPort chatProviderRegistrySeam(ObjectProvider<ChatProviderPort> chatProviderPort) {
        ChatProviderPort runtime = chatProviderPort.getIfAvailable();
        if (runtime != null) {
            return chatProviderRegistrySeamFor(runtime);
        }
        return StaticProviderPort.pending(
                ProviderModule.MATRIX,
                "weave-native",
                "No Chat runtime adapter is bound; the canonical Chat and Matrix protocol facades remain fail-closed.",
                Set.of("workspace-room-readiness", "message-sync-readiness", "e2ee-status-readiness", "homeserver-discovery"),
                Set.of("room-key-export", "raw-homeserver-errors", "direct-flutter-admin-api", "credential-exposure"),
                List.of("weave-native", "matrix-synapse", "synapse-homeserver", "slack", "microsoft-teams"),
                Map.of("canonicalDomain", "chat", "facade", "/_matrix/client", "mediaCallsCovered", false));
    }

    ProviderPort chatProviderRegistrySeamFor(ChatProviderPort runtime) {
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.MATRIX,
                runtime.providerKey(),
                runtime.configured(),
                runtime.conformanceProfile(),
                "The selected Chat adapter is bound behind the canonical Chat port; runtime reachability is reported by cached capability health.",
                List.of("weave-native", "matrix-synapse", "synapse-homeserver", "slack", "microsoft-teams"));
    }

    @Bean
    ProviderPort filesProviderRegistrySeam(ObjectProvider<FilesProviderPort> filesProviderPort) {
        FilesProviderPort runtime = filesProviderPort.getIfAvailable();
        if (runtime == null) {
            return StaticProviderPort.pending(
                    ProviderModule.FILES,
                    "weave-native",
                    "No Files runtime adapter is bound; the canonical Files facade remains fail-closed.",
                    Set.of("list", "create-collection", "delete", "copy", "move"),
                    Set.of("direct-member-provider-api", "credential-exposure", "raw-provider-errors"),
                    List.of("weave-native", "nextcloud-files", "webdav", "sharepoint", "onedrive", "s3-compatible", "smb"),
                    Map.of("runtimeBindingObserved", false, "facade", "/dav/files"));
        }
        return filesProviderRegistrySeamFor(runtime);
    }

    ProviderPort filesProviderRegistrySeamFor(FilesProviderPort runtime) {
        return () -> currentFilesProviderRegistry(runtime).status();
    }

    private ProviderPort currentFilesProviderRegistry(FilesProviderPort runtime) {
        Map<String, Object> diagnostics = Map.of();
        ProviderConformanceProfile conformance = runtime.conformanceProfile();
        FilesStreamingCapabilityProfile capability;
        if (runtime instanceof FilesStreamingContentPort streaming) {
            capability = FilesStreamingCapabilityProfile.observe(runtime, streaming);
        } else {
            capability = FilesStreamingCapabilityProfile.blocked(
                    conformance.adapterKey(),
                    java.time.Instant.now());
        }
        diagnostics = Map.of("capabilityProfile", capability.projection());
        if (!capability.qualified()) {
            Set<String> supported = new LinkedHashSet<>(conformance.supportedOperations());
            supported.remove("read");
            supported.remove("write");
            supported.remove(FilesStreamingCapabilityProfile.READ);
            supported.remove(FilesStreamingCapabilityProfile.WRITE);
            conformance = new ProviderConformanceProfile(
                    conformance.domain(),
                    conformance.adapterKey(),
                    supported,
                    conformance.fieldMappings(),
                    conformance.atomicWrites(),
                    conformance.stableVersionTokens(),
                    conformance.supportSafe());
        }
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.FILES,
                conformance.adapterKey(),
                runtime.configured(),
                conformance,
                "The selected Files adapter is bound behind the canonical Files port and the /dav/files projection.",
                List.of("weave-native", "nextcloud-files", "webdav", "sharepoint", "onedrive", "s3-compatible", "smb"),
                diagnostics);
    }

    @Bean
    ProviderPort calendarProviderRegistrySeam(ObjectProvider<CalendarProviderPort> calendarProviderPort) {
        CalendarProviderPort runtime = calendarProviderPort.getIfAvailable();
        if (runtime == null) {
            return StaticProviderPort.pending(
                    ProviderModule.CALENDAR,
                    "weave-native",
                    "No Calendar runtime adapter is bound; the canonical Calendar facade remains fail-closed.",
                    Set.of("query", "read", "create", "update", "delete", "free-busy"),
                    Set.of("direct-member-provider-api", "credential-exposure", "raw-provider-errors"),
                    List.of("weave-native", "nextcloud-caldav", "microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav"),
                    Map.of("runtimeBindingObserved", false, "facade", "/caldav"));
        }
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.CALENDAR,
                runtime.conformanceProfile().adapterKey(),
                runtime.configured(),
                runtime.conformanceProfile(),
                "The selected Calendar adapter is bound behind the canonical Calendar port and the /caldav projection.",
                List.of("weave-native", "nextcloud-caldav", "microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav"));
    }

    @Bean
    ProviderPort contactsProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.CONTACTS,
                "nextcloud-carddav",
                "Contacts provider seam is reserved for the Nextcloud CardDAV contract from backend PR #104.",
                Set.of("address-book-list", "contact-search", "contact-read"),
                Set.of("credential-exposure", "direct-flutter-carddav", "raw-vcard-errors"),
                List.of("nextcloud-carddav"),
                Map.of("dependency", "weave-backend#104", "compatibleSeam", true));
    }

    @Bean
    ProviderPort formsProviderRegistrySeam() {
        return StaticProviderPort.pending(
                ProviderModule.FORMS,
                "nextcloud-forms",
                "Forms provider seam is reserved for the Nextcloud Forms contract from backend PR #104.",
                Set.of("form-list", "form-read", "submission-summary", "submission-export"),
                Set.of("raw-provider-errors", "credential-exposure", "direct-flutter-forms-api"),
                List.of("nextcloud-forms"),
                Map.of("dependency", "weave-backend#104", "compatibleSeam", true));
    }

    @Bean
    ProviderPort boardsProviderRegistrySeam(ObjectProvider<BoardsRepository> boardsRepository) {
        BoardsRepository runtime = boardsRepository.getIfAvailable();
        if (runtime == null) {
            return StaticProviderPort.pending(
                    ProviderModule.BOARDS,
                    "local-workspace",
                    "No Boards runtime adapter is bound; the canonical Boards facade remains fail-closed.",
                    Set.of("project-list", "board-list", "task-list", "task-create", "task-move", "task-complete"),
                    Set.of("direct-member-provider-api", "provider-writes-without-audit", "raw-provider-errors"),
                    List.of("local-workspace", "openproject-primary", "microsoft-planner", "jira", "vikunja", "nextcloud-deck"),
                    Map.of("runtimeBindingObserved", false, "facade", "/api/boards/workspace"));
        }
        return boardsProviderRegistrySeamFor(runtime);
    }

    ProviderPort boardsProviderRegistrySeamFor(BoardsRepository runtime) {
        BoardProviderCapabilities capabilities = runtime.capabilities();
        String providerKey = boardsProviderKey(capabilities.provider());
        return RuntimeProviderStatus.fixed(
                ProviderModule.BOARDS,
                providerKey,
                capabilities.enabled(),
                "The selected Boards adapter is bound behind the canonical Boards repository and workspace facade.",
                capabilities.supported().stream()
                        .map(capability -> capability.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                capabilities.unsupported().stream()
                        .map(capability -> capability.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                List.of("local-workspace", "openproject-primary", "microsoft-planner", "jira", "vikunja", "nextcloud-deck"),
                capabilities.enabled() ? ProviderRealityLevel.CONFIGURED : ProviderRealityLevel.CONTRACT_ONLY,
                Map.of(
                        "canonicalDomain", "boards-tasks",
                        "runtimeAdapterKind", providerKey,
                        "facade", "/api/boards/workspace",
                        "supportSafeSummary", capabilities.supportSafeSummary()));
    }

    private String boardsProviderKey(ProviderKind provider) {
        return switch (provider) {
            case IN_MEMORY -> "local-workspace";
            case OPEN_PROJECT -> "openproject-primary";
            default -> provider.contractName();
        };
    }

    @Bean
    ProviderPort liveKitSfuProviderRegistrySeam(LiveKitSfuProviderProperties liveKit) {
        ProviderState state = liveKit.enabled()
                ? liveKit.configured() ? ProviderState.CONFIGURED : ProviderState.NOT_CONFIGURED
                : ProviderState.DISABLED;
        String readiness = liveKit.enabled()
                ? liveKit.configured() ? "configured" : "not_configured"
                : "disabled";

        return new StaticProviderPort(new ProviderStatusResponse(
                ProviderModule.MEETINGS,
                "livekit",
                state,
                readiness,
                liveKit.enabled(),
                liveKit.enabled() && liveKit.configured(),
                false,
                true,
                true,
                false,
                "LiveKit is a replaceable southbound SFU adapter for MatrixRTC Profile 0; signaling, membership, authorization, consent, and member APIs remain Weave/Matrix-owned.",
                Set.of(
                        "sfu-configuration-readiness",
                        "matrixrtc-profile-0-target",
                        "media-e2ee-readiness",
                        "turn-readiness"),
                Set.of(
                        "member-calls-rest-api",
                        "proprietary-join-grant",
                        "identity-only-sfu-token",
                        "direct-flutter-livekit-admin-api",
                        "livekit-api-key-exposure",
                        "livekit-api-secret-exposure",
                        "credential-bearing-join-url",
                        "raw-provider-errors"),
                List.of("calls-sfu-not-configured", "calls-sfu-disabled", "calls-sfu-unavailable", "rtc-authorization-required"),
                "support-safe: no LiveKit API keys, API secrets, bearer tokens, room tokens, credential-bearing URLs, or raw provider errors",
                List.of("livekit", "generic-webrtc-sfu"),
                ProviderRealityLevel.CONFIGURED,
                Map.of(
                        "activeSfuAdapter", "livekit",
                        "livekitUrlConfigured", liveKit.urlConfigured(),
                        "apiKeyConfigured", liveKit.apiKeyConfigured(),
                        "apiSecretConfigured", liveKit.apiSecretConfigured(),
                        "tokenEndpointConfigured", liveKit.tokenEndpointConfigured(),
                        "directCredentialModeConfigured", liveKit.directCredentialModeConfigured(),
                        "tokenEndpointModeConfigured", liveKit.tokenEndpointModeConfigured(),
                        "secretsReturned", false)));
    }
}
