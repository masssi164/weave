import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/config/feature_flags.dart';

void main() {
  test(
    'guest and connector previews remain feature-gated by default for the core product shell',
    () {
      expect(FeatureFlags.guestPortal, isFalse);
      expect(FeatureFlags.interopAdmin, isFalse);
      expect(FeatureFlags.migrationDryRun, isFalse);
      expect(FeatureFlags.hasFeatureGatedSurfaces, isFalse);
    },
  );
}
