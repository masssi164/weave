import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_capability_snapshot.dart';
import 'package:weave/features/app/domain/entities/workspace_home_snapshot.dart';
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

Map<String, Object?> _workspaceHomeActivityJson({
  String? activityRef,
  String activityHash =
      'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
  String domain = 'files',
  String action = 'files.webdav_write.completed',
  String occurredAt = '2026-07-12T10:00:00Z',
  String visibility = 'workspace',
  String? actorRefHash,
  String actorHash =
      'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff',
  bool actorIsCurrentUser = false,
  bool supportSafe = true,
}) {
  return <String, Object?>{
    'activityRef': activityRef ?? 'activity:sha256:$activityHash',
    'domain': domain,
    'action': action,
    'occurredAt': occurredAt,
    'visibility': visibility,
    'actorRefHash': actorRefHash ?? 'sha256:$actorHash',
    'actorIsCurrentUser': actorIsCurrentUser,
    'supportSafe': supportSafe,
  };
}

Map<String, Object?> _capability({
  required bool enabled,
  required String readiness,
  String policyState = 'allowed',
  List<String> grantedCapabilities = const <String>[],
}) {
  return {
    'enabled': enabled,
    'readiness': readiness,
    'policyState': policyState,
    'grantedCapabilities': grantedCapabilities,
  };
}

Map<String, Object?> _workspaceCapabilitiesJson({
  Map<String, Object?> overrides = const <String, Object?>{},
}) {
  return {
    'shellAccess': _capability(enabled: true, readiness: 'ready'),
    'chat': _capability(
      enabled: true,
      readiness: 'ready',
      grantedCapabilities: const ['chat.read', 'chat.send'],
    ),
    'files': _capability(enabled: true, readiness: 'ready'),
    'calendar': _capability(enabled: true, readiness: 'ready'),
    'boards': _capability(enabled: true, readiness: 'ready'),
    'meetingsCalls': _capability(
      enabled: false,
      readiness: 'unavailable',
      policyState: 'disabled',
    ),
    'documentsCollaboration': _capability(
      enabled: false,
      readiness: 'unavailable',
      policyState: 'disabled',
    ),
    'decisionsEvidence': _capability(enabled: true, readiness: 'ready'),
    'manualsHelp': _capability(enabled: true, readiness: 'ready'),
    'releaseEvidence': _capability(enabled: true, readiness: 'ready'),
    'adminControlPlane': _capability(enabled: true, readiness: 'ready'),
    'agentRuntimeControl': _capability(
      enabled: false,
      readiness: 'unavailable',
      policyState: 'disabled',
    ),
    ...overrides,
  };
}

Map<String, Object?> _organizationManifestJson({
  String organizationAuthUrl = 'https://auth.weave.test/realms/weave',
  bool supportSafe = true,
  bool providerConfigurationExposed = false,
  bool diagnosticsExposed = false,
}) {
  return {
    'manifestVersion': 'org-manifest-v1',
    'organizationId': 'weave-dogfood',
    'displayName': 'Weave Dogfood',
    'organizationAuthUrl': organizationAuthUrl,
    'generatedAt': '2026-05-24T12:00:00Z',
    'supportSafe': supportSafe,
    'providerConfigurationExposed': providerConfigurationExposed,
    'diagnosticsExposed': diagnosticsExposed,
    'whitelistingOwner': 'organization-admin-console',
    'clientResponsibilities': [
      'accept organization auth URL, invite link, or deep link',
      'complete SSO with the selected identity provider',
      'consume effective organization manifest and capability states',
      'render only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later member states',
    ],
    'adminConsoleResponsibilities': [
      'create and bootstrap organizations',
      'select and configure identity providers and category providers',
      'manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics',
      'manage users, groups, roles, capability profiles, and deny-by-default policy',
      'own provider, tool, and agent whitelisting plus privacy/compliance risk notes',
      'audit organization-wide defaults and administrative changes',
    ],
    'memberCapabilityStates': {
      'idm-rbac': 'available',
      'chat-channels': 'available',
      'files-docs': 'available',
      'calendar-events': 'degraded',
      'boards-tasks': 'disabled_by_policy',
      'meetings': 'not_configured',
      'forms-contacts': 'coming_later',
    },
    'capabilities': _workspaceCapabilitiesJson(
      overrides: {
        'calendar': _capability(enabled: true, readiness: 'degraded'),
        'boards': _capability(
          enabled: true,
          readiness: 'blocked',
          policyState: 'policy_blocked',
        ),
      },
    ),
  };
}

void main() {
  group('HttpWeaveApiClient', () {
    test('reconciles identity access without sending provider input', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'state': 'access_updated',
            'sessionRefreshRequired': true,
          });
        }),
      );

      final result = await client.reconcileIdentitySession(
        baseUrl: Uri.parse('https://api.home.internal/api'),
        accessToken: 'token-123',
      );

      expect(result, IdentitySessionReconcileResult.accessUpdated);
      expect(capturedRequest.method, 'POST');
      expect(
        capturedRequest.url.toString(),
        'https://api.home.internal/api/v1/identity/session/reconcile',
      );
      expect(capturedRequest.headers['Accept'], 'application/json');
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(capturedRequest.headers['Content-Type'], isNull);
      expect((capturedRequest as http.Request).body, isEmpty);
    });

    test('rejects inconsistent identity reconciliation results', () async {
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({
            'state': 'unchanged',
            'sessionRefreshRequired': true,
          });
        }),
      );

      await expectLater(
        () => client.reconcileIdentitySession(
          baseUrl: Uri.parse('https://api.home.internal/api'),
          accessToken: 'token-123',
        ),
        throwsA(
          isA<AppFailure>().having(
            (failure) => failure.message,
            'message',
            contains('inconsistent identity-session'),
          ),
        ),
      );
    });

    test('fetches workspace capabilities with a bearer token', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse(
            _workspaceCapabilitiesJson(
              overrides: {
                'chat': _capability(enabled: true, readiness: 'degraded'),
                'calendar': _capability(
                  enabled: false,
                  readiness: 'unavailable',
                  policyState: 'disabled',
                ),
                'boards': _capability(
                  enabled: false,
                  readiness: 'unavailable',
                  policyState: 'disabled',
                ),
              },
            ),
          );
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
      'fetches org manifest for member client without admin console leakage',
      () async {
        // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse(_organizationManifestJson());
          }),
        );

        final snapshot = await client.fetchOrganizationManifest(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.test/api/v1/organization/manifest',
        );
        expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
        expect(snapshot.safeForMemberClient, isTrue);
        expect(snapshot.whitelistingOwnedByAdminConsole, isTrue);
        expect(
          snapshot.clientResponsibilities,
          contains(
            'consume effective organization manifest and capability states',
          ),
        );
        expect(
          snapshot.adminConsoleResponsibilities,
          contains(
            'own provider, tool, and agent whitelisting plus privacy/compliance risk notes',
          ),
        );
        expect(
          snapshot.memberCapabilityStates['calendar-events'],
          MemberCapabilityState.degraded,
        );
        expect(
          snapshot.memberCapabilityStates['boards-tasks'],
          MemberCapabilityState.disabledByPolicy,
        );
        expect(
          snapshot.memberCapabilityStates['meetings'],
          MemberCapabilityState.notConfigured,
        );
        expect(
          snapshot.memberCapabilityStates['forms-contacts'],
          MemberCapabilityState.comingLater,
        );
        expect(snapshot.capabilities.chat.grants('chat.send'), isTrue);
      },
    );

    test('rejects missing organization identity in org manifest', () async {
      for (final patch in [
        {'organizationId': ''},
        {'displayName': ''},
        {'organizationId': null},
        {'displayName': null},
      ]) {
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            return _jsonResponse({..._organizationManifestJson(), ...patch});
          }),
        );

        expect(
          () => client.fetchOrganizationManifest(
            baseUrl: Uri.parse('https://api.weave.test/api'),
            accessToken: 'token-123',
          ),
          throwsA(isA<AppFailure>()),
        );
      }
    });

    test('rejects invalid organization auth URL in org manifest', () async {
      for (final invalidAuthUrl in [
        'configured-by-organization-admin',
        'https:///realms/weave',
        'https://user:pass@auth.weave.test/realms/weave',
        'https://auth.weave.test/realms/weave?provider=raw',
        'https://auth.weave.test/realms/weave#diagnostics',
      ]) {
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            return _jsonResponse(
              _organizationManifestJson(organizationAuthUrl: invalidAuthUrl),
            );
          }),
        );

        expect(
          () => client.fetchOrganizationManifest(
            baseUrl: Uri.parse('https://api.weave.test/api'),
            accessToken: 'token-123',
          ),
          throwsA(isA<AppFailure>()),
        );
      }
    });

    test(
      'rejects org manifests that expose provider setup or diagnostics',
      () async {
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            return _jsonResponse(
              _organizationManifestJson(
                supportSafe: false,
                providerConfigurationExposed: true,
                diagnosticsExposed: true,
              )..addAll({
                'providerUrls': [
                  'https://matrix.weave.test',
                  'https://files.weave.test',
                ],
                'diagnostics': {
                  'matrix': 'matrix.weave.test',
                  'files': 'files.weave.test',
                },
              }),
            );
          }),
        );

        expect(
          () => client.fetchOrganizationManifest(
            baseUrl: Uri.parse('https://api.weave.test/api'),
            accessToken: 'token-123',
          ),
          throwsA(isA<AppFailure>()),
        );
      },
    );

    test('fetches Weave Home through backend facade', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'version': 2,
            'readiness': 'degraded',
            'summary': 'Weave Home is usable, with setup actions remaining.',
            'supportSafe': true,
            'sections': [
              {
                'key': 'recent-channels',
                'title': 'Recent channels',
                'readiness': 'ready',
                'summary': 'Project conversations are available.',
                'itemCount': 1,
                'accessible': true,
                'productRoute': 'weave://home/channels',
              },
              {
                'key': 'open-tasks',
                'title': 'Open tasks',
                'readiness': 'degraded',
                'summary': 'Board writes stay gated behind audit.',
                'itemCount': 0,
                'accessible': true,
                'productRoute': 'weave://home/tasks',
              },
            ],
            'actions': [
              {
                'key': 'review-open-tasks',
                'label': 'Review open tasks',
                'productRoute': 'weave://home/tasks',
                'reason': 'Board writes stay gated behind audit.',
              },
            ],
            'recentActivity': [
              _workspaceHomeActivityJson(
                actorIsCurrentUser: true,
                activityHash:
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                actorHash:
                    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              ),
            ],
          });
        }),
      );

      final snapshot = await client.fetchWorkspaceHome(
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.test/api/v1/workspace/home',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(snapshot.supportSafe, isTrue);
      expect(snapshot.sections.first.key, 'recent-channels');
      expect(snapshot.sections.first.productRoute, 'weave://home/channels');
      expect(snapshot.actions.single.productRoute, 'weave://home/tasks');
      expect(snapshot.recentActivity.single.supportSafe, isTrue);
      expect(
        snapshot.recentActivity.single.domain,
        WorkspaceHomeActivityDomain.files,
      );
      expect(
        snapshot.recentActivity.single.action,
        WorkspaceHomeActivityAction.filesWebDavWriteCompleted,
      );
      expect(snapshot.recentActivity.single.actorIsCurrentUser, isTrue);
      expect(snapshot.hasActionableWork, isTrue);
    });

    test('rejects unsafe Weave Home provider leakage', () async {
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({
            'version': 2,
            'readiness': 'ready',
            'summary': 'Raw provider URL https://provider.example leaked.',
            'supportSafe': true,
            'sections': [],
            'actions': [],
            'recentActivity': [],
          });
        }),
      );

      expect(
        () => client.fetchWorkspaceHome(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        ),
        throwsA(isA<AppFailure>()),
      );
    });

    test('rejects unsafe or unknown Weave Home activity fields', () async {
      final unsafeActivities = <Map<String, Object?>>[
        _workspaceHomeActivityJson(activityRef: 'provider:file-123'),
        _workspaceHomeActivityJson(actorRefHash: 'user:member@example.test'),
        _workspaceHomeActivityJson(action: 'files.unknown.completed'),
        _workspaceHomeActivityJson(domain: 'provider-files'),
        _workspaceHomeActivityJson(visibility: 'context:private-id'),
        _workspaceHomeActivityJson(occurredAt: '2026-07-12T10:00:00'),
        _workspaceHomeActivityJson(supportSafe: false),
      ];

      for (final activity in unsafeActivities) {
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            return _jsonResponse({
              'version': 2,
              'readiness': 'ready',
              'summary': 'Weave Home is ready.',
              'supportSafe': true,
              'sections': [],
              'actions': [],
              'recentActivity': [activity],
            });
          }),
        );

        await expectLater(
          () => client.fetchWorkspaceHome(
            baseUrl: Uri.parse('https://api.weave.test/api'),
            accessToken: 'token-123',
          ),
          throwsA(isA<AppFailure>()),
        );
      }
    });

    test('rejects duplicate Weave Home activity references', () async {
      final activity = _workspaceHomeActivityJson();
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({
            'version': 2,
            'readiness': 'ready',
            'summary': 'Weave Home is ready.',
            'supportSafe': true,
            'sections': [],
            'actions': [],
            'recentActivity': [activity, activity],
          });
        }),
      );

      await expectLater(
        () => client.fetchWorkspaceHome(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        ),
        throwsA(isA<AppFailure>()),
      );
    });

    test(
      'preserves a base path when building the workspace endpoint',
      () async {
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse(_workspaceCapabilitiesJson());
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
            return _jsonResponse(_workspaceCapabilitiesJson());
          }),
        );

        await client.fetchWorkspaceCapabilities(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.test/api/v1/workspace/capabilities',
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
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.test/api/platform/status',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(diagnostic.e2eeEnabled, isFalse);
      expect(diagnostic.status, 'not_validated');
      expect(diagnostic.keepsMessageBodiesOpaque, isTrue);
      expect(diagnostic.keepsAgentsAndConnectorsFailClosed, isTrue);
    });

    test(
      'fetches provider stack readiness without direct provider URLs',
      () async {
        late http.BaseRequest capturedRequest;
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            capturedRequest = request;
            return _jsonResponse({
              'releaseStatus': 'provider-stack-contract-v1',
              'providerConfigSource':
                  'admin-control-plane-selected-provider-mappings',
              'bootstrapDefaultsAreSuggestionsOnly': true,
              'adminSelectedMappingsRequired': true,
              'backendOwnedFacades': true,
              'flutterDirectProviderCallsAllowed': false,
              'supportSafe': true,
              'categories': [
                {
                  'category': 'identity-idm',
                  'label': 'identity/IDM',
                  'contract': {
                    'category': 'identity-idm',
                    'featureCapabilities': [
                      'identity.sign_in',
                      'identity.groups',
                    ],
                    'defaultAdapters': ['keycloak-realm'],
                    'externalAdapters': ['entra-id', 'generic-oidc'],
                    'choiceModels': [
                      {
                        'choiceModel': 'recommended_self_hosted_default',
                        'adapters': ['keycloak-realm'],
                        'adminRiskNotes': [
                          'recommended sovereign/default posture',
                        ],
                        'recommended': true,
                      },
                      {
                        'choiceModel': 'external_existing_provider',
                        'adapters': ['entra-id'],
                        'adminRiskNotes': [
                          'admin records privacy and compliance risk outside member UX',
                        ],
                        'recommended': false,
                      },
                    ],
                    'adapterModules': ['identity-realm', 'matrix-auth'],
                    'stableMemberImpactStates': [
                      'available',
                      'disabled_by_policy',
                      'not_configured',
                      'degraded',
                      'unavailable',
                      'coming_later',
                    ],
                    'adminSelectable': true,
                    'normalMembersConfigureProviders': false,
                  },
                  'readiness': 'ready',
                  'providerRealityLevel': 'release_ready',
                  'memberCapabilityState': 'available',
                  'realityLevelRemediation':
                      'Release-ready provider: keep evidence current.',
                  'policyState': 'allowed',
                  'memberImpact': 'Sign-in is available.',
                  'modules': ['identity-realm', 'matrix-auth'],
                  'providerCandidates': ['keycloak', 'oidc'],
                  'selectedProviderKey': 'keycloak-realm',
                  'choiceModel': 'recommended_self_hosted_default',
                  'selectedByAdmin': true,
                  'bootstrapSuggestionOnly': false,
                  'lossyMappingNotes': [],
                  'adapterEvidence': [
                    {
                      'domain': 'identity-idm',
                      'adapterKey': 'keycloak-realm',
                      'configured': true,
                      'reachable': true,
                      'health': 'ready',
                      'providerRealityLevel': 'release_ready',
                      'failClosed': true,
                      'supportSafeDiagnostics': {
                        'providerState': 'ready',
                        'supportSafe': true,
                        'secretsReturned': false,
                        'rawProviderErrorsReturned': false,
                        'rawProviderError':
                            'Authorization: Bearer should-not-render',
                      },
                      'evidenceTimestamp': '2026-05-25T18:00:00Z',
                    },
                  ],
                  'diagnostics': {
                    'providerCount': 2,
                    'allSupportSafe': true,
                    'secretsReturned': false,
                    'rawProviderErrorsReturned': false,
                  },
                },
                {
                  'category': 'agent-runtime-control',
                  'label': 'Agent Runtime Control',
                  'contract': {
                    'category': 'agent-runtime-control',
                    'featureCapabilities': ['agent-runtime.entitled'],
                    'defaultAdapters': ['weaver-openclaw'],
                    'externalAdapters': [],
                    'choiceModels': [
                      {
                        'choiceModel': 'recommended_self_hosted_default',
                        'adapters': ['weaver-openclaw'],
                        'adminRiskNotes': ['disabled by default'],
                        'recommended': true,
                      },
                    ],
                    'adapterModules': [],
                    'stableMemberImpactStates': [
                      'available',
                      'disabled_by_policy',
                      'not_configured',
                      'degraded',
                      'unavailable',
                      'coming_later',
                    ],
                    'adminSelectable': true,
                    'normalMembersConfigureProviders': false,
                  },
                  'readiness': 'policy_blocked',
                  'providerRealityLevel': 'contract_only',
                  'memberCapabilityState': 'disabled_by_policy',
                  'realityLevelRemediation':
                      'Contract-only candidate remains unavailable.',
                  'policyState': 'policy_blocked',
                  'memberImpact':
                      'Agent Runtime Control is disabled by workspace policy.',
                  'modules': [],
                  'providerCandidates': [],
                  'selectedProviderKey': 'awaiting_admin_selection',
                  'choiceModel': 'not_selected',
                  'selectedByAdmin': false,
                  'bootstrapSuggestionOnly': true,
                  'lossyMappingNotes': [],
                  'diagnostics': {
                    'providerCount': 0,
                    'allSupportSafe': true,
                    'secretsReturned': false,
                    'rawProviderErrorsReturned': false,
                  },
                },
              ],
              'providers': [
                {
                  'module': 'office',
                  'providerKey': 'onlyoffice-community',
                  'state': 'disabled',
                  'readiness': 'fail-closed',
                  'enabled': false,
                  'configured': false,
                  'readOnly': true,
                  'failClosed': true,
                  'supportSafe': true,
                  'paidFeaturesRequired': false,
                  'summary': 'Disabled until configured behind backend facade.',
                  'supportedCapabilities': ['documents'],
                  'unsupportedOperations': ['launch'],
                  'supportSafeErrorCodes': ['PROVIDER_DISABLED'],
                  'redactionPolicy': 'no raw provider errors',
                  'candidates': ['ONLYOFFICE Docs Community'],
                  'providerRealityLevel': 'migration_dry_run',
                },
                {
                  'module': 'meetings',
                  'providerKey': 'livekit',
                  'state': 'not_configured',
                  'readiness': 'fail-closed',
                  'enabled': false,
                  'configured': false,
                  'readOnly': false,
                  'failClosed': true,
                  'supportSafe': true,
                  'paidFeaturesRequired': false,
                  'summary': 'LiveKit SFU readiness is fail-closed.',
                  'supportedCapabilities': ['sfu-configuration-readiness'],
                  'unsupportedOperations': ['livekit-api-secret-exposure'],
                  'supportSafeErrorCodes': ['rtc-authorization-required'],
                  'redactionPolicy': 'booleans only',
                  'candidates': ['livekit'],
                  'providerRealityLevel': 'rollback_ready',
                  'diagnostics': {
                    'activeSfuAdapter': 'livekit',
                    'livekitUrlConfigured': true,
                    'apiKeyConfigured': true,
                    'apiSecretConfigured': true,
                    'tokenEndpointConfigured': true,
                    'tokenEndpoint': 'https://token-broker.internal/livekit',
                    'rawProviderError': 'Authorization: Bearer leaked',
                    'secretsReturned': false,
                  },
                },
              ],
            });
          }),
        );

        final snapshot = await client.fetchProviderStackStatus(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.test/api/providers/status',
        );
        expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
        expect(snapshot.failClosed, isTrue);
        expect(
          snapshot.providerConfigSource,
          'admin-control-plane-selected-provider-mappings',
        );
        expect(snapshot.bootstrapDefaultsAreSuggestionsOnly, isTrue);
        expect(snapshot.adminSelectedMappingsRequired, isTrue);
        expect(snapshot.categories, hasLength(2));
        expect(snapshot.categories.first.category, 'identity-idm');
        expect(
          snapshot.categories.first.contract.featureCapabilities,
          containsAll(['identity.sign_in', 'identity.groups']),
        );
        expect(
          snapshot.categories.first.contract.defaultAdapters,
          contains('keycloak-realm'),
        );
        expect(
          snapshot.categories.first.contract.externalAdapters,
          containsAll(['entra-id', 'generic-oidc']),
        );
        expect(
          snapshot.categories.first.contract.keepsMemberSemanticsStable,
          isTrue,
        );
        expect(
          snapshot.categories.first.contract.choiceModels.map(
            (choiceModel) => choiceModel.choiceModel,
          ),
          containsAll([
            'recommended_self_hosted_default',
            'external_existing_provider',
          ]),
        );
        expect(
          snapshot.categories.first.contract.choiceModels.first.recommended,
          isTrue,
        );
        expect(
          snapshot.categories.first.contract.choiceModels.last.adminRiskNotes
              .join(' '),
          contains('privacy'),
        );
        expect(
          snapshot.categories.first.readiness,
          ProviderCategoryReadiness.ready,
        );
        expect(
          snapshot.categories.first.providerRealityLevel,
          ProviderRealityLevel.releaseReady,
        );
        expect(snapshot.categories.first.memberCapabilityState, 'available');
        expect(snapshot.categories.first.memberAvailable, isTrue);
        expect(snapshot.categories.first.selectedProviderKey, 'keycloak-realm');
        expect(snapshot.categories.first.selectedByAdmin, isTrue);
        expect(snapshot.categories.first.bootstrapSuggestionOnly, isFalse);
        expect(snapshot.categories.first.supportSafe, isTrue);
        expect(
          snapshot.categories.first.adapterEvidence.single.adapterKey,
          'keycloak-realm',
        );
        expect(
          snapshot.categories.first.adapterEvidence.single.configured,
          isTrue,
        );
        expect(
          snapshot.categories.first.adapterEvidence.single.reachable,
          isTrue,
        );
        expect(
          snapshot.categories.first.adapterEvidence.single.providerRealityLevel,
          ProviderRealityLevel.releaseReady,
        );
        expect(
          snapshot
              .categories
              .first
              .adapterEvidence
              .single
              .supportSafeDiagnostics,
          isNot(contains('rawProviderError')),
        );
        expect(snapshot.categories.last.category, 'agent-runtime-control');
        expect(
          snapshot.categories.last.readiness,
          ProviderCategoryReadiness.policyBlocked,
        );
        expect(
          snapshot.categories.last.providerRealityLevel,
          ProviderRealityLevel.contractOnly,
        );
        expect(snapshot.categories.last.memberAvailable, isFalse);
        expect(snapshot.providers.first.module, 'office');
        expect(snapshot.providers.first.failClosed, isTrue);
        expect(
          snapshot.providers.first.providerRealityLevel,
          ProviderRealityLevel.migrationDryRun,
        );
        expect(snapshot.providers.first.available, isFalse);
        final meetings = snapshot.providers.singleWhere(
          (provider) => provider.module == 'meetings',
        );
        expect(meetings.providerKey, 'livekit');
        expect(
          meetings.providerRealityLevel,
          ProviderRealityLevel.rollbackReady,
        );
        expect(meetings.available, isFalse);
        expect(meetings.diagnostics['livekitUrlConfigured'], isTrue);
        expect(meetings.diagnostics['apiSecretConfigured'], isTrue);
        expect(meetings.diagnostics['tokenEndpointConfigured'], isTrue);
        expect(meetings.diagnostics['secretsReturned'], isFalse);
        expect(meetings.diagnostics, isNot(contains('tokenEndpoint')));
        expect(meetings.diagnostics, isNot(contains('rawProviderError')));
      },
    );

    test(
      'keeps provider category contract optional during version skew',
      () async {
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            return _jsonResponse({
              'releaseStatus': 'contract-preview',
              'backendOwnedFacades': true,
              'flutterDirectProviderCallsAllowed': false,
              'supportSafe': true,
              'categories': [
                {
                  'category': 'chat',
                  'label': 'Chat',
                  'readiness': 'ready',
                  'policyState': 'allowed',
                  'memberImpact': 'usable',
                  'modules': ['matrix'],
                  'providerCandidates': ['synapse'],
                  'diagnostics': {
                    'secretsReturned': false,
                    'rawProviderErrorsReturned': false,
                  },
                },
              ],
              'providers': [],
            });
          }),
        );

        final snapshot = await client.fetchProviderStackStatus(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
        );

        expect(snapshot.categories.single.category, 'chat');
        expect(snapshot.categories.single.contract.category, 'chat');
        expect(snapshot.categories.single.contract.adminSelectable, isTrue);
        expect(
          snapshot.categories.single.contract.normalMembersConfigureProviders,
          isFalse,
        );
      },
    );

    test('redacts sensitive provider summary text from DTOs', () async {
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({
            'releaseStatus': 'contract-preview',
            'backendOwnedFacades': true,
            'flutterDirectProviderCallsAllowed': false,
            'supportSafe': true,
            'providers': [
              {
                'module': 'source-control',
                'providerKey': 'gitlab-ce-foss',
                'state': 'not_configured',
                'readiness': 'fail-closed',
                'enabled': false,
                'configured': false,
                'readOnly': true,
                'failClosed': true,
                'supportSafe': true,
                'paidFeaturesRequired': false,
                'summary': 'token leaked from https://gitlab.example.test',
                'redactionPolicy': 'secret should not be visible',
                'diagnostics': {
                  'safeFlag': true,
                  'nested': {
                    'details': ['ok', 'https://gitlab.example.test/token'],
                  },
                  'tokenHeader': 'Bearer provider-token-123',
                },
              },
            ],
          });
        }),
      );

      final snapshot = await client.fetchProviderStackStatus(
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
      );

      expect(snapshot.providers.single.summary, isNot(contains('token')));
      expect(snapshot.providers.single.summary, isNot(contains('https://')));
      expect(
        snapshot.providers.single.redactionPolicy,
        isNot(contains('secret')),
      );
      expect(snapshot.providers.single.diagnostics['safeFlag'], isTrue);
      expect(snapshot.providers.single.diagnostics.toString(), contains('ok'));
      expect(
        snapshot.providers.single.diagnostics.toString(),
        isNot(contains('provider-token-123')),
      );
      expect(
        snapshot.providers.single.diagnostics.toString(),
        isNot(contains('https://gitlab.example.test')),
      );
    });

    test('fetches DevOps and Office readiness through backend facades', () async {
      final requests = <String>[];
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          requests.add(request.url.toString());
          if (request.url.path.endsWith('/devops/summary')) {
            return _jsonResponse({
              'workspaceId': 'workspace-1',
              'channelId': 'channel-1',
              'releaseStatus': 'contract-preview',
              'readOnly': true,
              'paidFeaturesRequired': false,
              'supportSafe': true,
              'providerReadiness': [],
            });
          }
          return _jsonResponse({
            'releaseStatus': 'contract-preview',
            'enabled': false,
            'configured': false,
            'supportSafe': true,
            'launchMode': 'disabled',
            'defaultProvider': 'onlyoffice-community',
            'providerReadiness': [],
            'supportedFileTypes': [],
          });
        }),
      );

      final devops = await client.fetchDevopsSummary(
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
        workspaceId: 'workspace-1',
        channelId: 'channel-1',
      );
      final office = await client.fetchOfficeCapabilities(
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
      );

      expect(devops.supportSafe, isTrue);
      expect(office.launchAvailable, isFalse);
      expect(
        requests,
        contains(
          'https://api.weave.test/api/workspaces/workspace-1/channels/channel-1/devops/summary',
        ),
      );
      expect(
        requests,
        contains('https://api.weave.test/api/office/capabilities'),
      );
    });

    test('maps Office launch 503 errors to fail-closed results', () async {
      late http.BaseRequest capturedRequest;
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          capturedRequest = request;
          return _jsonResponse({
            'code': 'office-provider-not-configured',
            'message':
                'Office launch is fail-closed until the backend provider is configured.',
            'details': <String, Object?>{},
            'requestId': 'request-123',
            'supportRef': 'support:request-123',
          }, statusCode: 503);
        }),
      );

      final launch = await client.launchOfficeSession(
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
        fileId: 'file-1',
        requestedMode: 'view',
      );

      expect(capturedRequest.method, 'POST');
      expect(
        capturedRequest.url.toString(),
        'https://api.weave.test/api/office/launch',
      );
      expect(launch.launched, isFalse);
      expect(launch.failClosed, isTrue);
      expect(launch.errorCode, 'office-provider-not-configured');
      expect(launch.message, isNot(contains('token-123')));
    });

    test(
      'maps non-JSON Office launch 503 errors to fail-closed results',
      () async {
        final client = HttpWeaveApiClient(
          httpClient: _RecordingHttpClient((request) async {
            return http.StreamedResponse(Stream.value(const <int>[]), 503);
          }),
        );

        final launch = await client.launchOfficeSession(
          baseUrl: Uri.parse('https://api.weave.test/api'),
          accessToken: 'token-123',
          fileId: 'file-1',
          requestedMode: 'view',
        );

        expect(launch.launched, isFalse);
        expect(launch.failClosed, isTrue);
        expect(launch.errorCode, 'office-launch-fail-closed');
        expect(launch.message, isNot(contains('token-123')));
      },
    );

    test('maps successful Office launch sessions', () async {
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({
            'sessionId': 'session-1',
            'launchMode': 'view',
            'providerKey': 'onlyoffice-community',
            'expiresAt': '2026-05-22T20:00:00Z',
            'grantedPermissions': ['view'],
          });
        }),
      );

      final launch = await client.launchOfficeSession(
        baseUrl: Uri.parse('https://api.weave.test/api'),
        accessToken: 'token-123',
        fileId: 'file-1',
        requestedMode: 'view',
      );

      expect(launch.launched, isTrue);
      expect(launch.providerKey, 'onlyoffice-community');
      expect(launch.expiresAt, DateTime.parse('2026-05-22T20:00:00Z'));
      expect(launch.grantedPermissions, ['view']);
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
