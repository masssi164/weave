import 'dart:io';

import 'package:weave/features/files/domain/entities/files_failure.dart';

bool isWorkspaceResourceDeniedForEvidence(FilesFailure failure) {
  return (failure.type == FilesFailureType.invalidCredentials &&
          failure.cause == HttpStatus.forbidden) ||
      (failure.type == FilesFailureType.protocol &&
          failure.cause == HttpStatus.notFound);
}
