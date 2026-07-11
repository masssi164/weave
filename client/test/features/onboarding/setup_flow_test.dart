import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/onboarding/presentation/setup_flow.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() {
  Widget buildApp() {
    final router = GoRouter(
      initialLocation: AppRoutes.welcome,
      routes: [
        GoRoute(path: AppRoutes.welcome, builder: (_, __) => const SetupFlow()),
        GoRoute(
          path: AppRoutes.join,
          builder: (_, state) => Scaffold(body: Text('resolved:${state.uri}')),
        ),
      ],
    );
    return MaterialApp.router(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routerConfig: router,
    );
  }

  testWidgets(
    'offers one member organization-access flow without operator fields',
    (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      expect(find.text('Organization access'), findsOneWidget);
      expect(
        find.text('Server URI, invitation link, or QR payload'),
        findsOneWidget,
      );
      expect(find.text('Continue to organization'), findsOneWidget);
      expect(find.bySemanticsLabel('Weave logo'), findsOneWidget);
      expect(
        find.textContaining('next screen always offers Sign in'),
        findsOneWidget,
      );
      for (final leakedTerm in [
        'Operator',
        'OIDC Issuer URL',
        'OIDC Client ID',
        'Nextcloud Base URL',
        'Provider categories',
      ]) {
        expect(find.textContaining(leakedTerm), findsNothing);
      }
    },
  );

  testWidgets('manual server URI converges on the join handoff route', (
    tester,
  ) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'https://weave.example');
    await tester.tap(find.text('Continue to organization'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('resolved:https://weave.example/join?'),
      findsOneWidget,
    );
    expect(find.textContaining('workspace=default'), findsOneWidget);
  });

  testWidgets('meets accessible tap-target and labeling guidelines', (
    tester,
  ) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();

    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  });

  testWidgets('pasted email or QR join payload is preserved', (tester) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();
    const link =
        'https://weave.example/join?handoff_ref=invite-123&org=acme&workspace=home&profile=organization-access&run_id=email-123';

    await tester.enterText(find.byType(TextField), link);
    await tester.tap(find.text('Continue to organization'));
    await tester.pumpAndSettle();

    expect(find.textContaining('handoff_ref=invite-123'), findsOneWidget);
  });

  testWidgets('rejects insecure or credential-bearing input accessibly', (
    tester,
  ) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byType(TextField),
      'http://user:secret@weave.example',
    );
    await tester.tap(find.text('Continue to organization'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Enter a secure Weave server URI'),
      findsOneWidget,
    );
  });
}
