/// A stub file entry model.
///
/// Will be replaced with the WebDAV file type once
/// the Nextcloud integration is built.
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
