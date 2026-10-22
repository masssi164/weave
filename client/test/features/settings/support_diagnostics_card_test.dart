import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:weave/features/settings/presentation/providers/support_identity_reference_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/application_identity/domain/client_build_identity.dart';
import 'package:weave/core/application_identity/presentation/providers/client_build_identity_provider.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/features/settings/presentation/widgets/support_diagnostics_card.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/server_config_test_data.dart';

void main() {
  testWidgets(
    'secondary diagnostics expose and copy only server identity references',
    (tester) async {
      final account = 'sha256:${List.filled(64, 'a').join()}';
      final organization = 'sha256:${List.filled(64, 'b').join()}';
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            supportIdentityReferenceProvider.overrideWith(
              (ref) async => SupportIdentityReferences(
                account: account,
                organization: organization,
              ),
            ),
            savedServerConfigurationProvider.overrideWith((ref) async => null),
          ],
          child: const MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: SingleChildScrollView(child: SupportDiagnosticsCard()),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      for (final id in [
        'settings-current-account-reference',
        'settings-current-organization-reference',
      ]) {
        final value = find.byWidgetPredicate(
          (w) => w is Semantics && w.properties.identifier == id,
        );
        expect(value, findsOneWidget);
        expect(
          RegExp(r'sha256:[0-9a-f]{64}')
              .allMatches(tester.widget<Semantics>(value).properties.label!)
              .length,
          1,
        );
      }
      await tester.ensureVisible(find.text('Copy identity references'));
      await tester.tap(find.text('Copy identity references'));
      expect(
        copied,
        'Account reference: $account\nOrganization reference: $organization',
      );
      expect(tester.takeException(), isNull);
    },
  );

  for (final phase in ['loading', 'unavailable', 'error']) {
    testWidgets(
      'identity references cannot establish continuity during $phase',
      (tester) async {
        final pending = Completer<SupportIdentityReferences?>();
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              supportIdentityReferenceProvider.overrideWith((ref) async {
                if (phase == 'loading') return pending.future;
                if (phase == 'error') {
                  throw StateError('support-safe-unavailable');
                }
                return null;
              }),
              savedServerConfigurationProvider.overrideWith(
                (ref) async => null,
              ),
            ],
            child: const MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(
                body: SingleChildScrollView(child: SupportDiagnosticsCard()),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        for (final id in [
          'settings-current-account-reference',
          'settings-current-organization-reference',
        ]) {
          final value = find.byWidgetPredicate(
            (w) => w is Semantics && w.properties.identifier == id,
          );
          expect(
            tester.widget<Semantics>(value).properties.label,
            isNot(contains('sha256:')),
          );
        }
        final copy = find.widgetWithText(
          TextButton,
          'Copy identity references',
        );
        expect(tester.widget<TextButton>(copy).onPressed, isNull);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('shows installed candidate and support-safe server identity', (
    tester,
  ) async {
    final container = ProviderContainer.test(
      overrides: [
        clientBuildIdentityProvider.overrideWith(
          (ref) async => ClientBuildIdentity.supportSafe(
            candidateCommit: 'abc1234def5678abc1234def5678abc1234def56',
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
          home: Scaffold(
            body: SingleChildScrollView(child: SupportDiagnosticsCard()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Support diagnostics'), findsOneWidget);
    expect(find.text('https://api.home.internal'), findsOneWidget);
    expect(
      find.text('abc1234def5678abc1234def5678abc1234def56'),
      findsOneWidget,
    );
    expect(find.text('0.1.0 (1042)'), findsOneWidget);
    expect(find.text('com.massimotter.weave'), findsOneWidget);
    expect(find.text('dogfood/1042/manifest-v1'), findsOneWidget);
    expect(find.text('Candidate identity is complete'), findsOneWidget);
    expect(find.textContaining('unsafe'), findsNothing);
    expect(
      find.bySemanticsLabel(
        'Candidate commit: abc1234def5678abc1234def5678abc1234def56',
        skipOffstage: false,
      ),
      findsOneWidget,
    );
  });
}
