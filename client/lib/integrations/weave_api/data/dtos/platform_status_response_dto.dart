import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;

extension PlatformStatusResponseMapper on user_api.PlatformStatusResponse {
  MatrixE2eeDiagnostic toMatrixDiagnostic() {
    final matrixStatus = matrix;
    final e2eeStatus = matrixStatus?.e2ee;
    final boundary = matrixStatus?.backendBoundary;
    if (matrixStatus == null || e2eeStatus == null || boundary == null) {
      throw const AppFailure.unknown(
        'The backend returned an invalid Matrix diagnostic response.',
      );
    }
    return MatrixE2eeDiagnostic(
      e2eeEnabled: _requiredBool(matrixStatus.e2eeEnabled),
      status: _requiredText(e2eeStatus.status),
      serverReadableMessageContent: _requiredBool(
        boundary.serverReadableMessageContent,
      ),
      messageContentPolicy: _requiredText(boundary.messageContentPolicy),
      agentParticipation: _requiredText(boundary.agentParticipation),
      connectorWritePolicy: _requiredText(boundary.connectorWritePolicy),
    );
  }
}

String _requiredText(String? value) {
  if (value != null && value.trim().isNotEmpty) return value.trim();
  throw const AppFailure.unknown(
    'The backend returned an invalid platform status response.',
  );
}

bool _requiredBool(bool? value) {
  if (value != null) return value;
  throw const AppFailure.unknown(
    'The backend returned an invalid platform status response.',
  );
}
