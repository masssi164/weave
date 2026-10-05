class FileEntry {
  const FileEntry({
    required this.id,
    required this.name,
    required this.path,
    required this.isDirectory,
    this.modifiedAt,
    this.sizeInBytes,
    this.revision,
    this.allowedActions = const {},
  });

  final String id;
  final String name;
  final String path;
  final bool isDirectory;
  final DateTime? modifiedAt;
  final int? sizeInBytes;
  final String? revision;
  final Set<String> allowedActions;

  bool allows(String action) => allowedActions.contains(action);
}
