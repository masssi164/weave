import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/profile/presentation/widgets/profile_summary_card.dart';

import '../../helpers/test_app.dart';

void main() {
  group('ProfileSummaryCard', () {
    testWidgets('shows the authenticated profile from the Weave backend facade', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const ProfileSummaryCard(),
          overrides: [
            userProfileProvider.overrideWith((ref) async {
              return const UserProfile(
                userId: 'user-123',
                username: 'alice',
                email: 'alice@example.test',
                emailVerified: true,
                displayName: 'Alice Example',
                locale: 'en',
                timezone: 'Europe/Berlin',
                roles: ['member'],
                groups: ['workspace-default'],
              );
            }),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Weave profile'), findsOneWidget);
      expect(find.text('Alice Example'), findsOneWidget);
      expect(find.text('alice@example.test'), findsOneWidget);
      expect(find.text('Europe/Berlin'), findsOneWidget);
      expect(
        find.text(
          'Profile editing is prepared in the app, but saving changes is blocked until the backend exposes PATCH /api/profile.',
        ),
        findsOneWidget,
      );
    });
  });
}
