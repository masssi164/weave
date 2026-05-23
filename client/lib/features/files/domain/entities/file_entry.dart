class FileEntry {
  const FileEntry({
    required this.id,
    required this.name,
    required this.path,
    required this.isDirectory,
    this.modifiedAt,
    this.sizeInBytes,
  });

  final String id;
  final String name;
  final String path;
  final bool isDirectory;
  final DateTime? modifiedAt;
  final int? sizeInBytes;
}
