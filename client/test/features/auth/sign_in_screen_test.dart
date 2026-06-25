import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

void main() {
  group('SignInScreen', () {
    testWidgets('shows the Weave logo with saved sign-in configuration', (
      tester,
    ) async {
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
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
            home: SignInScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(WeaveLogo), findsOneWidget);
      expect(find.text('Sign in to continue'), findsOneWidget);
      expect(
        find.textContaining('Use your Weave workspace sign-in'),
        findsOneWidget,
      );
      for (final term in ['provider', 'OIDC', 'SAML', 'Matrix', 'Nextcloud']) {
        expect(find.textContaining(term), findsNothing);
      }
    });

    testWidgets('exposes the logo semantics on the sign-in screen', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();

      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(buildStoredConfiguration()),
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
            home: SignInScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.bySemanticsLabel('Weave logo'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('localizes offline token provider failures', (tester) async {
      late AppLocalizations l10n;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) {
              l10n = AppLocalizations.of(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      final message = authFailureMessage(
        l10n,
        const AuthFailure.protocol(
          'Offline tokens not allowed for the user or client',
        ),
      );

      expect(message, l10n.signInOfflineSessionNotAllowed);
      expect(message, isNot(contains('Offline tokens not allowed')));
    });
  });
}
