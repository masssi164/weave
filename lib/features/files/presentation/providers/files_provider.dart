import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/files/data/repositories/stub_files_repository.dart';
import 'package:weave/features/files/data/services/webdav_client.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';

part 'files_provider.g.dart';

@Riverpod(keepAlive: true)
WebDavClient webDavClient(Ref ref) => const WebDavClient();

@Riverpod(keepAlive: true)
FilesRepository filesRepository(Ref ref) {
  final client = ref.watch(webDavClientProvider);
  return StubFilesRepository(client: client);
}

@riverpod
class FilesNotifier extends _$FilesNotifier {
  @override
  Future<List<FileEntry>> build() async {
    final repository = ref.watch(filesRepositoryProvider);
    return repository.loadEntries();
  }
}
