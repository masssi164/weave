import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

// RUST_MATRIX_CORE_BRIDGE_CONTRACT
void main() {
  group('live-test Matrix root certificate', () {
    const pem = '''-----BEGIN CERTIFICATE-----
test-certificate
-----END CERTIFICATE-----
''';

    test('decodes the compile-time payload when explicitly enabled', () {
      expect(
        decodeMatrixLiveTestExtraRootCertificate(
          enabled: true,
          encodedCertificate: base64Encode(utf8.encode(pem)),
        ),
        pem,
      );
    });

    test('stays compiled off by default regardless of payload', () {
      expect(
        decodeMatrixLiveTestExtraRootCertificate(
          enabled: false,
          encodedCertificate: 'not-base64',
        ),
        isEmpty,
      );
    });

    test('fails closed when the enabled payload is missing or malformed', () {
      for (final encodedCertificate in <String>['', 'not-base64']) {
        expect(
          () => decodeMatrixLiveTestExtraRootCertificate(
            enabled: true,
            encodedCertificate: encodedCertificate,
          ),
          throwsA(
            isA<RustMatrixCoreBridgeException>().having(
              (error) => error.code,
              'code',
              'M_WEAVE_E2EE_TLS_ROOT',
            ),
          ),
        );
      }
    });

    test('rejects an oversized compile-time payload', () {
      final oversized = base64Encode(List<int>.filled(64 * 1024 + 1, 0x41));

      expect(
        () => decodeMatrixLiveTestExtraRootCertificate(
          enabled: true,
          encodedCertificate: oversized,
        ),
        throwsA(
          isA<RustMatrixCoreBridgeException>().having(
            (error) => error.code,
            'code',
            'M_WEAVE_E2EE_TLS_ROOT',
          ),
        ),
      );
    });
  });

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
      expect(descriptor.nativeLinked, isTrue);
      expect(descriptor.serverName, 'api.weave.test');
      expect(descriptor.supportedMatrixVersions, ['v1.18']);
      expect(descriptor.supportedEndpoints, contains(contains('/sync')));
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

  test('versions response is validated inside the Rust core', () async {
    const bridge = RustMatrixCoreBridge();
    final descriptor = await bridge.descriptor(serverName: 'api.weave.test');

    await bridge.validateVersions(
      responseJson: jsonEncode({
        'versions': ['v1.18'],
        'matrixCore': descriptor.toJson(),
      }),
      serverName: 'api.weave.test',
    );
  });

  test('whoami identity is validated inside the Rust core', () async {
    const bridge = RustMatrixCoreBridge();

    final userId = await bridge.parseWhoamiUserId(
      responseJson: jsonEncode({'user_id': '@user_alice:api.weave.test'}),
      serverName: 'api.weave.test',
    );

    expect(userId, '@user_alice:api.weave.test');
    await expectLater(
      bridge.parseWhoamiUserId(
        responseJson: jsonEncode({'user_id': '@user_alice:provider.invalid'}),
        serverName: 'api.weave.test',
      ),
      throwsA(isA<RustMatrixCoreBridgeException>()),
    );
  });
}
