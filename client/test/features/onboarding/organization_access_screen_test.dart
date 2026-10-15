import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/onboarding/presentation/organization_access_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

void main() {
  Widget buildApp({Locale? locale, double scale = 1}) {
    final router = GoRouter(
      initialLocation: AppRoutes.organizationAccess,
      routes: [
        GoRoute(
          path: AppRoutes.organizationAccess,
          builder: (_, __) => const OrganizationAccessScreen(),
        ),
        GoRoute(
          path: AppRoutes.join,
          builder: (_, state) => Scaffold(body: Text('resolved:${state.uri}')),
        ),
      ],
    );
    return MaterialApp.router(
      locale: locale,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(scale)),
        child: child!,
      ),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routerConfig: router,
    );
  }

  for (final locale in ['en', 'de']) {
    testWidgets(
      'organization entry wraps instructions on narrow large-text phone $locale',
      (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        await tester.pumpWidget(buildApp(locale: Locale(locale), scale: 1.6));
        await tester.pumpAndSettle();
        final field = tester.widget<TextField>(find.byType(TextField));
        expect(field.decoration!.helper, isA<Text>());
        final helper = tester.renderObject<RenderParagraph>(
          find.text((field.decoration!.helper! as Text).data!),
        );
        expect(
          helper.didExceedMaxLines,
          isFalse,
          reason: 'Essential organization guidance must remain fully visible.',
        );
        expect(field.decoration!.errorMaxLines, greaterThan(1));
        expect(field.decoration!.labelText, isNot(contains('URI')));
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'offers one member organization-access flow without operator fields',
    (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      expect(find.text('Organization access'), findsOneWidget);
      expect(find.text('Organization address or invitation'), findsOneWidget);
      expect(find.text('Continue to organization'), findsOneWidget);
      expect(find.bySemanticsLabel('Weave logo'), findsOneWidget);
      expect(find.textContaining('same Weave address'), findsOneWidget);
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

  testWidgets('manual server URI carries only the organization origin', (
    tester,
  ) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'https://weave.example');
    await tester.tap(find.text('Continue to organization'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('resolved:/join?organization_origin='),
      findsOneWidget,
    );
    expect(
      find.textContaining('organization_origin=https%3A%2F%2Fweave.example%2F'),
      findsOneWidget,
    );
    expect(find.textContaining('handoff_ref='), findsNothing);
    expect(find.textContaining('workspace='), findsNothing);
  });

  for (final input in ['weave.example', 'weave.example:8443']) {
    testWidgets('normalizes bare host $input to HTTPS', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), input);
      await tester.tap(find.text('Continue to organization'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining(Uri.encodeQueryComponent('https://$input/')),
        findsOneWidget,
      );
    });
  }
  for (final input in [
    'http://weave.example',
    'https://user:pass@weave.example',
    'weave.example:99999',
    'weave.example/private',
    'weave.example?token=secret',
  ]) {
    testWidgets('rejects unsafe or ambiguous organization input $input', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), input);
      await tester.tap(find.text('Continue to organization'));
      await tester.pumpAndSettle();
      expect(find.textContaining('resolved:'), findsNothing);
    });
  }

  testWidgets('meets accessible tap-target and labeling guidelines', (
    tester,
  ) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();

    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  });

  testWidgets('keeps organization access usable at large text sizes', (
    tester,
  ) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Organization access'), findsOneWidget);
    expect(find.text('Continue to organization'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'https://weave.example');
    await tester.ensureVisible(find.text('Continue to organization'));
    await tester.tap(find.text('Continue to organization'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('resolved:/join?organization_origin='),
      findsOneWidget,
    );
  });

  testWidgets('pasted email or QR join payload is preserved', (tester) async {
    await tester.pumpWidget(buildApp());
    await tester.pumpAndSettle();
    const link =
        'https://weave.example/join?handoff_ref=invite-123&org=acme&workspace=home&run_id=email-123';

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
