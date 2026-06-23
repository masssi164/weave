import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:weave/core/router/app_routes.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/domain/repositories/first_run_status_repository.dart';
import 'package:weave/features/onboarding/presentation/first_run_screen.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';
import 'package:weave/integrations/weave_api/presentation/providers/weave_authenticated_session_provider.dart';

import '../../helpers/first_run_status_fixture.dart';
import '../../helpers/test_app.dart';

void main() {
  group('FirstRunScreen', () {
    testWidgets('shows identity, role, and ready module states after SSO', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const FirstRunScreen(),
          overrides: [
            firstRunStatusProvider.overrideWith(
              (ref) async =>
                  FirstRunLoadResult.authenticated(buildTestFirstRunStatus()),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Your Weave workspace is ready'), findsOneWidget);
      expect(find.text('Alice Example'), findsOneWidget);
      expect(find.text('member'), findsOneWidget);
      expect(find.text('Chat'), findsOneWidget);
      expect(find.text('Files'), findsOneWidget);
      expect(find.text('Calendar'), findsOneWidget);
      expect(find.text('Ready'), findsWidgets);
      expect(
        find.textContaining('no separate Chat, Files, or Calendar credentials'),
        findsOneWidget,
      );
      expect(find.text('Owner/admin setup responsibilities'), findsNothing);
      expect(find.textContaining('OIDC'), findsNothing);
      expect(find.textContaining('provider stack'), findsNothing);
    });

    testWidgets('shows owner/admin setup responsibilities only to admins', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const FirstRunScreen(),
          overrides: [
            firstRunStatusProvider.overrideWith(
              (ref) async => FirstRunLoadResult.authenticated(
                buildTestFirstRunStatus(
                  access: const FirstRunAccess(
                    primaryRole: 'owner',
                    roles: ['owner'],
                    groups: ['workspace-default'],
                    canAdministerWorkspace: true,
                    canInviteUsers: true,
                    canUseWorkspaceModules: true,
                  ),
                ),
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Owner/admin setup responsibilities'), findsOneWidget);
      expect(
        find.textContaining('normal members should only need'),
        findsOneWidget,
      );
      for (final term in ['OIDC', 'realm', 'service endpoint', 'provider']) {
        expect(find.textContaining(term), findsNothing);
      }
    });

    testWidgets('shows pending and degraded module actions accessibly', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const FirstRunScreen(),
          overrides: [
            firstRunStatusProvider.overrideWith(
              (ref) async => FirstRunLoadResult.authenticated(
                buildTestFirstRunStatus(
                  firstRunComplete: false,
                  profile: const FirstRunProfileStatus(
                    status: 'pending',
                    missing: ['email_verified'],
                    message:
                        'The Weave profile is waiting for email verification.',
                    action: 'Verify your email, then refresh status.',
                  ),
                  matrix: const FirstRunModuleStatus(
                    state: FirstRunProvisioningState.pending,
                    message: 'Chat provisioning is pending.',
                    action: 'Wait briefly, then retry.',
                  ),
                  nextcloud: const FirstRunModuleStatus(
                    state: FirstRunProvisioningState.degraded,
                    message: 'Files are available but degraded.',
                    action: 'Ask an admin to check service health.',
                  ),
                  calendar: const FirstRunModuleStatus(
                    state: FirstRunProvisioningState.notConfigured,
                    message: 'Calendar is not available yet.',
                  ),
                  actions: ['Verify your email, then refresh status.'],
                ),
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Your Weave workspace is being prepared'),
        findsOneWidget,
      );
      expect(find.text('Pending'), findsWidgets);
      expect(find.text('Degraded'), findsWidgets);
      expect(find.text('Next steps'), findsOneWidget);
      expect(
        find.text('Verify your email, then refresh status.'),
        findsNothing,
      );
      expect(
        find.text(
          'Workspace setup needs admin attention before every capability is ready.',
        ),
        findsOneWidget,
      );
      expect(find.text('Continue to chat'), findsNothing);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });

    testWidgets('keeps provider diagnostics out of member readiness cards', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const FirstRunScreen(),
          overrides: [
            firstRunStatusProvider.overrideWith(
              (ref) async => FirstRunLoadResult.authenticated(
                buildTestFirstRunStatus(
                  firstRunComplete: false,
                  matrix: const FirstRunModuleStatus(
                    state: FirstRunProvisioningState.failed,
                    message: 'Matrix homeserver failed federation check.',
                    action: 'Inspect Synapse workers.',
                  ),
                  nextcloud: const FirstRunModuleStatus(
                    state: FirstRunProvisioningState.degraded,
                    message: 'Nextcloud WebDAV mount is degraded.',
                    action: 'Check Nextcloud app passwords.',
                  ),
                  calendar: const FirstRunModuleStatus(
                    state: FirstRunProvisioningState.notConfigured,
                    message: 'CalDAV backend missing.',
                    action: 'Configure CalDAV provider.',
                  ),
                  actions: ['Fix Matrix and Nextcloud provider setup.'],
                ),
              ),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      for (final term in [
        'Matrix',
        'homeserver',
        'Synapse',
        'Nextcloud',
        'WebDAV',
        'CalDAV',
        'provider',
      ]) {
        expect(find.textContaining(term), findsNothing);
      }
      expect(find.text('Chat'), findsOneWidget);
      expect(find.text('Files'), findsOneWidget);
      expect(find.text('Calendar'), findsOneWidget);
      expect(find.text('Workspace setup needs admin attention.'), findsWidgets);
    });

    testWidgets('recovers from load failures with guidance and retry', (
      tester,
    ) async {
      var loadAttempts = 0;
      await tester.pumpWidget(
        createTestApp(
          const FirstRunScreen(),
          overrides: [
            weaveAuthenticatedSessionProvider.overrideWithValue(
              AsyncData(
                WeaveAuthenticatedSession(
                  apiBaseUrl: Uri.parse('https://weave.test/api'),
                  accessToken: 'token',
                ),
              ),
            ),
            firstRunStatusRepositoryProvider.overrideWithValue(
              _RetryingFirstRunStatusRepository(() {
                loadAttempts += 1;
                if (loadAttempts == 1) {
                  throw Exception('backend unavailable');
                }
                return buildTestFirstRunStatus();
              }),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text(
          'We could not load your first-run status from the Weave backend.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Check your connection'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();

      expect(find.text('Your Weave workspace is ready'), findsOneWidget);
      expect(loadAttempts, 2);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });

    testWidgets('routes signed-out users back to sign-in recovery', (
      tester,
    ) async {
      final router = GoRouter(
        routes: [
          GoRoute(
            path: AppRoutes.firstRun,
            builder: (context, state) => const FirstRunScreen(),
          ),
          GoRoute(
            path: AppRoutes.signIn,
            builder: (context, state) => const Scaffold(
              body: Center(child: Text('Sign-in route ready')),
            ),
          ),
        ],
        initialLocation: AppRoutes.firstRun,
      );

      await tester.pumpWidget(
        createTestRouterApp(
          router,
          overrides: [
            firstRunStatusProvider.overrideWith(
              (ref) async => const FirstRunLoadResult.signedOut(),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Sign in to view your Weave first-run status.'),
        findsOneWidget,
      );
      expect(find.textContaining('active Weave SSO session'), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          'Sign in to view your Weave first-run status. We need an active Weave SSO session before we can check your profile, role, and module readiness. Action: Go to sign in',
        ),
        findsOneWidget,
      );

      await tester.tap(find.text('Go to sign in'));
      await tester.pumpAndSettle();

      expect(find.text('Sign-in route ready'), findsOneWidget);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}

class _RetryingFirstRunStatusRepository implements FirstRunStatusRepository {
  const _RetryingFirstRunStatusRepository(this._load);

  final FirstRunStatus? Function() _load;

  @override
  Future<FirstRunLoadResult> loadStatus() async {
    final status = _load();
    if (status == null) {
      return const FirstRunLoadResult.backendUnavailable('backend unavailable');
    }
    return FirstRunLoadResult.authenticated(status);
  }
}
