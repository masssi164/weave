import 'package:weave/features/files/domain/entities/file_entry.dart';

class DirectoryListing {
  const DirectoryListing({
    required this.path,
    required this.entries,
    this.parentFileId,
    this.allowedActions = const {},
  });

  final String path;
  final List<FileEntry> entries;
  final String? parentFileId;
  final Set<String> allowedActions;

  bool get isRoot => path == '/';

  bool allows(String action) => allowedActions.contains(action);
}
