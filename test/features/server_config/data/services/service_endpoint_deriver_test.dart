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
        'https://nextcloud.home.internal',
      );
      expect(
        endpoints.backendApiBaseUrl.toString(),
        'https://api.home.internal',
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
        'https://nextcloud.example.com',
      );
      expect(endpoints.backendApiBaseUrl.toString(), 'https://api.example.com');
    });

    test('rejects issuer URLs with non-https schemes', () {
      expect(
        () => deriver.parseIssuerUrl('http://auth.home.internal'),
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
