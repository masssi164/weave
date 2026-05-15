import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/config/feature_flags.dart';

void main() {
  test(
    'guest and connector previews remain hidden by default for Release 1',
    () {
      expect(FeatureFlags.guestPortal, isFalse);
      expect(FeatureFlags.interopAdmin, isFalse);
      expect(FeatureFlags.migrationDryRun, isFalse);
      expect(FeatureFlags.hasPostReleaseSurfaces, isFalse);
    },
  );
}
