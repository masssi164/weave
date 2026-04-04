import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/bootstrap/presentation/providers/app_bootstrap_provider.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_security_repository_provider.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/features/settings/presentation/settings_screen.dart';
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
      expect(
        find.text(
          'Weave brings messaging, files, and calendar into one workspace while this screen manages the server connection behind it.',
        ),
        findsOneWidget,
      );
      expect(find.text('Workspace Readiness'), findsOneWidget);
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
      expect(find.text('Server Configuration'), findsOneWidget);
      expect(find.text('https://auth.home.internal'), findsWidgets);

      await tester.enterText(
        _textFieldWithLabel('Files Base URL'),
        'https://nextcloud-alt.home.internal',
      );
      await tester.pump();
      expect(find.text('https://nextcloud-alt.home.internal'), findsWidgets);

      expect(
        container
            .read(serverConfigurationFormControllerProvider)
            .nextcloudBaseUrl,
        'https://nextcloud-alt.home.internal',
      );

      await container
          .read(serverConfigurationFormControllerProvider.notifier)
          .save();
      await tester.pumpAndSettle();

      final raw = store.rawString(serverConfigurationStorageKey);
      final json = jsonDecode(raw!) as Map<String, dynamic>;

      expect(json['nextcloudBaseUrl'], 'https://nextcloud-alt.home.internal');
    });

    testWidgets('preserves overridden service URLs when the issuer changes', (
      tester,
    ) async {
      final store = InMemoryPreferencesStore(
        buildStoredConfiguration(
          nextcloudBaseUrl: 'https://cloud.custom.internal',
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
