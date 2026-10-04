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
        'https://api.home.internal/dav/files',
      );
      expect(
        endpoints.backendApiBaseUrl.toString(),
        'https://api.home.internal/api',
      );
    });

    test('derives shared hostnames from known auth-style issuer prefixes', () {
      final issuerUrl = deriver.parseIssuerUrl('https://sso.example.com');
      final endpoints = deriver.derive(issuerUrl);

      expect(
        endpoints.matrixHomeserverUrl.toString(),
        'https://matrix.example.com',
      );
      expect(
        endpoints.nextcloudBaseUrl.toString(),
        'https://api.example.com/dav/files',
      );
      expect(
        endpoints.backendApiBaseUrl.toString(),
        'https://api.example.com/api',
      );
    });

    test(
      'keeps the full issuer host when it is not an auth-style subdomain',
      () {
        final issuerUrl = deriver.parseIssuerUrl(
          'https://workspace.example.com',
        );
        final endpoints = deriver.derive(issuerUrl);

        expect(
          endpoints.matrixHomeserverUrl.toString(),
          'https://matrix.workspace.example.com',
        );
        expect(
          endpoints.nextcloudBaseUrl.toString(),
          'https://api.workspace.example.com/dav/files',
        );
        expect(
          endpoints.backendApiBaseUrl.toString(),
          'https://api.workspace.example.com/api',
        );
      },
    );

    test('preserves HTTP scheme for local dev stacks', () {
      final issuerUrl = deriver.parseIssuerUrl('http://auth.home.internal');
      final endpoints = deriver.derive(issuerUrl);

      expect(
        endpoints.matrixHomeserverUrl.toString(),
        'http://matrix.home.internal',
      );
      expect(
        endpoints.nextcloudBaseUrl.toString(),
        'http://api.home.internal/dav/files',
      );
      expect(
        endpoints.backendApiBaseUrl.toString(),
        'http://api.home.internal/api',
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
