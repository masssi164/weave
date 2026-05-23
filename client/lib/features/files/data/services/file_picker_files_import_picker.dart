import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:weave/features/files/data/services/file_upload_request_from_path_stub.dart'
    if (dart.library.io) 'package:weave/features/files/data/services/file_upload_request_from_path_io.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';

typedef FilePickerPickFiles =
    Future<FilePickerResult?> Function({
      String? dialogTitle,
      bool allowMultiple,
      bool withData,
      bool withReadStream,
    });

class FilePickerFilesImportPicker implements FilesImportPicker {
  FilePickerFilesImportPicker({FilePickerPickFiles? pickFiles})
    : _pickFiles = pickFiles ?? _defaultPickFiles;

  final FilePickerPickFiles _pickFiles;

  static Future<FilePickerResult?> _defaultPickFiles({
    String? dialogTitle,
    bool allowMultiple = false,
    bool withData = false,
    bool withReadStream = false,
  }) {
    return FilePicker.pickFiles(
      dialogTitle: dialogTitle,
      allowMultiple: allowMultiple,
      withData: withData,
      withReadStream: withReadStream,
    );
  }

  @override
  Future<FileUploadRequest?> pickFile() async {
    final FilePickerResult? result;
    try {
      result = await _pickFiles(
        dialogTitle: 'Choose a file to upload',
        allowMultiple: false,
        withData: false,
        withReadStream: true,
      );
    } on PlatformException catch (error) {
      if (_isPickerCancellation(error)) {
        return null;
      }
      throw _mapPlatformException(error);
    }

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

    final pathRequest = fileUploadRequestFromPath(
      fileName: file.name,
      sizeInBytes: file.size,
      path: file.path,
    );
    if (pathRequest != null) {
      return pathRequest;
    }

    throw FilesFailure.unsupportedPlatform(
      'This device selected ${file.name}, but did not provide a readable file. '
      'Try choosing a local file or checking platform file permissions.',
    );
  }

  bool _isPickerCancellation(PlatformException error) {
    final code = error.code.toLowerCase();
    final message = error.message?.toLowerCase() ?? '';
    return code.contains('cancel') ||
        code.contains('abort') ||
        message.contains('cancel') ||
        message.contains('abort');
  }

  FilesFailure _mapPlatformException(PlatformException error) {
    final code = error.code.toLowerCase();
    final message = error.message?.toLowerCase() ?? '';
    final details = '${error.code} ${error.message ?? ''}'.trim();

    if (code.contains('permission') ||
        code.contains('denied') ||
        code.contains('security') ||
        message.contains('permission') ||
        message.contains('denied') ||
        message.contains('security')) {
      return FilesFailure.storage(
        'Weave could not access the selected file. Check file or document picker permissions and try again.',
        cause: error,
      );
    }

    if (code.contains('entitlement') || message.contains('entitlement')) {
      return FilesFailure.configuration(
        'This app build is missing file access permissions for the native picker.',
        cause: error,
      );
    }

    if (code.contains('unsupported') ||
        code.contains('unavailable') ||
        code.contains('missing_plugin')) {
      return FilesFailure.unsupportedPlatform(
        'Native file picking is unavailable on this platform.',
        cause: error,
      );
    }

    return FilesFailure.unknown(
      details.isEmpty
          ? 'Unable to choose a file for upload. Try again or pick a local file you can access.'
          : 'Unable to choose a file for upload ($details).',
      cause: error,
    );
  }
}

final filesImportPickerProvider = Provider<FilesImportPicker>((ref) {
  return FilePickerFilesImportPicker();
});
