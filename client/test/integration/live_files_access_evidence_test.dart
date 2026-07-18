import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';

import '../../integration_test/helpers/live_files_access_evidence.dart';

void main() {
  test('accepts explicit forbidden as outsider denial evidence', () {
    expect(
      isWorkspaceResourceDeniedForEvidence(
        const FilesFailure.invalidCredentials(
          'Forbidden.',
          cause: HttpStatus.forbidden,
        ),
      ),
      isTrue,
    );
  });

  test('accepts concealment-safe not found as outsider denial evidence', () {
    expect(
      isWorkspaceResourceDeniedForEvidence(
        const FilesFailure.protocol('Not found.', cause: HttpStatus.notFound),
      ),
      isTrue,
    );
  });

  test('does not misclassify authentication or provider failures', () {
    expect(
      isWorkspaceResourceDeniedForEvidence(
        const FilesFailure.invalidCredentials(
          'Unauthorized.',
          cause: HttpStatus.unauthorized,
        ),
      ),
      isFalse,
    );
    expect(
      isWorkspaceResourceDeniedForEvidence(
        const FilesFailure.unknown(
          'Provider failed.',
          cause: HttpStatus.internalServerError,
        ),
      ),
      isFalse,
    );
  });
}
