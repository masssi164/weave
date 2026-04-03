import 'package:weave/features/files/domain/entities/file_entry.dart';

class DirectoryListing {
  const DirectoryListing({required this.path, required this.entries});

  final String path;
  final List<FileEntry> entries;

  bool get isRoot => path == '/';
}
