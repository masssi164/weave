import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_connection_state.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';

class FakeFilesRepository
    implements FilesRepository, FilesEntryMutationRepository {
  FakeFilesRepository({
    required this.connectionState,
    this.listings = const <String, DirectoryListing>{},
    this.listDirectoryHandler,
    this.uploadFileHandler,
    this.createFolderHandler,
    this.deleteEntryHandler,
  });

  final FilesConnectionState connectionState;
  final Map<String, DirectoryListing> listings;
  final Future<DirectoryListing> Function(String path)? listDirectoryHandler;
  final Future<void> Function(
    String directoryPath,
    FileUploadRequest request,
    FileUploadProgressCallback? onProgress,
  )?
  uploadFileHandler;
  final Future<FileEntry> Function(String parentPath, String name)?
  createFolderHandler;
  final Future<void> Function(FileEntry entry)? deleteEntryHandler;
  final List<String> requestedPaths = <String>[];

  @override
  Future<FilesConnectionState> connect() async => connectionState;

  @override
  Future<void> disconnect() async {}

  @override
  Future<DirectoryListing> listDirectory(String path) async {
    requestedPaths.add(path);
    final handler = listDirectoryHandler;
    if (handler != null) {
      return handler(path);
    }
    return listings[path] ?? const DirectoryListing(path: '/', entries: []);
  }

  @override
  Future<void> uploadFile(
    String directoryPath,
    FileUploadRequest request, {
    FileUploadProgressCallback? onProgress,
  }) async {
    await uploadFileHandler?.call(directoryPath, request, onProgress);
  }

  @override
  Future<FileEntry> createFolder({
    required String parentPath,
    required String name,
  }) {
    final handler = createFolderHandler;
    if (handler != null) {
      return handler(parentPath, name);
    }
    return Future<FileEntry>.value(
      FileEntry(
        id: '$parentPath/$name',
        name: name,
        path: parentPath == '/' ? '/$name' : '$parentPath/$name',
        isDirectory: true,
      ),
    );
  }

  @override
  Future<void> deleteEntry(FileEntry entry) async {
    await deleteEntryHandler?.call(entry);
  }

  @override
  Future<FilesConnectionState> restoreConnection() async => connectionState;
}
