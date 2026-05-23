import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/config/feature_flags.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/theme/shared_preferences_app_theme_preference_repository.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
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

AsyncValue<WorkspaceConnectionState> _workspaceConnectionState() {
  return const AsyncData(
    WorkspaceConnectionState(
      appAuth: IntegrationConnectionState(
        integration: WorkspaceIntegration.appAuth,
        status: IntegrationConnectionStatus.connected,
      ),
      matrix: IntegrationConnectionState(
        integration: WorkspaceIntegration.matrix,
        status: IntegrationConnectionStatus.degraded,
        recoveryRequirement: IntegrationRecoveryRequirement.completeSetup,
        lastInvalidation: IntegrationInvalidation(
          integration: WorkspaceIntegration.matrix,
          reason: IntegrationInvalidationReason.matrixHomeserverChanged,
          sequence: 1,
        ),
      ),
      nextcloud: IntegrationConnectionState(
        integration: WorkspaceIntegration.nextcloud,
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
    testWidgets('loads the saved configuration and persists edits', (
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
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(WeaveLogo), findsOneWidget);
      expect(find.text('Appearance'), findsOneWidget);
      expect(find.text('Use device setting'), findsOneWidget);
      expect(find.text('Dark'), findsOneWidget);

      await tester.drag(find.byType(CustomScrollView), const Offset(0, -260));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Dark'));
      await tester.pump();

      expect(store.rawString(appThemePreferenceStorageKey), 'dark');
      expect(
        find.text(
          'Weave focuses on accessible, data-sovereign collaboration: chat, files, shared calendars, E2EE architecture, and boards behind clear gates.',
        ),
        findsOneWidget,
      );
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
        find.text('Last change: Matrix homeserver changed', findRichText: true),
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
      await tester.scrollUntilVisible(
        find.text('AI agent capability governance'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
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

      await tester.scrollUntilVisible(
        find.text('Help and user handbook'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Help and user handbook'), findsOneWidget);
      expect(
        find.text(
          'Open practical guidance for using Weave, recovering from issues, and understanding privacy basics.',
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

      await tester.scrollUntilVisible(
        find.text('AI agent capability governance'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('AI agent capability governance'), findsOneWidget);
      expect(
        find.textContaining('AI agent chats are not enabled'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Ask a workspace owner or admin'),
        findsOneWidget,
      );

      await tester.scrollUntilVisible(
        find.text('Workspace setup is admin-only'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Workspace setup is admin-only'), findsOneWidget);
      expect(
        find.textContaining('Normal users can keep using Weave'),
        findsOneWidget,
      );
      expect(find.text('Server Configuration'), findsNothing);
      expect(_textFieldWithLabel('OIDC Issuer URL'), findsNothing);
      expect(_textFieldWithLabel('Nextcloud Base URL'), findsNothing);
      expect(find.text('Provider stack readiness'), findsNothing);
      expect(find.text('Office readiness'), findsNothing);
      expect(find.text('Identity realm: unconfigured'), findsNothing);
      expect(find.text('Meetings: unconfigured'), findsNothing);
      expect(find.textContaining('Flutter provider calls'), findsNothing);
      expect(
        find.textContaining('Flutter does not call Nextcloud'),
        findsNothing,
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
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Workspace Readiness'), findsOneWidget);
      expect(find.text('Provider stack readiness'), findsNothing);
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
            home: Scaffold(body: SettingsScreen()),
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

      expect(find.text('Shell modules'), findsOneWidget);
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
            home: Scaffold(body: SettingsScreen()),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Provider stack readiness'), findsOneWidget);
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
            home: Scaffold(body: SettingsScreen()),
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
            matrixIntegrationConnectionProvider.overrideWith(
              (ref) async => const IntegrationConnectionState(
                integration: WorkspaceIntegration.matrix,
                status: IntegrationConnectionStatus.connected,
              ),
            ),
            nextcloudIntegrationConnectionProvider.overrideWith(
              (ref) async => const IntegrationConnectionState(
                integration: WorkspaceIntegration.nextcloud,
                status: IntegrationConnectionStatus.connected,
              ),
            ),
            chatSecurityRepositoryProvider.overrideWithValue(
              FakeChatSecurityRepository(),
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

        expect(find.text('Retry'), findsOneWidget);
        expect(
          find.text('Shell access and the mapped services are ready.'),
          findsNothing,
        );

        await tester.drag(find.byType(CustomScrollView), const Offset(0, -760));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Retry'));
        await tester.pumpAndSettle();

        expect(bootstrap.retryCalls, 1);
        expect(
          find.text('Shell access and the mapped services are ready.'),
          findsOneWidget,
        );
      },
    );
  });
}
