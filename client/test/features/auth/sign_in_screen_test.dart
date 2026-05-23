import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/weave_logo.dart';
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
  });
}
