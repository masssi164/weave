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

    test('fetches provider stack status from backend-owned facade', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'releaseStatus': 'preview',
            'backendOwnedFacades': true,
            'flutterDirectProviderCallsAllowed': false,
            'supportSafe': true,
            'generatedAt': '2026-05-22T18:00:00Z',
            'providers': [
              _providerJson(
                module: 'office',
                providerKey: 'onlyoffice-disabled',
                state: 'not_configured',
                enabled: false,
                configured: false,
                summary: 'Office provider is not configured.',
              ),
            ],
          });
        }),
      );

      final status = await client.fetchProviderStatus(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/providers/status',
      );
      expect(status.enforcesBackendFacades, isTrue);
      expect(status.providers.single.shouldFailClosed, isTrue);
      expect(status.providers.single.summary, isNot(contains('token')));
    });

    test('fetches DevOps summary without direct provider endpoints', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'workspaceId': 'workspace-default',
            'channelId': 'general-home.internal',
            'releaseStatus': 'preview',
            'readOnly': true,
            'paidFeaturesRequired': false,
            'supportSafe': true,
            'providerReadiness': [
              _providerJson(
                module: 'source-control',
                providerKey: 'forgejo',
                state: 'ready',
                summary: 'Source control summary is ready.',
              ),
            ],
            'linkedProjects': [
              {'id': 'project-1'},
            ],
            'repositories': [],
            'openIssues': [],
            'mergeRequests': [],
            'pipelines': [],
            'releases': [],
          });
        }),
      );

      final summary = await client.fetchDevopsSummary(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
        workspaceId: 'workspace-default',
        channelId: 'general-home.internal',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/workspaces/workspace-default/channels/general-home.internal/devops/summary',
      );
      expect(summary.isAvailable, isTrue);
      expect(summary.linkedProjectCount, 1);
    });

    test(
      'fetches Office capabilities and fails closed when disabled',
      () async {
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse({
              'releaseStatus': 'preview',
              'enabled': false,
              'configured': false,
              'supportSafe': true,
              'launchMode': 'disabled',
              'defaultProvider': 'onlyoffice',
              'providerReadiness': [
                _providerJson(
                  module: 'office',
                  providerKey: 'onlyoffice',
                  state: 'not_configured',
                  enabled: false,
                  configured: false,
                  summary: 'Office provider is disabled.',
                ),
              ],
              'candidates': [],
              'capabilities': {
                'view': false,
                'edit': false,
                'comment': false,
                'review': false,
                'formFill': false,
              },
              'supportedFileTypes': [],
              'permissions': {
                'canView': false,
                'canEdit': false,
                'canComment': false,
                'canReview': false,
                'canFillForms': false,
                'reason': 'office-provider-disabled',
              },
              'lockSessionReadiness': {
                'documentLocks': 'disabled',
                'sessionTokens': 'disabled',
                'callbackVerification': 'disabled',
                'supportSafe': true,
              },
            });
          }),
        );

        final capabilities = await client.fetchOfficeCapabilities(
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.local/api/office/capabilities',
        );
        expect(capabilities.shouldFailClosed, isTrue);
        expect(capabilities.canLaunchEdit, isFalse);
      },
    );

    test('posts Office launch through backend facade', () async {
      late http.BaseRequest capturedRequest;
      late String capturedBody;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          capturedBody = await request
              .finalize()
              .transform(utf8.decoder)
              .join();
          return _jsonResponse({
            'sessionId': 'office-session-1',
            'launchMode': 'view',
            'providerKey': 'onlyoffice',
            'expiresAt': '2026-05-22T18:10:00Z',
            'grantedPermissions': ['view'],
          });
        }),
      );

      final session = await client.launchOfficeSession(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
        fileId: 'file-1',
        requestedMode: 'view',
      );

      expect(capturedRequest.method, 'POST');
      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/office/launch',
      );
      expect(jsonDecode(capturedBody), {
        'fileId': 'file-1',
        'requestedMode': 'view',
      });
      expect(session.sessionId, 'office-session-1');
    });
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

Map<String, Object?> _providerJson({
  required String module,
  required String providerKey,
  required String state,
  required String summary,
  bool enabled = true,
  bool configured = true,
}) {
  return {
    'module': module,
    'providerKey': providerKey,
    'state': state,
    'readiness': state,
    'enabled': enabled,
    'configured': configured,
    'readOnly': true,
    'failClosed': true,
    'supportSafe': true,
    'paidFeaturesRequired': false,
    'summary': summary,
    'supportedCapabilities': [],
    'unsupportedOperations': [],
    'supportSafeErrorCodes': [],
    'redactionPolicy': 'support-safe',
    'candidates': [],
    'diagnostics': {},
  };
}
