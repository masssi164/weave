import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/persistence/flutter_secure_store.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/auth/data/services/flutter_appauth_oidc_client.dart';
import 'package:weave/features/auth/data/services/oidc_client.dart';
import 'package:weave/features/onboarding/presentation/setup_flow.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

class _FakeOidcClient implements OidcClient {
  @override
  Future<OidcTokenBundle> authorizeAndExchangeCode(configuration) {
    throw UnimplementedError();
  }

  @override
  Future<void> endSession(configuration, {required String idTokenHint}) async {}

  @override
  Future<OidcTokenBundle> refresh(
    configuration, {
    required String refreshToken,
  }) {
    throw UnimplementedError();
  }
}

void main() {
  group('SetupFlow', () {
    late InMemoryPreferencesStore preferencesStore;

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
            path: AppRoutes.signIn,
            builder: (context, state) =>
                const Scaffold(body: Center(child: Text('Sign In Ready'))),
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
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => preferencesStore),
          secureStoreProvider.overrideWithValue(InMemorySecureStore()),
          oidcClientProvider.overrideWithValue(_FakeOidcClient()),
        ],
        child: MaterialApp.router(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          routerConfig: router,
        ),
      );
    }

    setUp(() {
      preferencesStore = InMemoryPreferencesStore();
    });

    testWidgets(
      'renders first step with provider and issuer fields',
      (tester) async {
        await tester.pumpWidget(buildApp());
        await tester.pumpAndSettle();

        expect(find.byType(WeaveLogo), findsOneWidget);
        expect(find.text('Connect Your Server'), findsOneWidget);
        expect(find.text('Provider type'), findsOneWidget);
        expect(find.text('OIDC Client ID'), findsNothing);
        expect(find.text('Next'), findsOneWidget);
      },
    );

    testWidgets('does not show manual OIDC registration guidance', (
      tester,
    ) async {
      await tester.pumpWidget(buildApp());
      await tester.pumpAndSettle();

      expect(
        find.text('Register Weave as a native/public client'),
        findsNothing,
      );
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
      expect(find.text('https://files.home.internal'), findsWidgets);
      expect(find.text('Finish'), findsOneWidget);
    });

    testWidgets('saves configuration and navigates to sign-in on Finish tap', (
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
        _textFieldWithLabel('Files Base URL'),
        'https://files.home.internal',
      );
      await tester.tap(find.text('Finish'));
      await tester.pumpAndSettle();

      expect(find.text('Sign In Ready'), findsOneWidget);

      final raw = preferencesStore.rawString(serverConfigurationStorageKey);
      final json = jsonDecode(raw!) as Map<String, dynamic>;
      expect(json['oidcClientId'], 'weave-app');
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
