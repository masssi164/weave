import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/files/domain/entities/file_download.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/features/files/domain/services/files_export_saver.dart';

typedef FilePickerSaveFile =
    Future<Uri?> Function({
      required String fileName,
      required Uint8List bytes,
      String mimeType,
      String? dialogTitle,
    });

class FilePickerFilesExportSaver implements FilesExportSaver {
  FilePickerFilesExportSaver({FilePickerSaveFile? saveFile})
    : _saveFile = saveFile ?? FilePicker.saveFile;

  final FilePickerSaveFile _saveFile;

  @override
  Future<FilesExportResult?> save(FileDownload download) async {
    try {
      final destination = await _saveFile(
        dialogTitle: 'Save exported Weave file',
        fileName: download.fileName,
        bytes: download.bytes,
        mimeType: download.mediaType ?? 'application/octet-stream',
      );
      if (destination == null) {
        return null;
      }
      return FilesExportResult(
        fileName: download.fileName,
        destination: destination.scheme == 'file'
            ? destination.toFilePath()
            : destination.toString(),
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
