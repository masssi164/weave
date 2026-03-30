/// Stub files entity to be replaced by WebDAV-backed domain models later.
class FileEntry {
  const FileEntry({
    required this.id,
    required this.name,
    required this.path,
    required this.isDirectory,
    required this.modifiedAt,
  });

  final String id;
  final String name;
  final String path;
  final bool isDirectory;
  final DateTime modifiedAt;
}
