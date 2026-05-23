class ServiceEndpoints {
  const ServiceEndpoints({
    required this.matrixHomeserverUrl,
    required this.nextcloudBaseUrl,
    required this.backendApiBaseUrl,
  });

  final Uri matrixHomeserverUrl;
  final Uri nextcloudBaseUrl;
  final Uri backendApiBaseUrl;

  ServiceEndpoints copyWith({
    Uri? matrixHomeserverUrl,
    Uri? nextcloudBaseUrl,
    Uri? backendApiBaseUrl,
  }) {
    return ServiceEndpoints(
      matrixHomeserverUrl: matrixHomeserverUrl ?? this.matrixHomeserverUrl,
      nextcloudBaseUrl: nextcloudBaseUrl ?? this.nextcloudBaseUrl,
      backendApiBaseUrl: backendApiBaseUrl ?? this.backendApiBaseUrl,
    );
  }
}
