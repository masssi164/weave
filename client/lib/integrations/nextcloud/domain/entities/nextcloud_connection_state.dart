enum NextcloudConnectionStatus {
  misconfigured,
  disconnected,
  connected,
  invalid,
}

class NextcloudConnectionState {
  const NextcloudConnectionState({
    required this.status,
    this.baseUrl,
    this.accountLabel,
    this.message,
  });

  const NextcloudConnectionState.misconfigured({String? message})
    : this(status: NextcloudConnectionStatus.misconfigured, message: message);

  const NextcloudConnectionState.disconnected({Uri? baseUrl, String? message})
    : this(
        status: NextcloudConnectionStatus.disconnected,
        baseUrl: baseUrl,
        message: message,
      );

  const NextcloudConnectionState.connected({
    required Uri baseUrl,
    required String accountLabel,
  }) : this(
         status: NextcloudConnectionStatus.connected,
         baseUrl: baseUrl,
         accountLabel: accountLabel,
       );

  const NextcloudConnectionState.invalid({
    required Uri baseUrl,
    String? accountLabel,
    String? message,
  }) : this(
         status: NextcloudConnectionStatus.invalid,
         baseUrl: baseUrl,
         accountLabel: accountLabel,
         message: message,
       );

  final NextcloudConnectionStatus status;
  final Uri? baseUrl;
  final String? accountLabel;
  final String? message;

  bool get isConnected => status == NextcloudConnectionStatus.connected;
}
