import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/application_identity/domain/client_build_identity.dart';
import 'package:weave/core/application_identity/presentation/providers/client_build_identity_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_form_controller.dart';
import 'package:weave/features/settings/presentation/widgets/support_diagnostics_card.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/server_config_test_data.dart';

void main() {
  testWidgets('shows installed candidate and support-safe server identity', (
    tester,
  ) async {
    final container = ProviderContainer.test(
      overrides: [
        clientBuildIdentityProvider.overrideWith(
          (ref) async => const ClientBuildIdentity(
            candidateCommit: 'abc1234def5678',
            version: '0.1.0',
            buildNumber: '1042',
            bundleIdentifier: 'com.massimotter.weave',
            evidenceReference: 'dogfood/1042/manifest-v1',
          ),
        ),
        savedServerConfigurationProvider.overrideWith(
          (ref) async => buildTestConfiguration(
            backendApiBaseUrl:
                'https://member:unsafe@api.home.internal/api?token=unsafe',
          ),
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
          home: Scaffold(body: SupportDiagnosticsCard()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Support diagnostics'), findsOneWidget);
    expect(find.text('https://api.home.internal'), findsOneWidget);
    expect(find.text('abc1234def5678'), findsOneWidget);
    expect(find.text('0.1.0 (1042)'), findsOneWidget);
    expect(find.text('com.massimotter.weave'), findsOneWidget);
    expect(find.text('dogfood/1042/manifest-v1'), findsOneWidget);
    expect(find.text('Candidate identity is complete'), findsOneWidget);
    expect(find.textContaining('unsafe'), findsNothing);
    expect(
      find.bySemanticsLabel(
        'Candidate commit: abc1234def5678',
        skipOffstage: false,
      ),
      findsOneWidget,
    );
  });
}
