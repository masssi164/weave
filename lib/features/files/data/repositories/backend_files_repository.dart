import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';

/// Backend-facade repository seam for MVP files product flows.
///
/// The MVP contract is for Flutter to use `weave-backend` files APIs while the
/// backend owns the Nextcloud WebDAV/OCS integration. The concrete HTTP facade
/// endpoints are still blocked on masssi164/weave-backend#24/#26/#27, so this
/// implementation deliberately reports the facade as unavailable instead of
/// adding new direct Flutter-to-Nextcloud product calls.
class BackendFilesRepository implements FilesRepository {
  const BackendFilesRepository();

  static const unavailableMessage =
      'Files are waiting for the Weave backend files facade.';

  @override
  Future<FilesConnectionState> restoreConnection() async {
    return const FilesConnectionState.misconfigured(
      message: unavailableMessage,
    );
  }

  @override
  Future<FilesConnectionState> connect() async {
    throw const FilesFailure.configuration(unavailableMessage);
  }

  @override
  Future<void> disconnect() async {
    // No local Nextcloud session is owned by the backend-facade path.
  }

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    throw const FilesFailure.configuration(unavailableMessage);
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    throw const FilesFailure.configuration(unavailableMessage);
  }
}
