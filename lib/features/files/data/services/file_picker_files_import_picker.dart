import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';

class FilePickerFilesImportPicker implements FilesImportPicker {
  const FilePickerFilesImportPicker();

  @override
  Future<FileUploadRequest?> pickFile() async {
    final result = await FilePicker.pickFiles(
      allowMultiple: false,
      withData: false,
      withReadStream: true,
    );
    final file = result?.files.singleOrNull;
    if (file == null) {
      return null;
    }

    final byteStream = file.readStream;
    if (byteStream != null) {
      return FileUploadRequest(
        fileName: file.name,
        sizeInBytes: file.size,
        byteStream: byteStream,
      );
    }

    final bytes = file.bytes;
    if (bytes != null) {
      return FileUploadRequest(
        fileName: file.name,
        sizeInBytes: bytes.length,
        byteStream: Stream<List<int>>.value(bytes),
      );
    }

    throw const FilesFailure.unsupportedPlatform(
      'This device did not provide a readable file for upload.',
    );
  }
}

final filesImportPickerProvider = Provider<FilesImportPicker>((ref) {
  return const FilePickerFilesImportPicker();
});
