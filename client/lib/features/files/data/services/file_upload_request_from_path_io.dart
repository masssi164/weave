import 'dart:io';

import 'package:weave/features/files/domain/entities/file_upload_request.dart';

FileUploadRequest? fileUploadRequestFromPath({
  required String fileName,
  required int sizeInBytes,
  required String? path,
}) {
  if (path == null || path.isEmpty) {
    return null;
  }

  final file = File(path);
  return FileUploadRequest(
    fileName: fileName,
    sizeInBytes: sizeInBytes,
    byteStream: file.openRead(),
  );
}
