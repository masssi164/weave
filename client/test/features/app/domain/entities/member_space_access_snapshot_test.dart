import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';

void main() {
  test(
    'actual names disambiguate duplicates by stable visible-list ordinal without exposing references',
    () {
      const access = MemberSpaceAccessSnapshot(
        visibleSpaceRefs: {'opaque-z', 'opaque-a', 'private-other'},
        defaultSpaceReadable: false,
        displayNames: {
          'opaque-z': 'Research',
          'opaque-a': 'Research',
          'private-other': 'Design',
        },
      );
      expect(access.displayNameFor('opaque-a'), 'Research');
      expect(access.displayOrdinalFor('opaque-a'), 1);
      expect(access.displayNameFor('opaque-z'), 'Research');
      expect(access.displayOrdinalFor('opaque-z'), 2);
      expect(access.displayNameFor('private-other'), 'Design');
      expect(access.displayNameFor('revoked'), isNull);
      expect(access.hasChatSpace, isTrue);
      expect(access.hasDefaultSpace, isFalse);
    },
  );
  test('missing metadata cannot create a client-only Space alias', () {
    const access = MemberSpaceAccessSnapshot(
      visibleSpaceRefs: {'workspace-default'},
      defaultSpaceReadable: true,
    );
    expect(access.displayNameFor('workspace-default'), isNull);
    expect(access.hasDefaultSpace, isTrue);
  });
}
