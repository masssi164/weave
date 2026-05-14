import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/services/files_export_saver.dart';

typedef FilePickerSaveFile =
    Future<String?> Function({
      String? dialogTitle,
      String? fileName,
      FileType type,
      List<String>? allowedExtensions,
      Uint8List? bytes,
    });

class FilePickerFilesExportSaver implements FilesExportSaver {
  FilePickerFilesExportSaver({FilePickerSaveFile? saveFile})
    : _saveFile = saveFile ?? _defaultSaveFile;

  final FilePickerSaveFile _saveFile;

  static Future<String?> _defaultSaveFile({
    String? dialogTitle,
    String? fileName,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Uint8List? bytes,
  }) {
    return FilePicker.saveFile(
      dialogTitle: dialogTitle,
      fileName: fileName,
      type: type,
      allowedExtensions: allowedExtensions,
      bytes: bytes,
    );
  }

  @override
  Future<FilesExportResult?> save(FileDownload download) async {
    try {
      final destination = await _saveFile(
        dialogTitle: 'Save exported Weave file',
        fileName: download.fileName,
        bytes: download.bytes,
      );
      if (destination == null) {
        return null;
      }
      return FilesExportResult(
        fileName: download.fileName,
        destination: destination,
      );
    } on PlatformException catch (error) {
      throw _mapPlatformException(error);
    }
  }

  FilesFailure _mapPlatformException(PlatformException error) {
    final code = error.code.toLowerCase();
    final message = error.message?.toLowerCase() ?? '';
    if (code.contains('cancel') || message.contains('cancel')) {
      return const FilesFailure.unknown('File export was cancelled.');
    }
    if (code.contains('permission') || message.contains('permission')) {
      return FilesFailure.storage(
        'Weave could not save the exported file there. Choose a user-visible folder you can write to.',
        cause: error,
      );
    }
    if (code.contains('unsupported') || code.contains('missing_plugin')) {
      return FilesFailure.unsupportedPlatform(
        'Native file export is unavailable on this platform.',
        cause: error,
      );
    }
    return FilesFailure.unknown(
      'Unable to save the exported file with the native picker.',
      cause: error,
    );
  }
}

final filesExportSaverProvider = Provider<FilesExportSaver>((ref) {
  return FilePickerFilesExportSaver();
});
