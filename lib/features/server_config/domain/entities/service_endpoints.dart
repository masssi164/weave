class ServiceEndpoints {
  const ServiceEndpoints({
    required this.matrixHomeserverUrl,
    required this.nextcloudBaseUrl,
  });

  final Uri matrixHomeserverUrl;
  final Uri nextcloudBaseUrl;

  ServiceEndpoints copyWith({Uri? matrixHomeserverUrl, Uri? nextcloudBaseUrl}) {
    return ServiceEndpoints(
      matrixHomeserverUrl: matrixHomeserverUrl ?? this.matrixHomeserverUrl,
      nextcloudBaseUrl: nextcloudBaseUrl ?? this.nextcloudBaseUrl,
    );
  }
}
