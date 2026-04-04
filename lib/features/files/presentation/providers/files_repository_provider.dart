import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/data/repositories/nextcloud_files_repository.dart';
import 'package:weave/features/files/data/services/nextcloud_dav_client.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';
import 'package:weave/integrations/nextcloud/presentation/providers/nextcloud_provider.dart';

final filesRepositoryProvider = Provider<FilesRepository>((ref) {
  return NextcloudFilesRepository(
    connectionService: ref.watch(nextcloudConnectionServiceProvider),
    client: ref.watch(nextcloudDavClientProvider),
  );
});
