import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';

void main() {
  group('ServiceEndpointDeriver', () {
    final deriver = ServiceEndpointDeriver();

    test('derives homelab defaults from the issuer host', () {
      final issuerUrl = deriver.parseIssuerUrl('https://auth.home.internal');
      final endpoints = deriver.derive(issuerUrl);

      expect(
        endpoints.matrixHomeserverUrl.toString(),
        'https://matrix.home.internal',
      );
      expect(
        endpoints.nextcloudBaseUrl.toString(),
        'https://files.home.internal',
      );
    });

    test('falls back to the full host when only two labels exist', () {
      final issuerUrl = deriver.parseIssuerUrl('https://example.com');
      final endpoints = deriver.derive(issuerUrl);

      expect(
        endpoints.matrixHomeserverUrl.toString(),
        'https://matrix.example.com',
      );
      expect(
        endpoints.nextcloudBaseUrl.toString(),
        'https://files.example.com',
      );
    });

    test('preserves HTTP scheme for local dev stacks', () {
      final issuerUrl = deriver.parseIssuerUrl('http://auth.home.internal');
      final endpoints = deriver.derive(issuerUrl);

      expect(
        endpoints.matrixHomeserverUrl.toString(),
        'http://matrix.home.internal',
      );
      expect(
        endpoints.nextcloudBaseUrl.toString(),
        'http://files.home.internal',
      );
    });

    test('accepts issuer URLs with HTTP scheme', () {
      expect(
        () => deriver.parseIssuerUrl('http://auth.home.internal'),
        returnsNormally,
      );
    });

    test('rejects issuer URLs with non-http/https schemes', () {
      expect(
        () => deriver.parseIssuerUrl('ftp://auth.home.internal'),
        throwsA(isA<AppFailure>()),
      );
    });

    test('rejects issuer URLs with query or fragment values', () {
      expect(
        () => deriver.parseIssuerUrl('https://auth.home.internal?foo=bar'),
        throwsA(isA<AppFailure>()),
      );
      expect(
        () => deriver.parseIssuerUrl('https://auth.home.internal#test'),
        throwsA(isA<AppFailure>()),
      );
    });
  });
}
