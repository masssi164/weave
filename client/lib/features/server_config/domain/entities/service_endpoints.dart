class ServiceEndpoints {
  const ServiceEndpoints({
    required this.matrixHomeserverUrl,
    required this.backendApiBaseUrl,
  });

  final Uri matrixHomeserverUrl;
  final Uri backendApiBaseUrl;

  ServiceEndpoints copyWith({
    Uri? matrixHomeserverUrl,
    Uri? backendApiBaseUrl,
  }) {
    return ServiceEndpoints(
      matrixHomeserverUrl: matrixHomeserverUrl ?? this.matrixHomeserverUrl,
      backendApiBaseUrl: backendApiBaseUrl ?? this.backendApiBaseUrl,
    );
  }
}
