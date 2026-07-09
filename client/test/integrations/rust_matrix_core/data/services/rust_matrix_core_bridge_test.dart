import 'package:flutter_test/flutter_test.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

// RUST_MATRIX_CORE_BRIDGE_CONTRACT
void main() {
  test(
    'descriptor points Flutter at the OIDC gated Rust Matrix facade',
    () async {
      const bridge = RustMatrixCoreBridge();

      final descriptor = await bridge.descriptor();

      expect(descriptor.protocolSurface, 'matrix-client-server-facade');
      expect(descriptor.oidcGatekeeper, 'spring-boot-resource-server');
      expect(descriptor.northboundHomeserverDependency, isFalse);
      expect(
        descriptor.rustProtocolCore,
        'ruma-serde-serde_json-thiserror-tracing',
      );
      expect(descriptor.serverJniBoundary, 'server-jni-wrapper');
      expect(descriptor.flutterBridgeBoundary, 'flutter-rust-bridge');
      expect(descriptor.supportedMatrixVersions, ['v1.18']);
      expect(descriptor.isWeaveFacade, isTrue);
    },
  );

  test('descriptor json remains support safe', () async {
    const bridge = RustMatrixCoreBridge();

    final json = (await bridge.descriptor()).toJson().toString();

    expect(json, isNot(contains('access_token')));
    expect(json, isNot(contains('refresh_token')));
    expect(json, isNot(contains('Synapse')));
    expect(json, isNot(contains('providerAccessToken')));
  });
}
