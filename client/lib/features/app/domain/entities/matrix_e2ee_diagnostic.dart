class MatrixE2eeDiagnostic {
  const MatrixE2eeDiagnostic({
    required this.e2eeEnabled,
    required this.status,
    required this.serverReadableMessageContent,
    required this.messageContentPolicy,
    required this.agentParticipation,
    required this.connectorWritePolicy,
  });

  final bool e2eeEnabled;
  final String status;
  final bool serverReadableMessageContent;
  final String messageContentPolicy;
  final String agentParticipation;
  final String connectorWritePolicy;

  bool get isValidated => e2eeEnabled && status == 'validated';

  bool get keepsMessageBodiesOpaque => !serverReadableMessageContent;

  bool get keepsAgentsAndConnectorsFailClosed {
    final agentPolicy = agentParticipation.toLowerCase();
    final connectorPolicy = connectorWritePolicy.toLowerCase();
    return agentPolicy.contains('blocked') &&
        (connectorPolicy.contains('fail_closed') ||
            connectorPolicy.contains('fail-closed'));
  }
}
