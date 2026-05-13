import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';

abstract interface class FilesRepository {
  Future<FilesConnectionState> restoreConnection();

  Future<FilesConnectionState> connect();

  Future<void> disconnect();

  Future<DirectoryListing> listDirectory(String path);

  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  });
}

abstract interface class FilesEntryMutationRepository {
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  });

  Future<void> deleteEntry(FileEntry entry);
}
