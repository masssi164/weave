import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_connection_state.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/home/presentation/home_recent_activity.dart';
import 'package:weave/features/shell/presentation/shell_workspace_status.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';

import '../../helpers/test_app.dart';

WorkspaceCapabilityState ready(WorkspaceCapability capability) =>
    WorkspaceCapabilityState(
      capability: capability,
      readiness: WorkspaceCapabilityReadiness.ready,
    );
final capabilities = WorkspaceCapabilitySnapshot(
  shellAccess: ready(WorkspaceCapability.shellAccess),
  chat: ready(WorkspaceCapability.chat),
  files: ready(WorkspaceCapability.files),
  calendar: ready(WorkspaceCapability.calendar),
  boards: ready(WorkspaceCapability.boards),
);
const connection = WorkspaceConnectionState(
  appAuth: IntegrationConnectionState(
    integration: WorkspaceIntegration.appAuth,
    status: IntegrationConnectionStatus.connected,
  ),
  chat: IntegrationConnectionState(
    integration: WorkspaceIntegration.chat,
    status: IntegrationConnectionStatus.connected,
  ),
  files: IntegrationConnectionState(
    integration: WorkspaceIntegration.files,
    status: IntegrationConnectionStatus.connected,
  ),
);

void main() {
  testWidgets(
    'unknown Home count is omitted while measured zero remains visible',
    (tester) async {
      const home = WorkspaceHomeSnapshot(
        version: 3,
        readiness: WorkspaceCapabilityReadiness.ready,
        summary: 'Your workspace capabilities are available.',
        sections: [
          WorkspaceHomeSection(
            key: 'recent-channels',
            title: 'Recent channels',
            readiness: WorkspaceCapabilityReadiness.ready,
            summary: 'Conversations are available.',
            itemCount: null,
            accessible: true,
            productRoute: 'weave://home/channels',
          ),
          WorkspaceHomeSection(
            key: 'open-tasks',
            title: 'Open tasks',
            readiness: WorkspaceCapabilityReadiness.ready,
            summary: 'No tasks need your attention.',
            itemCount: 0,
            accessible: true,
            productRoute: 'weave://home/tasks',
          ),
        ],
        actions: [],
        recentActivity: [],
        supportSafe: true,
      );
      await tester.pumpWidget(
        createTestApp(
          const SingleChildScrollView(child: ShellWorkspaceStatus()),
          overrides: [
            workspaceConnectionStateProvider.overrideWith(
              (ref) => const AsyncData(connection),
            ),
            workspaceCapabilitySnapshotProvider.overrideWithValue(
              AsyncData(capabilities),
            ),
            weaveApiWorkspaceHomeProvider.overrideWith((ref) async => home),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Recent channels: Available'), findsOneWidget);
      expect(find.text('Open tasks (0): Available'), findsOneWidget);
      expect(find.textContaining('Recent channels (0)'), findsNothing);
      expect(find.textContaining('(null)'), findsNothing);
      expect(
        find.bySemanticsLabel(
          RegExp('Home.*Your workspace capabilities.*Recent channels'),
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'Home failure shows localized member recovery without raw diagnostics',
    (tester) async {
      final response = Completer<WorkspaceHomeSnapshot?>();
      await tester.pumpWidget(
        createTestApp(
          const HomeRecentActivity(),
          overrides: [
            weaveApiWorkspaceHomeProvider.overrideWith(
              (ref) => response.future,
            ),
          ],
        ),
      );
      await tester.pump();
      response.completeError(
        const AppFailure.unknown(
          'Configure provider password at https://private.example.test with Bearer unsafe-token',
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));
      await tester.pumpAndSettle();
      expect(
        find.text('Recent workspace activity is unavailable right now.'),
        findsOneWidget,
      );
      expect(find.text('Retry'), findsOneWidget);
      expect(find.textContaining('password'), findsNothing);
      expect(find.textContaining('private.example.test'), findsNothing);
      expect(find.textContaining('unsafe-token'), findsNothing);
    },
  );
}
