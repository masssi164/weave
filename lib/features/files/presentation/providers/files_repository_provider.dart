import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/data/repositories/backend_files_repository.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/features/files/presentation/providers/legacy_direct_nextcloud_files_repository_provider.dart';

/// Enables the backend files facade path once the backend endpoints exist.
///
/// Keep this disabled by default so current merged live-stack auth/chat/files
/// read tests continue to exercise the compatibility adapter. Set
/// `--dart-define=WEAVE_USE_BACKEND_FILES_FACADE=true` or override this provider
/// in tests to verify the facade path without introducing new direct
/// Flutter-to-Nextcloud product calls.
final useBackendFilesFacadeProvider = Provider<bool>((ref) {
  return const bool.fromEnvironment('WEAVE_USE_BACKEND_FILES_FACADE');
});

final filesRepositoryProvider = Provider<FilesRepository>((ref) {
  if (ref.watch(useBackendFilesFacadeProvider)) {
    return const BackendFilesRepository();
  }

  return ref.watch(legacyDirectNextcloudFilesRepositoryProvider);
});
