import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';
import 'package:weave/features/server_config/data/dtos/server_configuration_dto.dart';
import 'package:weave/features/server_config/data/repositories/shared_preferences_server_configuration_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/core/widgets/weave_logo.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/auth/presentation/auth_failure_message.dart';
import 'package:weave/features/auth/presentation/sign_in_screen.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';

class _FailingSignInWithOidc implements SignInWithOidc {
  const _FailingSignInWithOidc(this.failure);

  final AuthFailure failure;

  @override
  Future<void> call({required bool isInteractiveSignInSupported}) async {
    throw failure;
  }
}

// Saved sign-in requires the same validated manifest profile as ordinary setup.
Map<String, Object> _storedOrganizationProfile() => {
  serverConfigurationStorageKey: ServerConfigurationDto.fromConfiguration(
    buildTestConfiguration(),
  ).encode(),
};

void main() {
  group('SignInScreen', () {
    for (final locale in ['en', 'de']) {
      testWidgets(
        'saved bare-host organization sign-in contains no fabricated invitation metadata $locale',
        (tester) async {
          final container = ProviderContainer.test(
            overrides: [
              appStartDiscoveryClientProvider.overrideWith(
                (ref) => buildTestDiscoveryClient(
                  ref.watch(serverConfigurationRepositoryProvider),
                ),
              ),
              preferencesStoreProvider.overrideWith(
                (ref) => InMemoryPreferencesStore(_storedOrganizationProfile()),
              ),
            ],
          );
          addTearDown(container.dispose);
          await tester.pumpWidget(
            UncontrolledProviderScope(
              container: container,
              child: MaterialApp(
                locale: Locale(locale),
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                home: const SignInScreen(),
              ),
            ),
          );
          await tester.pumpAndSettle();
          expect(find.text('https://weave.home.internal'), findsOneWidget);
          expect(
            find.byKey(const ValueKey('weave.auth.sign-in')),
            findsOneWidget,
          );
          for (final wrong in [
            'unknown',
            'Dogfood',
            'dogfood',
            'WEAVE-SSO-NOT-COMPLETE',
            'invite ready',
            'Einladung bereit',
            'Issuer',
            'Client-ID',
          ]) {
            expect(find.textContaining(wrong), findsNothing);
          }
        },
      );
    }

    testWidgets('shows the Weave logo with saved sign-in configuration', (
      tester,
    ) async {
      final container = ProviderContainer.test(
        overrides: [
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(_storedOrganizationProfile()),
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
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(_storedOrganizationProfile()),
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

    testWidgets('shows a recoverable sign-in error without test metadata', (
      tester,
    ) async {
      final container = ProviderContainer.test(
        overrides: [
          appStartDiscoveryClientProvider.overrideWith(
            (ref) => buildTestDiscoveryClient(
              ref.watch(serverConfigurationRepositoryProvider),
            ),
          ),
          preferencesStoreProvider.overrideWith(
            (ref) => InMemoryPreferencesStore(_storedOrganizationProfile()),
          ),
          signInWithOidcProvider.overrideWithValue(
            const _FailingSignInWithOidc(
              AuthFailure.protocol('Unable to complete sign-in.'),
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
            home: SignInScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byType(FilledButton));
      await tester.tap(find.byType(FilledButton));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Sign-in could not be completed'),
        findsOneWidget,
      );
      expect(find.textContaining('dogfood'), findsNothing);
    });

  });
}
