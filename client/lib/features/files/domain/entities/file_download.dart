import 'dart:typed_data';

class FileDownload {
  const FileDownload({
    required this.fileName,
    required this.bytes,
    this.strongValidator,
    this.mediaType,
  });

  final String? strongValidator;
  final String? mediaType;
  final String fileName;
  final Uint8List bytes;
}
