import 'dart:convert';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/bootstrap/domain/bootstrap_state.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/app/domain/use_cases/sign_in_with_oidc.dart';
import 'package:weave/features/app/domain/use_cases/resolve_app_bootstrap.dart';
import 'package:weave/features/app/presentation/providers/app_application_providers.dart';
import 'package:weave/features/auth/domain/entities/auth_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_auth_onboarding_state.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/consume_member_handoff.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';

class _ThrowingConsumeMemberHandoff implements ConsumeMemberHandoff {
  const _ThrowingConsumeMemberHandoff(this.error);

  final Object error;

  @override
  Future<MemberHandoff> call(Uri uri) async {
    throw error;
  }
}

class _SuccessfulConsumeMemberHandoff implements ConsumeMemberHandoff {
  const _SuccessfulConsumeMemberHandoff(this.handoff);

  final MemberHandoff handoff;

  @override
  Future<MemberHandoff> call(Uri uri) async => handoff;
}

class _RecordingSignInWithOidc implements SignInWithOidc {
  _RecordingSignInWithOidc();

  final Completer<void> completer = Completer<void>();
  var callCount = 0;
  bool? isInteractiveSignInSupported;

  @override
  Future<void> call({required bool isInteractiveSignInSupported}) {
    callCount += 1;
    this.isInteractiveSignInSupported = isInteractiveSignInSupported;
    return completer.future;
  }
}

class _FailingSignInWithOidc implements SignInWithOidc {
  const _FailingSignInWithOidc(this.failure);

  final AuthFailure failure;

  @override
  Future<void> call({required bool isInteractiveSignInSupported}) async {
    throw failure;
  }
}

class _ReadyResolveAppBootstrap implements ResolveAppBootstrap {
  const _ReadyResolveAppBootstrap();

  @override
  Future<BootstrapState> call() async => const BootstrapState.ready();
}

void main() {
  group('MemberHandoffScreen', () {
    testWidgets(
      'shows handoff-ready UI and records visible state after success',
      (tester) async {
        final preferencesStore = InMemoryPreferencesStore();
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith((ref) => preferencesStore),
            consumeMemberHandoffProvider.overrideWithValue(
              _SuccessfulConsumeMemberHandoff(
                MemberHandoff(
                  handoffRef: 'handoff-s32-massimo-dogfood-home',
                  profile: 'local-lan-dogfood',
                  runId: 's32-massimo-dogfood',
                  organizationSlug: 'massimo-dogfood',
                  workspaceSlug: 'home',
                  platformConfigUrl: Uri.parse(
                    'https://weave.test:44443/api/platform/config',
                  ),
                  productBaseUrl: Uri.parse('https://weave.test:44443'),
                ),
              ),
            ),
          ],
        );
        addTearDown(container.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: MaterialApp(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: MemberHandoffScreen(
                uri: Uri.parse(
                  'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.pump();

        expect(find.text('Workspace ready for sign-in'), findsOneWidget);
        expect(find.textContaining('massimo-dogfood/home'), findsOneWidget);
        expect(find.text('Sign In'), findsOneWidget);

        final rawVisibleState = preferencesStore.rawString(
          dogfoodVisibleStateStorageKey,
        );
        expect(rawVisibleState, isNotNull);
        final visibleState =
            jsonDecode(rawVisibleState!) as Map<String, dynamic>;
        expect(
          visibleState['schemaVersion'],
          'weave.client.dogfood_visible_state.v1',
        );
        expect(visibleState['state'], 'handoff_ready');
        expect(visibleState['handoffRef'], 'handoff-s32-massimo-dogfood-home');
        expect(visibleState['runId'], 's32-massimo-dogfood');
        expect(visibleState['organizationSlug'], 'massimo-dogfood');
        expect(visibleState['workspaceSlug'], 'home');
        expect(visibleState['supportSafe'], isTrue);
      },
    );

    testWidgets('starts interactive sign-in from the handoff ready action', (
      tester,
    ) async {
      final preferencesStore = InMemoryPreferencesStore();
      final signIn = _RecordingSignInWithOidc();
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => preferencesStore),
          consumeMemberHandoffProvider.overrideWithValue(
            _SuccessfulConsumeMemberHandoff(
              MemberHandoff(
                handoffRef: 'handoff-s32-massimo-dogfood-home',
                profile: 'local-lan-dogfood',
                runId: 's32-massimo-dogfood',
                organizationSlug: 'massimo-dogfood',
                workspaceSlug: 'home',
                platformConfigUrl: Uri.parse(
                  'https://weave.test:44443/api/platform/config',
                ),
                productBaseUrl: Uri.parse('https://weave.test:44443'),
              ),
            ),
          ),
          signInWithOidcProvider.overrideWithValue(signIn),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: MemberHandoffScreen(
              uri: Uri.parse(
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Sign In'));
      await tester.pump();

      expect(signIn.callCount, 1);
      expect(signIn.isInteractiveSignInSupported, isTrue);

      final rawAuthState = preferencesStore.rawString(
        dogfoodAuthStateStorageKey,
      );
      expect(rawAuthState, isNotNull);
      final authState = jsonDecode(rawAuthState!) as Map<String, dynamic>;
      expect(authState['schemaVersion'], 'weave.client.dogfood_auth_state.v1');
      expect(authState['state'], 'sso_in_progress');
      expect(authState['handoffRef'], 'handoff-s32-massimo-dogfood-home');
      expect(authState['supportSafe'], isTrue);
    });

    testWidgets(
      'records browser return and workspace-ready evidence after successful sign-in',
      (tester) async {
        final preferencesStore = InMemoryPreferencesStore();
        final signIn = _RecordingSignInWithOidc();
        final router = GoRouter(
          initialLocation: '/join',
          routes: [
            GoRoute(
              path: '/join',
              builder: (context, state) => MemberHandoffScreen(
                uri: Uri.parse(
                  'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
                ),
              ),
            ),
            GoRoute(
              path: AppRoutes.home,
              builder: (context, state) =>
                  const Scaffold(body: Text('First run workspace shell')),
            ),
          ],
        );
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith((ref) => preferencesStore),
            consumeMemberHandoffProvider.overrideWithValue(
              _SuccessfulConsumeMemberHandoff(
                MemberHandoff(
                  handoffRef: 'handoff-s32-massimo-dogfood-home',
                  profile: 'local-lan-dogfood',
                  runId: 's32-massimo-dogfood',
                  organizationSlug: 'massimo-dogfood',
                  workspaceSlug: 'home',
                  platformConfigUrl: Uri.parse(
                    'https://weave.test:44443/api/platform/config',
                  ),
                  productBaseUrl: Uri.parse('https://weave.test:44443'),
                ),
              ),
            ),
            signInWithOidcProvider.overrideWithValue(signIn),
            resolveAppBootstrapProvider.overrideWithValue(
              const _ReadyResolveAppBootstrap(),
            ),
          ],
        );
        addTearDown(container.dispose);
        addTearDown(router.dispose);

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: MaterialApp.router(
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              routerConfig: router,
            ),
          ),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('Sign In'));
        await tester.pump();
        signIn.completer.complete();
        await tester.pumpAndSettle();

        expect(find.text('First run workspace shell'), findsOneWidget);

        final rawAuthState = preferencesStore.rawString(
          dogfoodAuthStateStorageKey,
        );
        expect(rawAuthState, isNotNull);
        final authState = jsonDecode(rawAuthState!) as Map<String, dynamic>;
        expect(authState['state'], 'workspace_ready');
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
          containsAllInOrder([
            'sso_in_progress',
            'authenticated',
            'workspace_bootstrap_loading',
            'workspace_ready',
          ]),
        );
      },
    );

    testWidgets('localizes sign-in failures on the handoff ready action', (
      tester,
    ) async {
      final preferencesStore = InMemoryPreferencesStore();
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => preferencesStore),
          consumeMemberHandoffProvider.overrideWithValue(
            _SuccessfulConsumeMemberHandoff(
              MemberHandoff(
                handoffRef: 'handoff-s32-massimo-dogfood-home',
                profile: 'local-lan-dogfood',
                runId: 's32-massimo-dogfood',
                organizationSlug: 'massimo-dogfood',
                workspaceSlug: 'home',
                platformConfigUrl: Uri.parse(
                  'https://weave.test:44443/api/platform/config',
                ),
                productBaseUrl: Uri.parse('https://weave.test:44443'),
              ),
            ),
          ),
          signInWithOidcProvider.overrideWithValue(
            const _FailingSignInWithOidc(
              AuthFailure.protocol(
                'Offline tokens not allowed for the user or client',
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: MemberHandoffScreen(
              uri: Uri.parse(
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Sign In'));
      await tester.pumpAndSettle();

      expect(
        find.text(
          'This organization has not allowed long-lived mobile sessions for this account yet. Ask an admin/operator to enable mobile session access, then sign in again.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Offline tokens not allowed'), findsNothing);

      final rawAuthState = preferencesStore.rawString(
        dogfoodAuthStateStorageKey,
      );
      expect(rawAuthState, isNotNull);
      final authState = jsonDecode(rawAuthState!) as Map<String, dynamic>;
      expect(authState['state'], 'recoverable_error');
      expect(authState['errorCode'], 'WEAVE-MOBILE-OFFLINE-SESSION-DENIED');
      expect(authState['supportSafe'], isTrue);
    });

    testWidgets('shows and records a support-safe handoff failure code', (
      tester,
    ) async {
      final preferencesStore = InMemoryPreferencesStore();
      final container = ProviderContainer.test(
        overrides: [
          preferencesStoreProvider.overrideWith((ref) => preferencesStore),
          consumeMemberHandoffProvider.overrideWithValue(
            const _ThrowingConsumeMemberHandoff(
              AppFailure.bootstrap(
                'WEAVE-APP-START-TLS-FAILED: The workspace start configuration could not be reached.',
              ),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: MemberHandoffScreen(
              uri: Uri.parse(
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('We could not open this Weave invite'), findsOneWidget);
      expect(
        find.textContaining(
          'Weave could not reach the workspace start configuration over trusted TLS.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('The invite may be expired'), findsNothing);
      expect(find.textContaining('WEAVE-APP-START-TLS-FAILED'), findsOneWidget);

      final rawEvidence = preferencesStore.rawString(
        lastHandoffConsumedStorageKey,
      );
      expect(rawEvidence, isNotNull);
      final evidence = jsonDecode(rawEvidence!) as Map<String, dynamic>;
      expect(
        evidence['schemaVersion'],
        'weave.client.last_handoff_consumed.v1',
      );
      expect(evidence['result'], 'failed');
      expect(evidence['phase'], 'app_start_discovery');
      expect(evidence['errorCode'], 'WEAVE-APP-START-TLS-FAILED');
      expect(evidence['handoffRef'], 'handoff-s32-massimo-dogfood-home');
      expect(evidence['supportSafe'], isTrue);

      final rawVisibleState = preferencesStore.rawString(
        dogfoodVisibleStateStorageKey,
      );
      expect(rawVisibleState, isNotNull);
      final visibleState = jsonDecode(rawVisibleState!) as Map<String, dynamic>;
      expect(visibleState['state'], 'handoff_error');
      expect(visibleState['errorCode'], 'WEAVE-APP-START-TLS-FAILED');
      expect(visibleState['supportSafe'], isTrue);
    });
  });
}
