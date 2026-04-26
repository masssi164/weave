import 'dart:async';

class FileUploadRequest {
  const FileUploadRequest({
    required this.fileName,
    required this.sizeInBytes,
    required this.byteStream,
  });

  final String fileName;
  final int sizeInBytes;
  final Stream<List<int>> byteStream;
}

typedef FileUploadProgressCallback =
    void Function(int uploadedBytes, int totalBytes);
