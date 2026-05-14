import 'dart:typed_data';

class FileDownload {
  const FileDownload({required this.fileName, required this.bytes});

  final String fileName;
  final Uint8List bytes;
}
