enum FilesConnectionStatus { misconfigured, disconnected, connected, invalid }

class FilesConnectionState {
  const FilesConnectionState({
    required this.status,
    this.baseUrl,
    this.accountLabel,
    this.message,
  });

  const FilesConnectionState.misconfigured({String? message})
    : this(status: FilesConnectionStatus.misconfigured, message: message);

  const FilesConnectionState.disconnected({Uri? baseUrl, String? message})
    : this(
        status: FilesConnectionStatus.disconnected,
        baseUrl: baseUrl,
        message: message,
      );

  const FilesConnectionState.connected({
    required Uri baseUrl,
    required String accountLabel,
  }) : this(
         status: FilesConnectionStatus.connected,
         baseUrl: baseUrl,
         accountLabel: accountLabel,
       );

  const FilesConnectionState.invalid({
    required Uri baseUrl,
    String? accountLabel,
    String? message,
  }) : this(
         status: FilesConnectionStatus.invalid,
         baseUrl: baseUrl,
         accountLabel: accountLabel,
         message: message,
       );

  final FilesConnectionStatus status;
  final Uri? baseUrl;
  final String? accountLabel;
  final String? message;

  bool get isConnected => status == FilesConnectionStatus.connected;
}
