import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/presentation/workspace_setup_action.dart';
import 'package:weave/features/app/presentation/providers/workspace_connection_provider.dart';
import 'package:weave/features/files/presentation/files_screen.dart';
import 'package:weave/features/calendar/presentation/calendar_screen.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_api_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../../helpers/server_config_test_data.dart';

const missingSpace = WorkspaceCapabilityState(
  capability: WorkspaceCapability.files,
  readiness: WorkspaceCapabilityReadiness.blocked,
  policyState: WorkspaceCapabilityPolicyState.policyBlocked,
  memberSpaceAccessUnavailable: true,
);
const deniedPolicy = WorkspaceCapabilityState(
  capability: WorkspaceCapability.files,
  readiness: WorkspaceCapabilityReadiness.blocked,
  policyState: WorkspaceCapabilityPolicyState.policyBlocked,
);

WorkspaceCapabilitySnapshot backend({bool admin = true}) =>
    WorkspaceCapabilitySnapshot(
      shellAccess: deniedPolicy,
      chat: deniedPolicy,
      files: deniedPolicy,
      calendar: deniedPolicy,
      boards: deniedPolicy,
      adminControlPlane: WorkspaceCapabilityState(
        capability: WorkspaceCapability.adminControlPlane,
        readiness: admin
            ? WorkspaceCapabilityReadiness.ready
            : WorkspaceCapabilityReadiness.blocked,
        policyState: admin
            ? WorkspaceCapabilityPolicyState.allowed
            : WorkspaceCapabilityPolicyState.policyBlocked,
      ),
    );

Widget buildApp({
  required ServerConfiguration? configuration,
  required WorkspaceSetupLauncher launcher,
  bool admin = true,
  bool hasBackend = true,
  WorkspaceCapabilityState capability = missingSpace,
  String locale = 'en',
  double scale = 1,
  Widget? screen,
}) => ProviderScope(
  overrides: [
    weaveApiWorkspaceCapabilitySnapshotProvider.overrideWith(
      (ref) async => hasBackend ? backend(admin: admin) : null,
    ),
    savedServerConfigurationProvider.overrideWith((ref) async => configuration),
    workspaceSetupLauncherProvider.overrideWithValue(launcher),
    workspaceCapabilitySnapshotProvider.overrideWithValue(
      AsyncData(
        WorkspaceCapabilitySnapshot(
          shellAccess: deniedPolicy,
          chat: deniedPolicy,
          files: capability,
          calendar: capability,
          boards: deniedPolicy,
        ),
      ),
    ),
  ],
  child: MaterialApp(
    locale: Locale(locale),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(
      body: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child:
              screen ??
              Center(
                child: SizedBox(
                  width: 240,
                  child: WorkspaceSetupAction(capability: capability),
                ),
              ),
        ),
      ),
    ),
  ),
);

void main() {
  for (final locale in ['en', 'de']) {
    testWidgets(
      'opens canonical workspace setup externally, large text $locale',
      (tester) async {
        final semantics = tester.ensureSemantics();
        Uri? opened;
        LaunchMode? openedMode;
        await tester.pumpWidget(
          buildApp(
            configuration: buildTestConfiguration().copyWith(
              organizationOrigin: Uri.parse(
                'https://organization.example:8443/',
              ),
            ),
            locale: locale,
            scale: 2,
            launcher: (url, {required mode}) async {
              opened = url;
              openedMode = mode;
              return true;
            },
          ),
        );
        await tester.pumpAndSettle();
        final label = locale == 'de'
            ? 'Workspace-Einrichtung öffnen'
            : 'Open workspace setup';
        expect(find.bySemanticsLabel(label), findsOneWidget);
        final button = find.widgetWithText(OutlinedButton, label);
        expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
        await tester.tap(button);
        await tester.pumpAndSettle();
        expect(
          opened,
          Uri.parse('https://organization.example:8443/admin-console/'),
        );
        expect(openedMode, LaunchMode.externalApplication);
        expect(tester.takeException(), isNull);
        semantics.dispose();
      },
    );
  }

  for (final screen in [const FilesScreen(), const CalendarScreen()]) {
    testWidgets('${screen.runtimeType} offers setup alongside retry', (
      tester,
    ) async {
      Uri? opened;
      await tester.pumpWidget(
        buildApp(
          configuration: buildTestConfiguration(),
          screen: screen,
          launcher: (url, {required mode}) async {
            opened = url;
            return true;
          },
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Retry'), findsOneWidget);
      expect(find.text('Open workspace setup'), findsOneWidget);
      await tester.ensureVisible(find.text('Open workspace setup'));
      await tester.tap(find.text('Open workspace setup'));
      await tester.pumpAndSettle();
      expect(opened, Uri.parse('https://weave.home.internal/admin-console/'));
    });
  }

  final cases = <String, Widget>{};
  Widget hidden({
    ServerConfiguration? configuration,
    bool admin = true,
    bool hasBackend = true,
    WorkspaceCapabilityState capability = missingSpace,
  }) => buildApp(
    configuration: configuration,
    admin: admin,
    hasBackend: hasBackend,
    capability: capability,
    launcher: (_, {required mode}) async => throw StateError('Must not launch'),
  );
  cases['member'] = hidden(
    configuration: buildTestConfiguration(),
    admin: false,
  );
  cases['missing backend authority'] = hidden(
    configuration: buildTestConfiguration(),
    hasBackend: false,
  );
  cases['policy denial'] = hidden(
    configuration: buildTestConfiguration(),
    capability: deniedPolicy,
  );
  cases['missing configuration'] = hidden();
  cases['unvalidated configuration'] = hidden(
    configuration: buildTestConfiguration(withManifest: false),
  );
  for (final origin in [
    'http://organization.example',
    'https://user:secret@organization.example',
    'https://organization.example/private',
    'https://organization.example/?token=secret',
  ]) {
    cases[origin] = hidden(
      configuration: buildTestConfiguration().copyWith(
        organizationOrigin: Uri.parse(origin),
      ),
    );
  }
  for (final entry in cases.entries) {
    testWidgets('does not offer setup for ${entry.key}', (tester) async {
      await tester.pumpWidget(entry.value);
      await tester.pumpAndSettle();
      expect(find.byType(OutlinedButton), findsNothing);
    });
  }

  for (final throws in [false, true]) {
    testWidgets('explains failed external launch, throws=$throws', (
      tester,
    ) async {
      await tester.pumpWidget(
        buildApp(
          configuration: buildTestConfiguration(),
          launcher: (_, {required mode}) async {
            if (throws) throw StateError('Private platform details');
            return false;
          },
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Open workspace setup'));
      await tester.pump();
      expect(
        find.text('Workspace setup could not open. Try again.'),
        findsOneWidget,
      );
      expect(find.textContaining('Private platform details'), findsNothing);
    });
  }
}
