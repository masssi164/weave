import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/app/domain/entities/organization_manifest_snapshot.dart';
import 'package:weave/features/app/domain/entities/provider_stack_snapshot.dart';
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

Map<String, Object?> _organizationManifestJson({
  String organizationAuthUrl = 'https://auth.weave.local/realms/weave',
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
      'render only ready, disabled, degraded, or policy-blocked member states',
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
      'identity-idm': 'ready',
      'chat': 'ready',
      'files': 'ready',
      'calendar': 'degraded',
      'boards-tasks': 'policy-blocked',
      'weaver': 'disabled',
    },
    'capabilities': {
      'shellAccess': {
        'enabled': true,
        'readiness': 'ready',
        'policyState': 'allowed',
      },
      'chat': {
        'enabled': true,
        'readiness': 'ready',
        'policyState': 'allowed',
        'grantedCapabilities': ['chat.read', 'chat.send'],
      },
      'files': {
        'enabled': true,
        'readiness': 'ready',
        'policyState': 'allowed',
      },
      'calendar': {
        'enabled': true,
        'readiness': 'degraded',
        'policyState': 'allowed',
      },
      'boards': {
        'enabled': true,
        'readiness': 'blocked',
        'policyState': 'policy_blocked',
      },
      'weaver': {
        'enabled': false,
        'readiness': 'unavailable',
        'policyState': 'disabled',
      },
    },
  };
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
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.local/api/v1/organization/manifest',
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
          snapshot.memberCapabilityStates['calendar'],
          MemberCapabilityState.degraded,
        );
        expect(
          snapshot.memberCapabilityStates['boards-tasks'],
          MemberCapabilityState.policyBlocked,
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
            baseUrl: Uri.parse('https://api.weave.local/api'),
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
        'https://user:pass@auth.weave.local/realms/weave',
        'https://auth.weave.local/realms/weave?provider=raw',
        'https://auth.weave.local/realms/weave#diagnostics',
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
            baseUrl: Uri.parse('https://api.weave.local/api'),
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
                  'https://matrix.weave.local',
                  'https://files.weave.local',
                ],
                'diagnostics': {
                  'matrix': 'matrix.weave.local',
                  'files': 'files.weave.local',
                },
              }),
            );
          }),
        );

        expect(
          () => client.fetchOrganizationManifest(
            baseUrl: Uri.parse('https://api.weave.local/api'),
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
            'version': 1,
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
          });
        }),
      );

      final snapshot = await client.fetchWorkspaceHome(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
      );

      expect(
        capturedRequest.url.toString(),
        'https://api.weave.local/api/v1/workspace/home',
      );
      expect(capturedRequest.headers['Authorization'], 'Bearer token-123');
      expect(snapshot.supportSafe, isTrue);
      expect(snapshot.sections.first.key, 'recent-channels');
      expect(snapshot.sections.first.productRoute, 'weave://home/channels');
      expect(snapshot.actions.single.productRoute, 'weave://home/tasks');
      expect(snapshot.hasActionableWork, isTrue);
    });

    test('rejects unsafe Weave Home provider leakage', () async {
      final client = HttpWeaveApiClient(
        httpClient: _RecordingHttpClient((request) async {
          return _jsonResponse({
            'version': 1,
            'readiness': 'ready',
            'summary': 'Raw provider URL https://provider.example leaked.',
            'supportSafe': false,
            'sections': [],
            'actions': [],
          });
        }),
      );

      expect(
        () => client.fetchWorkspaceHome(
          baseUrl: Uri.parse('https://api.weave.local/api'),
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
                      'usable',
                      'disabled',
                      'degraded',
                      'policy-blocked',
                    ],
                    'adminSelectable': true,
                    'normalMembersConfigureProviders': false,
                  },
                  'readiness': 'ready',
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
                  'category': 'weaver',
                  'label': 'Weaver',
                  'contract': {
                    'category': 'weaver',
                    'featureCapabilities': ['weaver.enabled'],
                    'defaultAdapters': ['weaver-runtime-disabled'],
                    'externalAdapters': ['openclaw-governed-runtime'],
                    'choiceModels': [
                      {
                        'choiceModel': 'recommended_self_hosted_default',
                        'adapters': ['weaver-runtime-disabled'],
                        'adminRiskNotes': ['disabled by default'],
                        'recommended': true,
                      },
                    ],
                    'adapterModules': [],
                    'stableMemberImpactStates': [
                      'usable',
                      'disabled',
                      'degraded',
                      'policy-blocked',
                    ],
                    'adminSelectable': true,
                    'normalMembersConfigureProviders': false,
                  },
                  'readiness': 'policy_blocked',
                  'policyState': 'policy_blocked',
                  'memberImpact': 'Weaver is disabled by workspace policy.',
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
                  'summary': 'LiveKit readiness is fail-closed.',
                  'supportedCapabilities': ['join-token-broker'],
                  'unsupportedOperations': ['livekit-api-secret-exposure'],
                  'supportSafeErrorCodes': ['meetings-token-unavailable'],
                  'redactionPolicy': 'booleans only',
                  'candidates': ['livekit'],
                  'diagnostics': {
                    'activeProvider': 'livekit',
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
          baseUrl: Uri.parse('https://api.weave.local/api'),
          accessToken: 'token-123',
        );

        expect(
          capturedRequest.url.toString(),
          'https://api.weave.local/api/providers/status',
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
          snapshot
              .categories
              .first
              .adapterEvidence
              .single
              .supportSafeDiagnostics,
          isNot(contains('rawProviderError')),
        );
        expect(snapshot.categories.last.category, 'weaver');
        expect(
          snapshot.categories.last.readiness,
          ProviderCategoryReadiness.policyBlocked,
        );
        expect(snapshot.providers.first.module, 'office');
        expect(snapshot.providers.first.failClosed, isTrue);
        final meetings = snapshot.providers.singleWhere(
          (provider) => provider.module == 'meetings',
        );
        expect(meetings.providerKey, 'livekit');
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
          baseUrl: Uri.parse('https://api.weave.local/api'),
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
        baseUrl: Uri.parse('https://api.weave.local/api'),
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
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
        workspaceId: 'workspace-1',
        channelId: 'channel-1',
      );
      final office = await client.fetchOfficeCapabilities(
        baseUrl: Uri.parse('https://api.weave.local/api'),
        accessToken: 'token-123',
      );

      expect(devops.supportSafe, isTrue);
      expect(office.launchAvailable, isFalse);
      expect(
        requests,
        contains(
          'https://api.weave.local/api/workspaces/workspace-1/channels/channel-1/devops/summary',
        ),
      );
      expect(
        requests,
        contains('https://api.weave.local/api/office/capabilities'),
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
            'requestId': 'request-123',
          }, statusCode: 503);
        }),
      );

      final launch = await client.launchOfficeSession(
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
          baseUrl: Uri.parse('https://api.weave.local/api'),
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
        baseUrl: Uri.parse('https://api.weave.local/api'),
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
