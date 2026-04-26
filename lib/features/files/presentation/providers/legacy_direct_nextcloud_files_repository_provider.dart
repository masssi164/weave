import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_dav_client.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/integrations/nextcloud/presentation/providers/nextcloud_provider.dart';

/// Compatibility adapter for the pre-facade live stack.
///
/// Product files flows should move to the backend facade (issue #85). This
/// provider keeps today's merged live-stack files-read tests working until the
/// backend files facade is implemented in masssi164/weave-backend#24/#26/#27.
final legacyDirectNextcloudFilesRepositoryProvider = Provider<FilesRepository>((
  ref,
) {
  return NextcloudFilesRepository(
    connectionService: ref.watch(nextcloudConnectionServiceProvider),
    client: ref.watch(nextcloudDavClientProvider),
  );
});
