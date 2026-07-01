import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/profile/domain/entities/user_profile.dart';
import 'package:weave/features/profile/domain/repositories/user_profile_repository.dart';
import 'package:weave/features/profile/presentation/profile_screen.dart';
import 'package:weave/features/profile/presentation/providers/user_profile_provider.dart';
import 'package:weave/features/profile/presentation/widgets/profile_summary_card.dart';

import '../../helpers/test_app.dart';

class _RecordingProfileRepository implements UserProfileRepository {
  UserProfileUpdate? savedUpdate;

  @override
  Future<UserProfile?> loadProfile() async => _profile;

  @override
  Future<UserProfile> updateProfile(UserProfileUpdate update) async {
    savedUpdate = update;
    return UserProfile(
      userId: _profile.userId,
      username: _profile.username,
      email: _profile.email,
      emailVerified: _profile.emailVerified,
      displayName: update.displayName ?? _profile.displayName,
      locale: update.locale ?? _profile.locale,
      timezone: update.timezone ?? _profile.timezone,
      roles: _profile.roles,
      groups: _profile.groups,
    );
  }
}

const _profile = UserProfile(
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

void main() {
  group('ProfileScreen', () {
    testWidgets('hosts profile editing on a dedicated route surface', (
      tester,
    ) async {
      await tester.pumpWidget(
        createTestApp(
          const ProfileScreen(),
          overrides: [
            userProfileProvider.overrideWith((ref) async => _profile),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Profile'), findsWidgets);
      expect(find.text('Weave profile'), findsOneWidget);
      expect(find.text('Edit profile'), findsOneWidget);
      expect(find.text('Save profile'), findsOneWidget);
    });
  });

  group('ProfileSummaryCard', () {
    testWidgets(
      'shows the authenticated profile from the Weave backend facade',
      (tester) async {
        await tester.pumpWidget(
          createTestApp(
            const SingleChildScrollView(child: ProfileSummaryCard()),
            overrides: [
              userProfileProvider.overrideWith((ref) async => _profile),
            ],
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Weave profile'), findsOneWidget);
        expect(find.text('Alice Example'), findsWidgets);
        expect(find.text('alice@example.test'), findsOneWidget);
        expect(find.text('Europe/Berlin'), findsWidgets);
        expect(find.text('Edit profile'), findsOneWidget);
        expect(find.text('Save profile'), findsOneWidget);
      },
    );

    testWidgets('saves accessible profile edits through the repository', (
      tester,
    ) async {
      final repository = _RecordingProfileRepository();
      await tester.pumpWidget(
        createTestApp(
          const SingleChildScrollView(child: ProfileSummaryCard()),
          overrides: [
            userProfileProvider.overrideWith((ref) async => _profile),
            userProfileRepositoryProvider.overrideWithValue(repository),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextFormField).at(0), 'Alice Updated');
      await tester.ensureVisible(find.byType(DropdownButtonFormField<String>));
      await tester.tap(find.byType(DropdownButtonFormField<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.text('German').last);
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Save profile'));
      await tester.tap(find.text('Save profile'));
      await tester.pumpAndSettle();

      expect(repository.savedUpdate?.displayName, 'Alice Updated');
      expect(repository.savedUpdate?.locale, 'de');
      expect(repository.savedUpdate?.timezone, 'Europe/Berlin');
      expect(find.text('Profile saved.'), findsOneWidget);
    });
  });
}
