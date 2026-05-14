import 'package:weave/features/files/domain/entities/file_download.dart';

class FilesExportResult {
  const FilesExportResult({required this.fileName, required this.destination});

  final String fileName;
  final String destination;
}

abstract interface class FilesExportSaver {
  Future<FilesExportResult?> save(FileDownload download);
}
