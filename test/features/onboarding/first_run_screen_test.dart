import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/onboarding/domain/entities/first_run_status.dart';
import 'package:weave/features/onboarding/presentation/first_run_screen.dart';
import 'package:weave/features/onboarding/presentation/providers/first_run_status_provider.dart';

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
  });
}
