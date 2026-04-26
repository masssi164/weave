import 'package:weave/features/files/domain/entities/file_upload_request.dart';

abstract interface class FilesImportPicker {
  Future<FileUploadRequest?> pickFile();
}
