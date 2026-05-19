import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/matrix_e2ee_diagnostic.dart';

class PlatformStatusResponseDto {
  const PlatformStatusResponseDto({required this.matrix});

  factory PlatformStatusResponseDto.fromJson(Map<String, dynamic> json) {
    final matrix = json['matrix'];
    if (matrix is! Map<String, dynamic>) {
      throw const AppFailure.unknown(
        'The backend returned an invalid platform status response.',
      );
    }
    return PlatformStatusResponseDto(matrix: MatrixStatusDto.fromJson(matrix));
  }

  final MatrixStatusDto matrix;

  MatrixE2eeDiagnostic toMatrixDiagnostic() => matrix.toDiagnostic();
}

class MatrixStatusDto {
  const MatrixStatusDto({
    required this.e2eeEnabled,
    required this.e2eeStatus,
    required this.backendBoundary,
  });

  factory MatrixStatusDto.fromJson(Map<String, dynamic> json) {
    final e2ee = json['e2ee'];
    final backendBoundary = json['backendBoundary'];
    if (e2ee is! Map<String, dynamic> ||
        backendBoundary is! Map<String, dynamic>) {
      throw const AppFailure.unknown(
        'The backend returned an invalid Matrix diagnostic response.',
      );
    }
    return MatrixStatusDto(
      e2eeEnabled: _bool(json['e2eeEnabled']),
      e2eeStatus: MatrixE2eeStatusDto.fromJson(e2ee),
      backendBoundary: MatrixBackendBoundaryDto.fromJson(backendBoundary),
    );
  }

  final bool e2eeEnabled;
  final MatrixE2eeStatusDto e2eeStatus;
  final MatrixBackendBoundaryDto backendBoundary;

  MatrixE2eeDiagnostic toDiagnostic() {
    return MatrixE2eeDiagnostic(
      e2eeEnabled: e2eeEnabled,
      status: e2eeStatus.status,
      serverReadableMessageContent:
          backendBoundary.serverReadableMessageContent,
      messageContentPolicy: backendBoundary.messageContentPolicy,
      agentParticipation: backendBoundary.agentParticipation,
      connectorWritePolicy: backendBoundary.connectorWritePolicy,
    );
  }
}

class MatrixE2eeStatusDto {
  const MatrixE2eeStatusDto({required this.status});

  factory MatrixE2eeStatusDto.fromJson(Map<String, dynamic> json) {
    return MatrixE2eeStatusDto(status: _string(json['status']));
  }

  final String status;
}

class MatrixBackendBoundaryDto {
  const MatrixBackendBoundaryDto({
    required this.serverReadableMessageContent,
    required this.messageContentPolicy,
    required this.agentParticipation,
    required this.connectorWritePolicy,
  });

  factory MatrixBackendBoundaryDto.fromJson(Map<String, dynamic> json) {
    return MatrixBackendBoundaryDto(
      serverReadableMessageContent: _bool(json['serverReadableMessageContent']),
      messageContentPolicy: _string(json['messageContentPolicy']),
      agentParticipation: _string(json['agentParticipation']),
      connectorWritePolicy: _string(json['connectorWritePolicy']),
    );
  }

  final bool serverReadableMessageContent;
  final String messageContentPolicy;
  final String agentParticipation;
  final String connectorWritePolicy;
}

String _string(Object? value) {
  if (value is String && value.trim().isNotEmpty) {
    return value.trim();
  }
  throw const AppFailure.unknown(
    'The backend returned an invalid platform status response.',
  );
}

bool _bool(Object? value) {
  if (value is bool) {
    return value;
  }
  throw const AppFailure.unknown(
    'The backend returned an invalid platform status response.',
  );
}
