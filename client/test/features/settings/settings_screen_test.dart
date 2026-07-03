import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/config/feature_flags.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/l10n/shared_preferences_app_locale_preference_repository.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/domain/repositories/user_profile_repository.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/profile/presentation/widgets/profile_summary_card.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
import 'package:weave/features/shell/data/repositories/shared_preferences_shell_module_preferences_repository.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/fake_chat_security_repository.dart';
import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _RetryableAppBootstrap extends AppBootstrap {
  int retryCalls = 0;
  bool shouldFail = true;

  @override
  Future<BootstrapState> build() async {
    if (shouldFail) {
      throw const AppFailure.bootstrap('Temporary bootstrap failure.');
    }

    return const BootstrapState.ready();
  }

  @override
  Future<void> retry() async {
    retryCalls++;
    shouldFail = false;
    state = const AsyncLoading();
    state = const AsyncData(BootstrapState.ready());
  }
}

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

class _FakeUserProfileRepository implements UserProfileRepository {
  _FakeUserProfileRepository(this.profile);

  UserProfile profile;
  UserProfileUpdate? lastUpdate;

  @override
  Future<UserProfile?> loadProfile() async => profile;

  @override
  Future<UserProfile> updateProfile(UserProfileUpdate update) async {
    lastUpdate = update;
    profile = UserProfile(
      userId: profile.userId,
      username: profile.username,
      displayName: update.displayName ?? profile.displayName,
      locale: update.locale ?? profile.locale,
      timezone: update.timezone ?? profile.timezone,
      email: profile.email,
      emailVerified: profile.emailVerified,
      roles: profile.roles,
      groups: profile.groups,
    );
    return profile;
  }
}

AsyncValue<WorkspaceConnectionState> _workspaceConnectionState() {
  return const AsyncData(
    WorkspaceConnectionState(
      appAuth: IntegrationConnectionState(
        integration: WorkspaceIntegration.appAuth,
        status: IntegrationConnectionStatus.connected,
      ),
      chat: IntegrationConnectionState(
        integration: WorkspaceIntegration.chat,
        status: IntegrationConnectionStatus.degraded,
        recoveryRequirement: IntegrationRecoveryRequirement.completeSetup,
        lastInvalidation: IntegrationInvalidation(
          integration: WorkspaceIntegration.chat,
          reason: IntegrationInvalidationReason.chatConfigurationChanged,
          sequence: 1,
        ),
      ),
      files: IntegrationConnectionState(
        integration: WorkspaceIntegration.files,
        status: IntegrationConnectionStatus.connected,
      ),
    ),
  );
}

const _matrixDiagnostic = MatrixE2eeDiagnostic(
  e2eeEnabled: false,
  status: 'not_validated',
  serverReadableMessageContent: false,
  messageContentPolicy: 'encrypted_message_bodies_are_client_readable_only',
  agentParticipation:
      'blocked_until_explicit_consent_audit_and_matrix_device_trust_are_implemented',
  connectorWritePolicy:
      'fail_closed_until_audit_consent_and_matrix_e2ee_client_identity_are_implemented',
);

const _ownerProfile = UserProfile(
  userId: 'owner-1',
  username: 'owner',
  displayName: 'Workspace Owner',
  locale: 'en',
  timezone: 'Europe/Berlin',
  emailVerified: true,
  roles: ['owner'],
  groups: ['workspace-default'],
);

const _memberProfile = UserProfile(
  userId: 'member-1',
  username: 'member',
  displayName: 'Workspace Member',
  locale: 'en',
  timezone: 'Europe/Berlin',
  emailVerified: true,
  roles: ['member'],
  groups: ['workspace-default'],
);

AsyncValue<WorkspaceCapabilitySnapshot> _workspaceCapabilitySnapshot() {
  return const AsyncData(
    WorkspaceCapabilitySnapshot(
      shellAccess: WorkspaceCapabilityState(
        capability: WorkspaceCapability.shellAccess,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
      ),
      chat: WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.degraded,
        connectionStatus: IntegrationConnectionStatus.degraded,
        recoveryRequirement: IntegrationRecoveryRequirement.completeSetup,
        memberImpact: 'RAW BACKEND MEMBER IMPACT MUST NOT DISPLAY',
      ),
      files: WorkspaceCapabilityState(
        capability: WorkspaceCapability.files,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
      ),
      calendar: WorkspaceCapabilityState(
        capability: WorkspaceCapability.calendar,
        readiness: WorkspaceCapabilityReadiness.unavailable,
      ),
      boards: WorkspaceCapabilityState(
        capability: WorkspaceCapability.boards,
        readiness: WorkspaceCapabilityReadiness.unavailable,
      ),
    ),
  );
}

AsyncValue<WorkspaceCapabilitySnapshot>
_workspaceCapabilitySnapshotWithWeaver() {
  return const AsyncData(
    WorkspaceCapabilitySnapshot(
      shellAccess: WorkspaceCapabilityState(
        capability: WorkspaceCapability.shellAccess,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
        policyState: WorkspaceCapabilityPolicyState.allowed,
      ),
      chat: WorkspaceCapabilityState(
        capability: WorkspaceCapability.chat,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
        policyState: WorkspaceCapabilityPolicyState.allowed,
      ),
      files: WorkspaceCapabilityState(
        capability: WorkspaceCapability.files,
        readiness: WorkspaceCapabilityReadiness.ready,
        connectionStatus: IntegrationConnectionStatus.connected,
        policyState: WorkspaceCapabilityPolicyState.allowed,
      ),
      calendar: WorkspaceCapabilityState(
        capability: WorkspaceCapability.calendar,
        readiness: WorkspaceCapabilityReadiness.unavailable,
      ),
      boards: WorkspaceCapabilityState(
        capability: WorkspaceCapability.boards,
        readiness: WorkspaceCapabilityReadiness.unavailable,
      ),
      weaver: WorkspaceCapabilityState(
        capability: WorkspaceCapability.weaver,
        readiness: WorkspaceCapabilityReadiness.ready,
        policyState: WorkspaceCapabilityPolicyState.allowed,
        memberImpact: 'Mein Weaver follows workspace-approved choices.',
        grantedCapabilities: [
          'weaver.enabled',
          'weaver.model_alias.fast_local',
          'weaver.model_alias.careful_cloud',
          'weaver.configure_style',
          'weaver.configure_memory',
          'weaver.skill.summarize_notes',
          'weaver.personal_connection.calendar_import',
        ],
      ),
    ),
  );
}

ProviderStatusSnapshot _providerStatus({
  required String module,
  required String providerKey,
  ProviderState state = ProviderState.notConfigured,
  bool enabled = false,
  bool configured = false,
  bool readOnly = true,
}) {
  return ProviderStatusSnapshot(
    module: module,
    providerKey: providerKey,
    state: state,
    readiness: state == ProviderState.ready ? 'ready' : 'fail-closed',
    enabled: enabled,
    configured: configured,
    readOnly: readOnly,
    failClosed: state != ProviderState.ready,
    supportSafe: true,
    paidFeaturesRequired: false,
    summary: 'Support-safe readiness for $providerKey.',
    supportedCapabilities: const [],
    unsupportedOperations: state == ProviderState.ready
        ? const []
        : const ['runtime-action'],
    supportSafeErrorCodes: state == ProviderState.ready
        ? const []
        : const ['PROVIDER_NOT_CONFIGURED'],
    redactionPolicy: 'support-safe',
    candidates: const [],
  );
}

void main() {
  group('SettingsScreen', () {
    testWidgets('workspace health loads the saved configuration and persists edits', (
      tester,
    ) async {
      final store = InMemoryPreferencesStore(buildStoredConfiguration());
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => store),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          userProfileProvider.overrideWith((ref) async => _ownerProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: WorkspaceHealthScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Workspace Health'), findsWidgets);
      expect(find.text('Workspace Readiness'), findsOneWidget);
      expect(FeatureFlags.hasFeatureGatedSurfaces, isFalse);
      expect(find.text('Preview surfaces'), findsNothing);
      expect(find.text('Guest Portal'), findsNothing);
      expect(
        find.text(
          'Shell access is ready, but one or more services still need attention.',
        ),
        findsOneWidget,
      );
      expect(
        find.text('RAW BACKEND MEMBER IMPACT MUST NOT DISPLAY'),
        findsNothing,
      );
      expect(
        find.text(
          'Recovery: You can keep working, but some actions may be limited until workspace readiness recovers.',
          findRichText: true,
        ),
        findsOneWidget,
      );
      expect(
        find.text(
          'Last change: Chat configuration changed',
          findRichText: true,
        ),
        findsOneWidget,
      );
      expect(
        find.text('E2EE gate: Not validated', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.text('Server-readable bodies: No', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.text('Agent writes: Blocked/fail-closed', findRichText: true),
        findsOneWidget,
      );
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -900));
      await tester.pumpAndSettle();
      expect(find.text('AI agent capability governance'), findsOneWidget);
      expect(
        find.textContaining('Owners and admins decide which agent packages'),
        findsOneWidget,
      );
      expect(find.text('Personal assistant'), findsOneWidget);
      expect(find.text('Channel agent'), findsOneWidget);
      expect(
        find.text('Management unavailable until admin setup is complete'),
        findsOneWidget,
      );

      await tester.drag(find.byType(CustomScrollView), const Offset(0, -900));
      await tester.pumpAndSettle();
      expect(find.text('Provider categories'), findsOneWidget);
      expect(find.text('Identity/IDM'), findsOneWidget);
      expect(find.text('Chat'), findsWidgets);
      expect(find.text('Files'), findsWidgets);
      expect(find.text('Calendar'), findsWidgets);
      expect(find.text('Boards/tasks'), findsOneWidget);
      expect(find.text('Meetings/calls'), findsOneWidget);
      expect(find.text('Documents/collaboration'), findsOneWidget);
      expect(find.text('Weaver'), findsOneWidget);
      expect(find.text('Disabled by default'), findsOneWidget);
      expect(find.textContaining('Keycloak/Auth'), findsOneWidget);
      expect(find.textContaining('Chat'), findsWidgets);
      expect(find.textContaining('File storage'), findsOneWidget);
      expect(find.textContaining('Calendar sync'), findsOneWidget);
      expect(
        find.textContaining('OpenProject Boards validation'),
        findsOneWidget,
      );
      expect(find.textContaining('LiveKit Meetings readiness'), findsOneWidget);
      expect(find.text('Embedded admin/operator manual'), findsOneWidget);
      expect(
        find.text('Manual source: docs/admin-operator-handbook.md'),
        findsOneWidget,
      );
      expect(
        find.text(
          'Constrained embed: no broad script, camera, microphone, or provider access',
        ),
        findsOneWidget,
      );
      expect(find.text('Server Configuration'), findsOneWidget);
      expect(find.text('https://auth.home.internal'), findsWidgets);
      expect(find.text('weave-app'), findsWidgets);
      expect(find.text('https://api.home.internal/api'), findsWidgets);

      await tester.enterText(
        _textFieldWithLabel('Nextcloud Base URL'),
        'https://files-alt.home.internal',
      );
      await tester.pump();
      expect(find.text('https://files-alt.home.internal'), findsWidgets);

      expect(
        container
            .read(serverConfigurationFormControllerProvider)
            .nextcloudBaseUrl,
        'https://files-alt.home.internal',
      );

      await container
          .read(serverConfigurationFormControllerProvider.notifier)
          .save();
      await tester.pumpAndSettle();

      final raw = store.rawString(serverConfigurationStorageKey);
      final json = jsonDecode(raw!) as Map<String, dynamic>;

      expect(json['nextcloudBaseUrl'], 'https://files-alt.home.internal');
      expect(json['backendApiBaseUrl'], 'https://api.home.internal/api');
    });

    testWidgets('hides OIDC and service endpoint setup from members', (
      tester,
    ) async {
      final store = InMemoryPreferencesStore(buildStoredConfiguration());
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => store),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          userProfileProvider.overrideWith((ref) async => _memberProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Settings sections'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'Appearance'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'Language'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'Profile'), findsOneWidget);
      expect(find.widgetWithText(ActionChip, 'Shell modules'), findsOneWidget);
      expect(find.text('Appearance'), findsWidgets);
      expect(find.text('Language'), findsWidgets);
      expect(find.text('Profile'), findsWidgets);
      expect(
        find.textContaining('Open profile details and editing'),
        findsOneWidget,
      );
      expect(find.text('Weave profile'), findsNothing);
      expect(find.text('Edit profile'), findsNothing);
      expect(find.text('Save profile'), findsNothing);
      expect(find.text('Help and user handbook'), findsOneWidget);
      expect(find.text('Shell modules'), findsWidgets);
      expect(find.text('Workspace Health'), findsNothing);
      expect(find.text('AI agent capability governance'), findsNothing);
      expect(
        find.textContaining('AI agent chats are not enabled'),
        findsNothing,
      );
      expect(
        find.textContaining('Ask a workspace owner or admin'),
        findsNothing,
      );
      expect(find.text('Workspace setup is admin-only'), findsNothing);
      expect(
        find.textContaining('Normal users can keep using Weave'),
        findsNothing,
      );
      expect(find.text('Workspace Readiness'), findsNothing);
      expect(find.text('Server Configuration'), findsNothing);
      expect(find.text('Provider categories'), findsNothing);
      expect(find.text('Identity/IDM'), findsNothing);
      expect(find.text('Documents/collaboration'), findsNothing);
      expect(_textFieldWithLabel('OIDC Issuer URL'), findsNothing);
      expect(_textFieldWithLabel('Nextcloud Base URL'), findsNothing);
      expect(find.text('Provider stack readiness'), findsNothing);
      expect(find.text('Office readiness'), findsNothing);
      expect(find.text('Embedded admin/operator manual'), findsNothing);
      expect(find.text('Identity realm: unconfigured'), findsNothing);
      expect(find.text('Meetings: unconfigured'), findsNothing);
      expect(find.textContaining('Flutter provider calls'), findsNothing);
      expect(
        find.textContaining('Flutter does not call Nextcloud'),
        findsNothing,
      );
    });

    testWidgets(
      'owner settings link to workspace health without mounting diagnostics',
      (tester) async {
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
            ),
            userProfileProvider.overrideWith((ref) async => _ownerProfile),
          ],
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(body: SettingsScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Settings sections'), findsOneWidget);
        expect(find.widgetWithText(ActionChip, 'Profile'), findsOneWidget);
        expect(
          find.widgetWithText(ActionChip, 'Workspace Health'),
          findsOneWidget,
        );
        expect(find.text('Profile'), findsWidgets);
        expect(find.text('Edit profile'), findsNothing);
        expect(find.text('Save profile'), findsNothing);
        expect(find.text('Workspace Health'), findsWidgets);
        expect(
          find.textContaining('Open admin setup, provider readiness'),
          findsOneWidget,
        );
        expect(find.text('Workspace Readiness'), findsNothing);
        expect(find.text('Provider stack readiness'), findsNothing);
        expect(find.text('Server Configuration'), findsNothing);
        expect(_textFieldWithLabel('OIDC Issuer URL'), findsNothing);
      },
    );

    testWidgets(
      'settings section shortcuts stay member-bounded and language persists',
      (tester) async {
        final store = InMemoryPreferencesStore(buildStoredConfiguration());
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith((ref) => store),
            userProfileProvider.overrideWith((ref) async => _memberProfile),
          ],
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(body: SettingsScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Settings sections'), findsOneWidget);
        expect(
          find.widgetWithText(ActionChip, 'Workspace Health'),
          findsNothing,
        );
        expect(
          find.widgetWithText(ActionChip, 'Shell modules'),
          findsOneWidget,
        );

        await tester.tap(find.widgetWithText(ActionChip, 'Language'));
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('German'));
        await tester.tap(find.text('German'));
        await tester.pumpAndSettle();

        expect(store.rawString(appLocalePreferenceStorageKey), 'de');

        expect(find.text('Provider stack readiness'), findsNothing);
        expect(_textFieldWithLabel('OIDC Issuer URL'), findsNothing);
      },
    );

    testWidgets('uses a language picker instead of requiring locale codes', (
      tester,
    ) async {
      final profileRepository = _FakeUserProfileRepository(_memberProfile);
      final container = ProviderContainer.test(
        overrides: [
          userProfileProvider.overrideWith((ref) async => _memberProfile),
          userProfileRepositoryProvider.overrideWithValue(profileRepository),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: SingleChildScrollView(child: ProfileSummaryCard()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(_textFieldWithLabel('Locale'), findsNothing);
      expect(find.text('English'), findsWidgets);

      final languagePicker = find.byType(DropdownButtonFormField<String>);
      expect(languagePicker, findsOneWidget);
      await tester.scrollUntilVisible(
        languagePicker,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(languagePicker);
      await tester.pumpAndSettle();
      await tester.tap(find.text('German').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save profile'));
      await tester.pumpAndSettle();

      expect(profileRepository.lastUpdate?.locale, 'de');
      expect(find.text('Profile saved.'), findsOneWidget);
    });

    testWidgets(
      'shows governed Mein Weaver choices without raw runtime surfaces',
      (tester) async {
        final capabilities = _workspaceCapabilitySnapshotWithWeaver();
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
            ),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(),
            ),
            workspaceConnectionStateProvider.overrideWithValue(
              _workspaceConnectionState(),
            ),
            workspaceCapabilitySnapshotProvider.overrideWithValue(capabilities),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async => capabilities.requireValue,
            ),
            weaveBackendConnectionStateProvider.overrideWithValue(
              WeaveBackendConnectionState.connected,
            ),
            weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
              (ref) async => _matrixDiagnostic,
            ),
            userProfileProvider.overrideWith((ref) async => _memberProfile),
          ],
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(body: SettingsScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();

        await tester.scrollUntilVisible(
          find.text('Enabled by policy'),
          300,
          scrollable: find.byType(Scrollable).first,
        );
        expect(find.text('Mein Weaver'), findsWidgets);
        expect(find.text('Enabled by policy'), findsOneWidget);
        expect(find.text('Careful Cloud'), findsOneWidget);
        expect(find.text('Fast Local'), findsOneWidget);
        expect(find.text('Style preferences'), findsOneWidget);
        expect(find.text('Memory controls'), findsOneWidget);
        expect(find.text('Summarize Notes'), findsOneWidget);
        expect(find.text('Calendar Import'), findsOneWidget);
        expect(
          find.textContaining('members only see policy-approved choices'),
          findsOneWidget,
        );
        expect(find.textContaining('OpenClaw'), findsNothing);
        expect(find.textContaining('openclaw.json'), findsNothing);
        expect(find.textContaining('channel tokens'), findsNothing);
        expect(find.textContaining('provider secrets'), findsNothing);
        expect(find.textContaining('raw MCP'), findsNothing);
        expect(find.text('Server Configuration'), findsNothing);
      },
    );

    testWidgets('localizes unavailable Mein Weaver copy', (tester) async {
      tester.view.physicalSize = const Size(1200, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      const capabilities = AsyncData(
        WorkspaceCapabilitySnapshot(
          shellAccess: WorkspaceCapabilityState(
            capability: WorkspaceCapability.shellAccess,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
          ),
          chat: WorkspaceCapabilityState(
            capability: WorkspaceCapability.chat,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
          ),
          files: WorkspaceCapabilityState(
            capability: WorkspaceCapability.files,
            readiness: WorkspaceCapabilityReadiness.ready,
            policyState: WorkspaceCapabilityPolicyState.allowed,
          ),
          calendar: WorkspaceCapabilityState(
            capability: WorkspaceCapability.calendar,
            readiness: WorkspaceCapabilityReadiness.unavailable,
          ),
          boards: WorkspaceCapabilityState(
            capability: WorkspaceCapability.boards,
            readiness: WorkspaceCapabilityReadiness.unavailable,
          ),
          weaver: WorkspaceCapabilityState(
            capability: WorkspaceCapability.weaver,
            readiness: WorkspaceCapabilityReadiness.unavailable,
            policyState: WorkspaceCapabilityPolicyState.disabled,
            memberImpact: 'RAW WEAVER BACKEND MEMBER IMPACT',
          ),
        ),
      );
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
          ),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(capabilities),
          weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
            (ref) async => capabilities.requireValue,
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          userProfileProvider.overrideWith((ref) async => _memberProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Weaver unavailable'), findsOneWidget);
      expect(find.text('RAW WEAVER BACKEND MEMBER IMPACT'), findsNothing);
      expect(
        find.textContaining(
          'Your workspace has not enabled a governed Weaver profile',
        ),
        findsOneWidget,
      );
    });

    testWidgets('keeps provider diagnostics admin-only for members', (
      tester,
    ) async {
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
          ),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          weaveApiProviderStackSnapshotProvider.overrideWith(
            (ref) async => const ProviderStackSnapshot(
              releaseStatus: 'provider-readiness',
              backendOwnedFacades: true,
              flutterDirectProviderCallsAllowed: false,
              supportSafe: true,
              providers: [
                ProviderStatusSnapshot(
                  module: 'files',
                  providerKey: 'nextcloud-files',
                  state: ProviderState.ready,
                  readiness: 'ready',
                  enabled: true,
                  configured: true,
                  readOnly: false,
                  failClosed: false,
                  supportSafe: true,
                  paidFeaturesRequired: false,
                  summary: 'Operator-only provider readiness.',
                  supportedCapabilities: [],
                  unsupportedOperations: [],
                  supportSafeErrorCodes: [],
                  redactionPolicy: 'support-safe',
                  candidates: [],
                ),
              ],
            ),
          ),
          userProfileProvider.overrideWith((ref) async => _memberProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: WorkspaceHealthScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Workspace setup is admin-only'), findsOneWidget);
      expect(find.text('Workspace Readiness'), findsNothing);
      expect(find.text('Provider stack readiness'), findsNothing);
      expect(find.text('Admin/operator readiness cockpit'), findsNothing);
      expect(find.textContaining('nextcloud-files'), findsNothing);
      expect(find.textContaining('Flutter provider calls'), findsNothing);
    });

    testWidgets('preserves overridden service URLs when the issuer changes', (
      tester,
    ) async {
      final store = InMemoryPreferencesStore(
        buildStoredConfiguration(
          nextcloudBaseUrl: 'https://cloud.custom.internal',
          backendApiBaseUrl: 'https://backend.custom.internal',
        ),
      );
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => store),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          userProfileProvider.overrideWith((ref) async => _ownerProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: WorkspaceHealthScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(
        _textFieldWithLabel('OIDC Issuer URL'),
        'https://sso.example.com',
      );
      await tester.pumpAndSettle();

      expect(find.text('https://matrix.example.com'), findsWidgets);
      expect(find.text('https://cloud.custom.internal'), findsWidgets);
      expect(find.text('https://backend.custom.internal'), findsWidgets);
    });

    testWidgets('persists shell module visibility changes', (tester) async {
      final store = InMemoryPreferencesStore(buildStoredConfiguration());
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => store),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          userProfileProvider.overrideWith((ref) async => null),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Shell modules'), findsWidgets);
      final recentActivityToggle = find.text('Recent activity quick links');
      expect(recentActivityToggle, findsOneWidget);

      await tester.ensureVisible(recentActivityToggle);
      await tester.pumpAndSettle();
      await tester.tap(recentActivityToggle);
      await tester.pumpAndSettle();

      expect(
        store.rawString(shellModulePreferencesStorageKey),
        '{"hiddenModules":["workspaceStatus","recentActivity"],"moduleOrder":["workspaceStatus","recentActivity"]}',
      );

      await tester.tap(recentActivityToggle);
      await tester.pumpAndSettle();

      expect(
        store.rawString(shellModulePreferencesStorageKey),
        '{"hiddenModules":["workspaceStatus"],"moduleOrder":["workspaceStatus","recentActivity"]}',
      );

      await tester.ensureVisible(find.text('Workspace status summary'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Move Workspace status summary down'));
      await tester.pumpAndSettle();

      expect(
        store.rawString(shellModulePreferencesStorageKey),
        '{"hiddenModules":["workspaceStatus"],"moduleOrder":["recentActivity","workspaceStatus"]}',
      );
    });

    testWidgets('surfaces provider stack fail-closed readiness safely', (
      tester,
    ) async {
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
          ),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          weaveApiProviderStackSnapshotProvider.overrideWith(
            (ref) async => const ProviderStackSnapshot(
              releaseStatus: 'contract-preview',
              backendOwnedFacades: true,
              flutterDirectProviderCallsAllowed: false,
              supportSafe: true,
              categories: [
                ProviderCategoryStatusSnapshot(
                  category: 'calendar',
                  label: 'calendar',
                  readiness: ProviderCategoryReadiness.degraded,
                  policyState: 'allowed',
                  memberImpact: 'Calendar is degraded.',
                  modules: ['calendar'],
                  providerCandidates: ['nextcloud-calendar'],
                  adapterEvidence: [
                    ProviderAdapterReadinessEvidenceSnapshot(
                      domain: 'calendar',
                      adapterKey: 'nextcloud-caldav',
                      configured: false,
                      reachable: false,
                      health: 'admin_selected_pending_backend_configuration',
                      failClosed: true,
                      supportSafeDiagnostics: {
                        'secretsReturned': false,
                        'rawProviderErrorsReturned': false,
                      },
                    ),
                  ],
                  diagnostics: {
                    'secretsReturned': false,
                    'rawProviderErrorsReturned': false,
                  },
                ),
                ProviderCategoryStatusSnapshot(
                  category: 'weaver',
                  label: 'Weaver',
                  readiness: ProviderCategoryReadiness.policyBlocked,
                  policyState: 'policy_blocked',
                  memberImpact: 'Weaver is disabled by workspace policy.',
                  modules: [],
                  providerCandidates: [],
                  diagnostics: {
                    'secretsReturned': false,
                    'rawProviderErrorsReturned': false,
                  },
                ),
              ],
              providers: [
                ProviderStatusSnapshot(
                  module: 'office',
                  providerKey: 'onlyoffice-community',
                  state: ProviderState.disabled,
                  readiness: 'fail-closed',
                  enabled: false,
                  configured: false,
                  readOnly: true,
                  failClosed: true,
                  supportSafe: true,
                  paidFeaturesRequired: false,
                  summary: 'Disabled until configured behind backend facade.',
                  supportedCapabilities: [],
                  unsupportedOperations: ['launch'],
                  supportSafeErrorCodes: ['PROVIDER_DISABLED'],
                  redactionPolicy: 'support-safe',
                  candidates: ['ONLYOFFICE Docs Community'],
                ),
              ],
            ),
          ),
          weaveApiOfficeCapabilitiesSnapshotProvider.overrideWith(
            (ref) async => const OfficeCapabilitiesSnapshot(
              releaseStatus: 'contract-preview',
              enabled: false,
              configured: false,
              supportSafe: true,
              launchMode: 'disabled',
              defaultProvider: 'onlyoffice-community',
              providerReadiness: [],
              supportedFileTypes: [],
              candidates: [],
              capabilities: OfficeCapabilityFlagsSnapshot(
                view: false,
                edit: false,
                comment: false,
                review: false,
                formFill: false,
              ),
              permissions: OfficePermissionModelSnapshot(
                canView: false,
                canEdit: false,
                canComment: false,
                canReview: false,
                canFillForms: false,
                reason: 'token leaked from https://office.example.test',
              ),
              lockSessionReadiness: OfficeLockSessionReadinessSnapshot(
                documentLocks: 'unavailable',
                sessionTokens: 'unavailable',
                callbackVerification: 'unavailable',
                supportSafe: true,
              ),
            ),
          ),
          userProfileProvider.overrideWith((ref) async => _ownerProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: WorkspaceHealthScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Provider stack readiness'), findsOneWidget);
      expect(find.text('Admin/operator readiness cockpit'), findsOneWidget);
      expect(
        find.text('Overall posture: Admin action required', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.text(
          'Category health: 0 ready of 2; 2 need action',
          findRichText: true,
        ),
        findsOneWidget,
      );
      expect(
        find.text('Evidence: Support-safe and redacted', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.text(
          'Member boundary: Provider diagnostics hidden from normal members',
          findRichText: true,
        ),
        findsOneWidget,
      );
      expect(find.text('Next actions'), findsOneWidget);
      expect(
        find.textContaining('Configure endpoint mappings and SecretRefs'),
        findsWidgets,
      );
      expect(find.text('Category health and member impact'), findsOneWidget);
      expect(find.text('Member impact: Calendar is degraded.'), findsOneWidget);
      expect(find.text('Policy: allowed'), findsOneWidget);
      expect(find.textContaining('support-safe readiness only'), findsWidgets);
      expect(
        find.text('Flutter provider calls: Blocked', findRichText: true),
        findsOneWidget,
      );
      expect(
        find.textContaining('Flutter does not call Nextcloud'),
        findsOneWidget,
      );
      expect(find.text('Office readiness'), findsOneWidget);
      expect(find.text('unconfigured'), findsWidgets);
      expect(find.text('fail-closed'), findsWidgets);
      expect(
        find.textContaining('Office launch is fail-closed'),
        findsOneWidget,
      );
      expect(find.text('calendar: degraded'), findsOneWidget);
      expect(find.text('nextcloud-caldav: unconfigured'), findsOneWidget);
      expect(find.text('Unavailable'), findsWidgets);
      expect(find.text('Weaver: Blocked'), findsOneWidget);
      expect(find.textContaining('provider-token-123'), findsNothing);
      expect(find.textContaining('https://gitlab.example.test'), findsNothing);
      expect(find.textContaining('https://office.example.test'), findsNothing);
    });

    testWidgets('renders the full final provider product coverage list', (
      tester,
    ) async {
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
          ),
          chatSecurityRepositoryProvider.overrideWithValue(
            FakeChatSecurityRepository(),
          ),
          workspaceConnectionStateProvider.overrideWithValue(
            _workspaceConnectionState(),
          ),
          workspaceCapabilitySnapshotProvider.overrideWithValue(
            _workspaceCapabilitySnapshot(),
          ),
          weaveBackendConnectionStateProvider.overrideWithValue(
            WeaveBackendConnectionState.connected,
          ),
          weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
            (ref) async => _matrixDiagnostic,
          ),
          weaveApiProviderStackSnapshotProvider.overrideWith(
            (ref) async => ProviderStackSnapshot(
              releaseStatus: 'provider-final-coverage',
              backendOwnedFacades: true,
              flutterDirectProviderCallsAllowed: false,
              supportSafe: true,
              providers: [
                _providerStatus(
                  module: 'identity-realm',
                  providerKey: 'keycloak-realm',
                  state: ProviderState.ready,
                  enabled: true,
                  configured: true,
                  readOnly: false,
                ),
                _providerStatus(
                  module: 'files',
                  providerKey: 'nextcloud-files',
                  state: ProviderState.ready,
                  enabled: true,
                  configured: true,
                  readOnly: false,
                ),
                _providerStatus(
                  module: 'calendar',
                  providerKey: 'nextcloud-caldav',
                  state: ProviderState.ready,
                  enabled: true,
                  configured: true,
                  readOnly: false,
                ),
                _providerStatus(
                  module: 'contacts',
                  providerKey: 'nextcloud-carddav',
                ),
                _providerStatus(
                  module: 'forms',
                  providerKey: 'nextcloud-forms',
                ),
                _providerStatus(
                  module: 'matrix',
                  providerKey: 'synapse-homeserver',
                  state: ProviderState.ready,
                  enabled: true,
                  configured: true,
                  readOnly: false,
                ),
                _providerStatus(
                  module: 'matrix-auth',
                  providerKey: 'matrix-authentication-service',
                  state: ProviderState.ready,
                  enabled: true,
                  configured: true,
                  readOnly: false,
                ),
                _providerStatus(module: 'meetings', providerKey: 'livekit'),
                _providerStatus(
                  module: 'boards',
                  providerKey: 'openproject-primary',
                ),
              ],
            ),
          ),
          weaveApiOfficeCapabilitiesSnapshotProvider.overrideWith(
            (ref) async => const OfficeCapabilitiesSnapshot(
              releaseStatus: 'contract-preview',
              enabled: false,
              configured: false,
              supportSafe: true,
              launchMode: 'disabled',
              defaultProvider: 'onlyoffice-community',
              providerReadiness: [],
              supportedFileTypes: [],
              candidates: [],
              capabilities: OfficeCapabilityFlagsSnapshot(
                view: false,
                edit: false,
                comment: false,
                review: false,
                formFill: false,
              ),
              permissions: OfficePermissionModelSnapshot(
                canView: false,
                canEdit: false,
                canComment: false,
                canReview: false,
                canFillForms: false,
                reason: 'not-configured',
              ),
              lockSessionReadiness: OfficeLockSessionReadinessSnapshot(
                documentLocks: 'unavailable',
                sessionTokens: 'unavailable',
                callbackVerification: 'unavailable',
                supportSafe: true,
              ),
            ),
          ),
          userProfileProvider.overrideWith((ref) async => _ownerProfile),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(body: WorkspaceHealthScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Identity realm: ready'), findsOneWidget);
      expect(find.text('Files: ready'), findsOneWidget);
      expect(find.text('Calendar: ready'), findsOneWidget);
      expect(find.text('Contacts: unconfigured'), findsOneWidget);
      expect(find.text('Forms: unconfigured'), findsOneWidget);
      expect(find.text('Matrix chat: ready'), findsOneWidget);
      expect(find.text('Matrix auth: ready'), findsOneWidget);
      expect(find.text('Meetings: unconfigured'), findsOneWidget);
      expect(find.text('Boards: unconfigured'), findsOneWidget);
    });

    testWidgets(
      'workspace readiness retry rebuilds bootstrap after an async error',
      (tester) async {
        final bootstrap = _RetryableAppBootstrap();
        final container = ProviderContainer.test(
          overrides: [
            savedServerConfigurationProvider.overrideWith(
              (ref) async => buildTestConfiguration(),
            ),
            preferencesStoreProvider.overrideWith(
              (ref) => InMemoryPreferencesStore(),
            ),
            appBootstrapProvider.overrideWith(() => bootstrap),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(),
            ),
            weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
              (ref) async => _workspaceCapabilitySnapshot().requireValue,
            ),
            weaveBackendConnectionStateProvider.overrideWithValue(
              WeaveBackendConnectionState.connected,
            ),
            weaveApiMatrixE2eeDiagnosticProvider.overrideWith(
              (ref) async => _matrixDiagnostic,
            ),
            userProfileProvider.overrideWith((ref) async => _ownerProfile),
          ],
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: const MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(body: WorkspaceHealthScreen()),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Retry'), findsOneWidget);
        expect(
          find.text('Shell access and the mapped services are ready.'),
          findsNothing,
        );

        final retryButton = find.text('Retry');
        await tester.ensureVisible(retryButton);
        await tester.pumpAndSettle();
        await tester.tap(retryButton);
        await tester.pumpAndSettle();

        expect(bootstrap.retryCalls, 1);
      },
    );
  });
}
