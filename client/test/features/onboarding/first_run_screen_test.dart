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
              (ref) async => buildTestFirstRunStatus(),
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
        find.textContaining('no separate Matrix or Nextcloud'),
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
              (ref) async => buildTestFirstRunStatus(
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
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Owner/admin setup responsibilities'), findsOneWidget);
      expect(
        find.textContaining('normal users should only need'),
        findsOneWidget,
      );
    });

    testWidgets('shows pending and degraded module actions accessibly', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const FirstRunScreen(),
          overrides: [
            firstRunStatusProvider.overrideWith(
              (ref) async => buildTestFirstRunStatus(
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
                  message: 'Matrix chat provisioning is pending.',
                  action: 'Wait briefly, then retry.',
                ),
                nextcloud: const FirstRunModuleStatus(
                  state: FirstRunProvisioningState.degraded,
                  message:
                      'Nextcloud files/calendar is available but degraded.',
                  action: 'Ask an admin to check service health.',
                ),
                actions: ['Verify your email, then refresh status.'],
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
        findsWidgets,
      );
      expect(find.text('Continue to chat'), findsNothing);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
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
          overrides: [firstRunStatusProvider.overrideWith((ref) async => null)],
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
  Future<FirstRunStatus?> loadStatus() async => _load();
}
