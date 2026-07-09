class RustMatrixCoreBridgeDescriptor {
  const RustMatrixCoreBridgeDescriptor({
    required this.protocolSurface,
    required this.oidcGatekeeper,
    required this.northboundHomeserverDependency,
    required this.rustProtocolCore,
    required this.serverJniBoundary,
    required this.flutterBridgeBoundary,
    required this.serverName,
    required this.supportedMatrixVersions,
  });

  final String protocolSurface;
  final String oidcGatekeeper;
  final bool northboundHomeserverDependency;
  final String rustProtocolCore;
  final String serverJniBoundary;
  final String flutterBridgeBoundary;
  final String serverName;
  final List<String> supportedMatrixVersions;

  bool get isWeaveFacade =>
      protocolSurface == 'matrix-client-server-facade' &&
      oidcGatekeeper == 'spring-boot-resource-server' &&
      northboundHomeserverDependency == false;

  Map<String, Object?> toJson() => {
    'protocolSurface': protocolSurface,
    'oidcGatekeeper': oidcGatekeeper,
    'northboundHomeserverDependency': northboundHomeserverDependency,
    'rustProtocolCore': rustProtocolCore,
    'serverJniBoundary': serverJniBoundary,
    'flutterBridgeBoundary': flutterBridgeBoundary,
    'serverName': serverName,
    'supportedMatrixVersions': supportedMatrixVersions,
  };
}

class RustMatrixCoreBridge {
  const RustMatrixCoreBridge();

  Future<RustMatrixCoreBridgeDescriptor> descriptor({
    String serverName = 'weave.local',
  }) async {
    return RustMatrixCoreBridgeDescriptor(
      protocolSurface: 'matrix-client-server-facade',
      oidcGatekeeper: 'spring-boot-resource-server',
      northboundHomeserverDependency: false,
      rustProtocolCore: 'ruma-serde-serde_json-thiserror-tracing',
      serverJniBoundary: 'server-jni-wrapper',
      flutterBridgeBoundary: 'flutter-rust-bridge',
      serverName: serverName,
      supportedMatrixVersions: const ['v1.18'],
    );
  }
}
