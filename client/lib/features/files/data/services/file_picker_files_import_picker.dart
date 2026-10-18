import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:weave/features/files/domain/entities/file_upload_request.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/services/files_import_picker.dart';

typedef FilePickerPickFile =
    Future<PlatformFile?> Function({String? dialogTitle});

class FilePickerFilesImportPicker implements FilesImportPicker {
  FilePickerFilesImportPicker({FilePickerPickFile? pickFile})
    : _pickFile = pickFile ?? FilePicker.pickFile;

  final FilePickerPickFile _pickFile;

  @override
  Future<FileUploadRequest?> pickFile() async {
    final PlatformFile? file;
    try {
      file = await _pickFile(dialogTitle: 'Choose a file to upload');
    } on PlatformException catch (error) {
      if (_isPickerCancellation(error)) return null;
      throw _mapPlatformException(error);
    }
    if (file == null) return null;
    try {
      final length = await file.length();
      if (length == null || length < 0) {
        throw const FilesFailure.storage(
          'Weave could not read the selected file. Choose a local file you can access.',
        );
      }
      return FileUploadRequest(
        fileName: file.name,
        sizeInBytes: length,
        byteStream: file.readAsByteStream(),
      );
    } on FilesFailure {
      rethrow;
    } on PlatformException catch (error) {
      throw _mapPlatformException(error);
    } on Object catch (error) {
      throw FilesFailure.storage(
        'Weave could not read the selected file. Choose a local file you can access.',
        cause: error,
      );
    }
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
