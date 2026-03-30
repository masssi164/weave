import 'package:weave/features/files/data/services/webdav_client.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/repositories/files_repository.dart';

class StubFilesRepository implements FilesRepository {
  const StubFilesRepository({required WebDavClient client}) : _client = client;

  final WebDavClient _client;

  @override
  Future<List<FileEntry>> loadEntries() async {
    final _ = _client;
    return const [];
  }
}
