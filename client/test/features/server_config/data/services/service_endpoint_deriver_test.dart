import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/server_config/data/services/service_endpoint_deriver.dart';

void main() {
  group('ServiceEndpointDeriver', () {
    final deriver = ServiceEndpointDeriver();

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

    test('accepts only the canonical backend api base path', () {
      expect(
        deriver.parseServiceUrl(
          'https://api.home.internal/api',
          fieldName: 'the backend API URL',
        ),
        Uri.parse('https://api.home.internal/api'),
      );
      expect(
        () => deriver.parseServiceUrl(
          'https://api.home.internal',
          fieldName: 'the backend API URL',
        ),
        throwsA(isA<AppFailure>()),
      );
    });

    test('accepts an independent Matrix homeserver origin only', () {
      expect(
        deriver.parseMatrixHomeserverUrl('https://matrix.example.com/'),
        Uri.parse('https://matrix.example.com'),
      );
      expect(
        () => deriver.parseMatrixHomeserverUrl('https://api.example.com/api'),
        throwsA(isA<AppFailure>()),
      );
      expect(
        () => deriver.parseMatrixHomeserverUrl(
          'https://user:secret@matrix.example.com',
        ),
        throwsA(isA<AppFailure>()),
      );
    });
  });
}
