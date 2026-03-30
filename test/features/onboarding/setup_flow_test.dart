import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/features/onboarding/presentation/setup_flow.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

void main() {
  group('SetupFlow', () {
    Widget buildApp() {
      final router = GoRouter(
        routes: [
          GoRoute(
            path: AppRoutes.welcome,
            builder: (context, state) => const SizedBox.shrink(),
          ),
          GoRoute(
            path: AppRoutes.setup,
            builder: (context, state) => const SetupFlow(),
          ),
          GoRoute(
            path: AppRoutes.chat,
            builder: (context, state) =>
                const Scaffold(body: Center(child: Text('Chat Ready'))),
          ),
        ],
        initialLocation: AppRoutes.setup,
      );

      return ProviderScope(
        child: MaterialApp.router(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          routerConfig: router,
        ),
      );
    }

    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    testWidgets('renders first step with provider and issuer fields', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      expect(find.text('Connect Your Server'), findsOneWidget);
      expect(find.text('Provider type'), findsOneWidget);
      expect(find.text('Next'), findsOneWidget);
    });

    testWidgets('derives service endpoints from the issuer host', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.enterText(
        _textFieldWithLabel('OIDC Issuer URL'),
        'https://auth.home.internal',
      );
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      expect(find.text('Review Service Endpoints'), findsOneWidget);
      expect(find.text('https://matrix.home.internal'), findsWidgets);
      expect(find.text('https://nextcloud.home.internal'), findsWidgets);
      expect(find.text('Finish'), findsOneWidget);
    });

    testWidgets('saves configuration and navigates to chat on Finish tap', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.enterText(
        _textFieldWithLabel('OIDC Issuer URL'),
        'https://auth.home.internal',
      );
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      await tester.enterText(
        _textFieldWithLabel('Nextcloud Base URL'),
        'https://cloud.home.internal',
      );
      await tester.tap(find.text('Finish'));
      await tester.pumpAndSettle();

      expect(find.text('Chat Ready'), findsOneWidget);

      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString(serverConfigurationStorageKey), isNotNull);
    });

    testWidgets('goes back to provider step from services step', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await tester.enterText(
        _textFieldWithLabel('OIDC Issuer URL'),
        'https://auth.home.internal',
      );
      await tester.tap(find.text('Next'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Back'));
      await tester.pumpAndSettle();

      expect(find.text('Connect Your Server'), findsOneWidget);
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
