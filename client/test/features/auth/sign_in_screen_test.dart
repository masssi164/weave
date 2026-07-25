import 'dart:convert';

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
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
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

    testWidgets(
      'records support-safe SSO failure evidence when a saved handoff exists',
      (tester) async {
        final preferencesStore = InMemoryPreferencesStore({
          ...buildStoredConfiguration(),
          lastHandoffConsumedStorageKey: jsonEncode(<String, Object>{
            'schemaVersion': 'weave.client.last_handoff_consumed.v1',
            'result': 'saved_configuration',
            'handoffRef': 'handoff-s32-massimo-dogfood-home',
            'runId': 's32-massimo-dogfood',
            'organizationSlug': 'massimo-dogfood',
            'workspaceSlug': 'home',
            'profile': 'local-lan-dogfood',
            'supportSafe': true,
          }),
        });
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith((ref) => preferencesStore),
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

        final rawAuthState = preferencesStore.rawString(
          dogfoodAuthStateStorageKey,
        );
        expect(rawAuthState, isNotNull);
        final authState = jsonDecode(rawAuthState!) as Map<String, dynamic>;
        expect(authState['state'], 'recoverable_error');
        expect(authState['errorCode'], 'WEAVE-SSO-NOT-COMPLETE');
        expect(authState['handoffRef'], 'handoff-s32-massimo-dogfood-home');
        expect(authState['supportSafe'], isTrue);

        final rawHistory = preferencesStore.rawString(
          dogfoodAuthStateHistoryStorageKey,
        );
        expect(rawHistory, isNotNull);
        final history = jsonDecode(rawHistory!) as List<dynamic>;
        expect(
          history
              .cast<Map<String, dynamic>>()
              .map((entry) => entry['state'])
              .toList(),
          containsAllInOrder(['sso_in_progress', 'recoverable_error']),
        );
      },
    );
  });
}
