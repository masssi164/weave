import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';

class _RecordingHttpClient extends http.BaseClient {
  _RecordingHttpClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}

http.StreamedResponse _jsonResponse(
  Map<String, Object?> json, {
  int statusCode = 200,
}) {
  return http.StreamedResponse(
    Stream.value(utf8.encode(jsonEncode(json))),
    statusCode,
    headers: {'content-type': 'application/json'},
  );
}

void main() {
  group('HttpWeaveApiClient', () {
    test('fetches workspace capabilities with a bearer token', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'shellAccess': {'enabled': true, 'readiness': 'ready'},
            'chat': {'enabled': true, 'readiness': 'degraded'},
            'files': {'enabled': true, 'readiness': 'ready'},
            'calendar': {'enabled': false, 'readiness': 'unavailable'},
            'boards': {'enabled': false, 'readiness': 'unavailable'},
          });
        }),
      );

      final snapshot = await client.fetchWorkspaceCapabilities(
        baseUrl: Uri.parse('https://api.home.internal/api'),
        accessToken: 'token-123',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/v1/workspace/capabilities',
      );
      expect(capturedRequest.headers['Accept'], 'application/json');
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(
        snapshot.shellAccess.readiness,
        WorkspaceCapabilityReadiness.ready,
      );
      expect(snapshot.chat.readiness, WorkspaceCapabilityReadiness.degraded);
      expect(
        snapshot.calendar.readiness,
        WorkspaceCapabilityReadiness.unavailable,
      );
    });

    test(
      'preserves a base path when building the workspace endpoint',
      () async {
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse({
              'shellAccess': {'enabled': true, 'readiness': 'ready'},
              'chat': {'enabled': true, 'readiness': 'ready'},
              'files': {'enabled': true, 'readiness': 'ready'},
              'calendar': {'enabled': true, 'readiness': 'ready'},
              'boards': {'enabled': true, 'readiness': 'ready'},
            });
          }),
        );

        await client.fetchWorkspaceCapabilities(
          baseUrl: Uri.parse('https://home.internal/service/root'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://home.internal/service/root/api/v1/workspace/capabilities',
        );
      },
    );

    test(
      'does not duplicate the api segment for canonical API bases',
      () async {
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse({
              'shellAccess': {'enabled': true, 'readiness': 'ready'},
              'chat': {'enabled': true, 'readiness': 'ready'},
              'files': {'enabled': true, 'readiness': 'ready'},
              'calendar': {'enabled': true, 'readiness': 'ready'},
              'boards': {'enabled': true, 'readiness': 'ready'},
            });
          }),
        );

        await client.fetchWorkspaceCapabilities(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.local/api/v1/workspace/capabilities',
        );
      },
    );

    test('fetches Matrix E2EE diagnostics from platform status', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'matrix': {
              'status': 'up',
              'readiness': 'degraded',
              'message':
                  'Matrix chat is available, but E2EE gates are pending.',
              'action': 'Do not claim Matrix chat E2EE complete.',
              'federationEnabled': false,
              'e2eeEnabled': false,
              'e2ee': {
                'status': 'not_validated',
                'source': 'backend_runtime_flags_only',
                'encryptedRoomsValidated': false,
                'deviceVerificationValidated': false,
                'keyBackupValidated': false,
                'lostDeviceRecoveryValidated': false,
                'multiDeviceValidated': false,
                'accessibilityReviewed': false,
                'action': 'Do not claim Matrix chat E2EE complete.',
              },
              'backendBoundary': {
                'serverReadableMessageContent': false,
                'metadataReadable': [
                  'room_id',
                  'event_id',
                  'sender_id',
                  'origin_server_ts',
                ],
                'messageContentPolicy':
                    'encrypted_message_bodies_are_client_readable_only',
                'agentParticipation':
                    'blocked_until_explicit_consent_audit_and_matrix_device_trust_are_implemented',
                'connectorWritePolicy':
                    'fail_closed_until_audit_consent_and_matrix_e2ee_client_identity_are_implemented',
              },
            },
          });
        }),
      );

      final diagnostic = await client.fetchMatrixE2eeDiagnostic(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/platform/status',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(diagnostic.e2eeEnabled, isFalse);
      expect(diagnostic.status, 'not_validated');
      expect(diagnostic.keepsMessageBodiesOpaque, isTrue);
      expect(diagnostic.keepsAgentsAndConnectorsFailClosed, isTrue);
    });

    test(
      'fetches provider stack readiness through the backend facade',
      () async {
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse({
              'releaseStatus': 'provider-stack-contract-preview',
              'backendOwnedFacades': true,
              'flutterDirectProviderCallsAllowed': false,
              'supportSafe': true,
              'generatedAt': '2026-05-22T18:47:00Z',
              'providers': [
                {
                  'module': 'office',
                  'providerKey': 'onlyoffice-community',
                  'state': 'not_configured',
                  'readiness': 'not_configured',
                  'enabled': false,
                  'configured': false,
                  'readOnly': true,
                  'failClosed': true,
                  'supportSafe': true,
                  'paidFeaturesRequired': false,
                  'summary': 'Office provider is not configured.',
                  'supportedCapabilities': ['view', 'edit'],
                  'unsupportedOperations': [
                    'credential-bearing-url',
                    'raw-provider-errors',
                  ],
                  'supportSafeErrorCodes': ['office-provider-not-configured'],
                  'redactionPolicy': 'support-safe',
                  'candidates': ['onlyoffice-community'],
                  'diagnostics': {'defaultProvider': 'onlyoffice-community'},
                },
              ],
            });
          }),
        );

        final status = await client.fetchProviderStackStatus(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.local/api/providers/status',
        );
        expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
        expect(status.backendOwnedFacades, isTrue);
        expect(status.flutterDirectProviderCallsAllowed, isFalse);
        expect(status.supportSafe, isTrue);
        expect(status.allOptionalProvidersFailClosed, isTrue);
        expect(status.providers.single.module, 'office');
        expect(status.providers.single.failClosed, isTrue);
      },
    );

    test('fetches DevOps summary through the backend facade', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'workspaceId': 'workspace-default',
            'channelId': 'general',
            'releaseStatus': 'provider-stack-contract-preview',
            'readOnly': true,
            'paidFeaturesRequired': false,
            'supportSafe': true,
            'providerReadiness': [
              {
                'module': 'source-control',
                'providerKey': 'gitlab-ce',
                'state': 'disabled',
                'readiness': 'unavailable',
                'enabled': false,
                'configured': false,
                'readOnly': true,
                'failClosed': true,
                'supportSafe': true,
                'summary': 'GitLab CE is profiled out by default.',
                'supportedCapabilities': [],
                'unsupportedOperations': ['clone'],
              },
            ],
          });
        }),
      );

      final summary = await client.fetchDevopsSummary(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
        workspaceId: 'workspace-default',
        channelId: 'general',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/workspaces/workspace-default/channels/general/devops/summary',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(summary.isUnavailableFailClosed, isTrue);
      expect(summary.providerReadiness.single.providerKey, 'gitlab-ce');
    });

    test(
      'fetches Office capabilities and refuses launch fail-closed',
      () async {
        final requests = <http.BaseRequest>[];
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            requests.add(request);
            if (request.method == 'POST') {
              return _jsonResponse({'status': 'blocked'}, statusCode: 409);
            }
            return _jsonResponse({
              'releaseStatus': 'provider-stack-contract-preview',
              'enabled': false,
              'configured': false,
              'supportSafe': true,
              'launchMode': 'unavailable',
              'defaultProvider': 'onlyoffice-community',
              'providerReadiness': [
                {
                  'module': 'office',
                  'providerKey': 'onlyoffice-community',
                  'state': 'disabled',
                  'readiness': 'unavailable',
                  'enabled': false,
                  'configured': false,
                  'readOnly': true,
                  'failClosed': true,
                  'supportSafe': true,
                  'summary': 'Office provider is disabled.',
                  'supportedCapabilities': [],
                  'unsupportedOperations': ['launch'],
                },
              ],
              'supportedFileTypes': [],
              'permissions': {
                'canView': false,
                'canEdit': false,
                'canComment': false,
                'canReview': false,
                'canFillForms': false,
                'reason': 'office-provider-disabled',
              },
            });
          }),
        );

        final capabilities = await client.fetchOfficeCapabilities(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accessToken: 'token-123',
        );

        expect(
          requests.single.url.toString(),
          'https://api.weave.local/api/office/capabilities',
        );
        expect(capabilities.isUnavailableFailClosed, isTrue);

        await expectLater(
          client.launchOfficeSession(
            baseUrl: Uri.parse('https://api.weave.local/api'),
            accessToken: 'token-123',
            fileId: 'file-1',
            requestedMode: 'edit',
          ),
          throwsA(isA<AppFailure>()),
        );
        expect(
          requests.last.url.toString(),
          'https://api.weave.local/api/office/launch',
        );
        expect(requests.last.headers['Authorization'], 'Bearer token-123');
      },
    );

    test(
      'rejects unauthorized backend sessions with a dedicated failure',
      () async {
        for (final statusCode in [401, 403]) {
          final client = HttpWeaveApiClient(
            httpClient: _RecordingHttpClient((request) async {
              return _jsonResponse({
                'error': 'unauthorized',
              }, statusCode: statusCode);
            }),
          );

          await expectLater(
            () => client.fetchWorkspaceCapabilities(
              baseUrl: Uri.parse('https://api.home.internal/api'),
              accessToken: 'token-123',
            ),
            throwsA(
              isA<AppFailure>().having(
                (failure) => failure.message,
                'message',
                contains('rejected the current session'),
              ),
            ),
          );
        }
      },
    );

    test('throws when the backend returns a non-success response', () async {
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({'error': 'boom'}, statusCode: 503);
        }),
      );

      await expectLater(
        () => client.fetchWorkspaceCapabilities(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accessToken: 'token-123',
        ),
        throwsA(isA<AppFailure>()),
      );
    });
  });
}
