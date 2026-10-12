import 'dart:convert';
import 'dart:async';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:weave/features/server_config/presentation/providers/saved_server_configuration_provider.dart';
import 'package:weave/features/server_config/presentation/providers/server_configuration_repository_provider.dart';

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
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/onboarding/presentation/member_handoff_screen.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

import '../../helpers/in_memory_stores.dart';
import '../../helpers/server_config_test_data.dart';
import '../../../integration_test/dual_ios_collaboration_test.dart'
    show
        nativeDualHandoffSignIn,
        nativeDualOnboardingDiagnostic,
        safelyReportDualOnboardingFailure;

class _ThrowingDiscoverOrganizationAccess
    implements DiscoverOrganizationAccess {
  const _ThrowingDiscoverOrganizationAccess(this.error);

  final Object error;

  @override
  Future<OrganizationAccess> call(Uri uri) async {
    throw error;
  }
}

class _SuccessfulDiscoverOrganizationAccess
    implements DiscoverOrganizationAccess {
  _SuccessfulDiscoverOrganizationAccess(MemberHandoff handoff)
    : access = OrganizationAccess(
        organizationOrigin: handoff.productBaseUrl,
        handoff: handoff,
      );

  final OrganizationAccess access;

  @override
  Future<OrganizationAccess> call(Uri uri) async => access;
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

class _SignInThenReadyResolveAppBootstrap implements ResolveAppBootstrap {
  var calls = 0;

  @override
  Future<BootstrapState> call() async => calls++ == 0
      ? const BootstrapState.needsSignIn()
      : const BootstrapState.ready();
}

void main() {
  test('dual onboarding diagnostics bound counts and discard private values', () {
    final safe = nativeDualOnboardingDiagnostic(
      role: 'A',
      authState: 'ready_for_sso',
      visibleState: 'handoff_ready',
      errorCode: 'none',
      counts: {'organization': -1, 'handoff': 10000, 'ready': 1, 'signIn': 1},
    );
    expect(
      safe,
      contains(
        'actor=A authState=ready_for_sso visibleState=handoff_ready errorCode=none',
      ),
    );
    expect(
      safe,
      contains(
        'organizationCount=0 handoffCount=999 readyCount=1 signInCount=1 workspaceCount=0',
      ),
    );
    final private = nativeDualOnboardingDiagnostic(
      role: 'private-role',
      authState: 'private-token',
      visibleState: 'https://private.invalid',
      errorCode: 'private-password',
      counts: {},
    );
    expect(private, isNot(contains('private')));
    expect(
      private,
      contains(
        'actor=unavailable authState=unavailable visibleState=unavailable errorCode=unavailable',
      ),
    );
  });

  test(
    'diagnostic failures preserve the original onboarding assertion',
    () async {
      final original = StateError('original UI assertion');
      Future<void> failing() async {
        try {
          throw original;
        } catch (_) {
          await safelyReportDualOnboardingFailure(
            report: () async => throw StateError('reporter failure'),
            unavailable: () => throw StateError('fallback failure'),
          );
          rethrow;
        }
      }

      await expectLater(failing(), throwsA(same(original)));
      var reported = false;
      await safelyReportDualOnboardingFailure(
        report: () async {
          reported = true;
        },
        unavailable: () => fail('successful report must not invoke fallback'),
      );
      expect(reported, isTrue);
    },
  );

  group('MemberHandoffScreen', () {
    for (final successful in [true, false]) {
      testWidgets(
        successful
            ? 'publishes discovery-saved canonical profile to existing null subscribers before ready UI'
            : 'failed discovery preserves the existing canonical profile subscription',
        (tester) async {
          final store = InMemoryPreferencesStore();
          final container = ProviderContainer(
            overrides: [
              resolveAppBootstrapProvider.overrideWithValue(
                _SignInThenReadyResolveAppBootstrap(),
              ),
              preferencesStoreProvider.overrideWith((ref) => store),
              discoverOrganizationAccessProvider.overrideWith(
                (ref) => DiscoverOrganizationAccess(
                  repository: ref.watch(serverConfigurationRepositoryProvider),
                  evidenceStore: store,
                  discoveryClient: AppStartDiscoveryClient(
                    httpClient: MockClient((request) async {
                      expect(
                        request.url,
                        Uri.parse('https://weave.example/api/platform/config'),
                      );
                      return http.Response(
                        successful
                            ? jsonEncode(
                                (jsonDecode(
                                        File(
                                          'test/fixtures/org_manifest_v2_valid.json',
                                        ).readAsStringSync(),
                                      )
                                      as Map<String, dynamic>)
                                  ..['organizationOrigin'] =
                                      'https://weave.example',
                              )
                            : '{}',
                        successful ? 200 : 503,
                        headers: {'content-type': 'application/json'},
                      );
                    }),
                  ),
                ),
              ),
            ],
          );
          addTearDown(container.dispose);
          if (!successful) {
            await container
                .read(serverConfigurationRepositoryProvider)
                .saveConfiguration(buildTestConfiguration());
          }
          final subscription = container.listen(
            savedServerConfigurationProvider,
            (_, _) {},
          );
          addTearDown(subscription.close);
          final previous = await container.read(
            savedServerConfigurationProvider.future,
          );
          if (successful) {
            expect(previous, isNull);
          } else {
            expect(previous, isNotNull);
          }
          await tester.pumpWidget(
            UncontrolledProviderScope(
              container: container,
              child: MaterialApp(
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                home: MemberHandoffScreen(
                  uri: Uri.parse('https://weave.example'),
                ),
              ),
            ),
          );
          // The generated HTTP client consumes an asynchronous response stream;
          // wait for its real event-loop work before inspecting the ready frame.
          for (var attempt = 0; attempt < 50; attempt++) {
            await tester.runAsync(
              () => Future<void>.delayed(const Duration(milliseconds: 10)),
            );
            await tester.pumpAndSettle();
            final raw = store.rawString(dogfoodAuthStateStorageKey);
            if (raw != null &&
                {
                  'ready_for_sso',
                  'recoverable_error',
                }.contains(jsonDecode(raw)['state'])) {
              break;
            }
          }
          final current = await container.read(
            savedServerConfigurationProvider.future,
          );
          if (successful) {
            expect(nativeDualHandoffSignIn('Sign In'), findsOneWidget);
            expect(current, isNotNull);
            expect(
              current!.organizationOrigin,
              Uri.parse('https://weave.example/'),
            );
            expect(current.validatedManifest, isNotNull);
            expect(current.manifestValidatedAt, isNotNull);
            expect(
              jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
              'ready_for_sso',
            );
          } else {
            expect(nativeDualHandoffSignIn('Sign In'), findsNothing);
            expect(current, same(previous));
            expect(
              jsonDecode(store.rawString(dogfoodAuthStateStorageKey)!)['state'],
              'recoverable_error',
            );
          }
        },
      );
    }

    testWidgets(
      'shows handoff-ready UI and records visible state after success',
      (tester) async {
        final preferencesStore = InMemoryPreferencesStore();
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith((ref) => preferencesStore),
            discoverOrganizationAccessProvider.overrideWithValue(
              _SuccessfulDiscoverOrganizationAccess(
                MemberHandoff(
                  handoffRef: 'handoff-s32-massimo-dogfood-home',
                  runId: 's32-massimo-dogfood',
                  organizationSlug: 'massimo-dogfood',
                  workspaceSlug: 'home',
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
                  'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.pump();

        expect(find.text('Workspace ready for sign-in'), findsOneWidget);
        expect(find.textContaining('massimo-dogfood'), findsOneWidget);
        expect(find.text('Sign In'), findsOneWidget);
        expect(nativeDualHandoffSignIn('Sign In'), findsOneWidget);
        expect(nativeDualHandoffSignIn('Anmelden'), findsNothing);

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
          discoverOrganizationAccessProvider.overrideWithValue(
            _SuccessfulDiscoverOrganizationAccess(
              MemberHandoff(
                handoffRef: 'handoff-s32-massimo-dogfood-home',
                runId: 's32-massimo-dogfood',
                organizationSlug: 'massimo-dogfood',
                workspaceSlug: 'home',
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
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
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
      expect(nativeDualHandoffSignIn('Sign In'), findsNothing);

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
                  'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
                ),
              ),
            ),
            GoRoute(
              path: AppRoutes.home,
              builder: (context, state) =>
                  const Scaffold(body: Text('Authenticated workspace shell')),
            ),
          ],
        );
        final container = ProviderContainer.test(
          overrides: [
            preferencesStoreProvider.overrideWith((ref) => preferencesStore),
            discoverOrganizationAccessProvider.overrideWithValue(
              _SuccessfulDiscoverOrganizationAccess(
                MemberHandoff(
                  handoffRef: 'handoff-s32-massimo-dogfood-home',
                  runId: 's32-massimo-dogfood',
                  organizationSlug: 'massimo-dogfood',
                  workspaceSlug: 'home',
                  productBaseUrl: Uri.parse('https://weave.test:44443'),
                ),
              ),
            ),
            signInWithOidcProvider.overrideWithValue(signIn),
            resolveAppBootstrapProvider.overrideWithValue(
              _SignInThenReadyResolveAppBootstrap(),
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

        expect(find.text('Authenticated workspace shell'), findsOneWidget);

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
          discoverOrganizationAccessProvider.overrideWithValue(
            _SuccessfulDiscoverOrganizationAccess(
              MemberHandoff(
                handoffRef: 'handoff-s32-massimo-dogfood-home',
                runId: 's32-massimo-dogfood',
                organizationSlug: 'massimo-dogfood',
                workspaceSlug: 'home',
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
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fweave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
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
          discoverOrganizationAccessProvider.overrideWithValue(
            const _ThrowingDiscoverOrganizationAccess(
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
                'weave://join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&run_id=s32-massimo-dogfood&product_base_url=https%3A%2F%2Fweave.test%3A44443&platform_config_url=https%3A%2F%2Fapi.weave.test%3A44443%2Fapi%2Fplatform%2Fconfig',
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
