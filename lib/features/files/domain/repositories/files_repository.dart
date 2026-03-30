import 'package:weave/features/files/domain/entities/file_entry.dart';

abstract interface class FilesRepository {
  Future<List<FileEntry>> loadEntries();
}
