import 'package:flutter_riverpod/flutter_riverpod.dart';

class PostReleaseFeatureFlags {
  const PostReleaseFeatureFlags({
    this.guestPortal = false,
    this.interopStatus = false,
    this.migrationDryRun = false,
  });

  final bool guestPortal;
  final bool interopStatus;
  final bool migrationDryRun;

  bool get hasEnabledShell => guestPortal || interopStatus || migrationDryRun;
}

final postReleaseFeatureFlagsProvider = Provider<PostReleaseFeatureFlags>(
  (ref) => const PostReleaseFeatureFlags(),
);
