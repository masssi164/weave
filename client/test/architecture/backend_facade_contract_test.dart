import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'primary files provider is wired through the backend-facade seam',
    () async {
      final source = await File(
        'lib/features/files/presentation/providers/files_repository_provider.dart',
      ).readAsString();

      expect(source, contains('BackendFilesRepository'));
      expect(
        source,
        isNot(contains('legacyDirectNextcloudFilesRepositoryProvider')),
      );
      expect(source, isNot(contains('WEAVE_USE_BACKEND_FILES_FACADE')));
      expect(source, isNot(contains('bool.fromEnvironment')));
      expect(source, isNot(contains('integrations/nextcloud')));
      expect(
        source,
        isNot(contains('data/repositories/nextcloud_files_repository.dart')),
      );
      expect(source, isNot(contains('nextcloudDavClientProvider')));
    },
  );

  test('onboarding status uses generated OpenAPI DTO source of truth', () async {
    final mapper = await File(
      'lib/features/onboarding/data/dtos/first_run_status_dto.dart',
    ).readAsString();
    final client = await File(
      'lib/features/onboarding/data/services/backend_onboarding_status_client.dart',
    ).readAsString();

    expect(mapper, contains('openapi.OnboardingStatusResponse'));
    expect(client, contains('openapi.OnboardingStatusResponse.fromJson'));
    expect(mapper, isNot(contains('class FirstRunStatusDto')));
    expect(mapper, isNot(contains('class FirstRunIdentityDto')));
    expect(mapper, isNot(contains('class FirstRunModuleProvisioningDto')));
  });

  test('profile facade uses generated OpenAPI DTO source of truth', () async {
    final mapper = await File(
      'lib/features/profile/data/dtos/user_profile_dto.dart',
    ).readAsString();
    final client = await File(
      'lib/features/profile/data/services/backend_profile_client.dart',
    ).readAsString();

    expect(mapper, contains('openapi.AuthenticatedUserResponse'));
    expect(mapper, contains('openapi.ProductProfileResponse'));
    expect(mapper, contains('openapi.UpdateProductProfileRequest'));
    expect(client, contains('openapi.AuthenticatedUserResponse.fromJson'));
    expect(client, contains('openapi.ProductProfileResponse.fromJson'));
    expect(mapper, isNot(contains('class UserProfileDto')));
    expect(client, isNot(contains('UserProfileDto.fromJson')));
  });

  test('Chat maps OpenAPI DTOs and Files uses WebDAV in data only', () async {
    final chatRepository = await File(
      'lib/features/chat/data/repositories/backend_chat_repository.dart',
    ).readAsString();
    final chatMapper = await File(
      'lib/features/chat/data/dtos/chat_openapi_mappers.dart',
    ).readAsString();
    final filesRepository = await File(
      'lib/features/files/data/repositories/backend_files_repository.dart',
    ).readAsString();
    expect(chatRepository, contains('openapi.ChatConversationsResponse'));
    expect(chatRepository, contains('openapi.ChatMessagesResponse'));
    expect(chatRepository, contains('openapi.ChatSendMessageRequest'));
    expect(chatMapper, contains('openapi.ChatConversationResponse'));
    expect(chatMapper, contains('OpenApiResourcePage<ChatConversation>'));
    expect(chatMapper, isNot(contains('Matrix')));

    expect(filesRepository, contains('PROPFIND'));
    expect(filesRepository, contains('/dav/files'));
    expect(filesRepository, contains("http.StreamedRequest('PUT'"));
    expect(filesRepository, contains("'PUT'"));
    expect(filesRepository, contains("'MKCOL'"));
    expect(filesRepository, contains("'DELETE'"));
    expect(filesRepository, contains("'If-None-Match': '*'"));
    expect(filesRepository, contains("'If-Match': '*'"));
    expect(filesRepository, isNot(contains('generated/openapi_models.dart')));
    expect(filesRepository, isNot(contains('/api/files/upload')));
    expect(filesRepository, isNot(contains('/api/files/folders')));
    expect(filesRepository, isNot(contains('Nextcloud')));

    final featureBoundaryFiles = <String>[
      'lib/features/chat/domain',
      'lib/features/chat/presentation',
      'lib/features/files/domain',
      'lib/features/files/presentation',
    ].expand(_dartFilesUnder);

    for (final file in featureBoundaryFiles) {
      final source = await File(file).readAsString();
      expect(
        source,
        isNot(contains('generated/openapi_models.dart')),
        reason:
            '$file must consume feature domain models, not raw OpenAPI DTOs.',
      );
    }
  });

  test('workspace API DTOs use generated OpenAPI response models', () async {
    final client = await File(
      'lib/integrations/weave_api/data/services/weave_api_client.dart',
    ).readAsString();
    final workspaceCapabilities = await File(
      'lib/integrations/weave_api/data/dtos/workspace_capabilities_response_dto.dart',
    ).readAsString();
    final workspaceHome = await File(
      'lib/integrations/weave_api/data/dtos/workspace_home_response_dto.dart',
    ).readAsString();
    final organizationManifest = await File(
      'lib/integrations/weave_api/data/dtos/organization_manifest_response_dto.dart',
    ).readAsString();

    expect(client, contains('openapi.OrganizationManifestResponse.fromJson'));
    expect(client, contains('openapi.WorkspaceCapabilitiesResponse.fromJson'));
    expect(client, contains('openapi.WorkspaceHomeResponse.fromJson'));
    for (final source in <String>[
      workspaceCapabilities,
      workspaceHome,
      organizationManifest,
    ]) {
      expect(source, isNot(contains('class ')));
      expect(source, contains('extension '));
    }
  });

  test('calendar member route stays behind the backend facade seam', () async {
    final provider = await File(
      'lib/features/calendar/presentation/providers/calendar_provider.dart',
    ).readAsString();
    final router = await File('lib/core/router/app_router.dart').readAsString();
    final screen = await File(
      'lib/features/calendar/presentation/calendar_screen.dart',
    ).readAsString();

    expect(provider, contains('CalendarFacadeClient'));
    expect(provider, isNot(contains('CalDavClient')));
    expect(provider, isNot(contains('caldav_client.dart')));
    expect(screen, contains('workspaceCapabilitySnapshotProvider'));
    expect(screen, isNot(contains('CalDavClient')));
    expect(screen, isNot(contains('caldav_client.dart')));
    expect(router, contains('CalendarScreen'));
    expect(router, contains('AppRoutes.calendar'));
  });

  test(
    'primary chat provider is wired through the Matrix Client-Server projection',
    () async {
      // FLUTTER_MATRIX_BOUNDARY_CONTRACT
      final source = await File(
        'lib/features/chat/presentation/providers/chat_repository_provider.dart',
      ).readAsString();

      expect(source, contains('WeaveMatrixFacadeChatRepository'));
      expect(source, contains('Matrix Client-Server projection'));
      expect(source, contains('/api/chat/**'));
      expect(source, contains('OpenAPI/REST'));
      expect(source, contains('direct Matrix SDK'));
      expect(source, isNot(contains('FeatureFlags.legacyDirectMatrixChat')));
      expect(source, isNot(contains('BackendChatRepository(')));
      expect(source, isNot(contains('matrixSessionServiceProvider')));
      expect(source, isNot(contains('package:matrix')));

      final workspaceReadiness = await File(
        'lib/features/app/presentation/providers/workspace_connection_provider.dart',
      ).readAsString();
      expect(
        workspaceReadiness,
        isNot(contains('chatSecurityRepositoryProvider')),
      );
      expect(
        workspaceReadiness,
        isNot(contains('MatrixChatSecurityRepository')),
      );
      expect(
        workspaceReadiness,
        isNot(contains('RustMatrixCoreChatSecurityRepository')),
      );

      final securityProvider = await File(
        'lib/features/chat/presentation/providers/chat_security_repository_provider.dart',
      ).readAsString();
      expect(
        securityProvider,
        contains(
          'Diagnostic-only Matrix E2EE/security seam through the Rust core boundary',
        ),
      );
      expect(
        securityProvider,
        contains('Direct Matrix SDK crypto is intentionally absent'),
      );
    },
  );

  test('member Chat screen stays on Weave-domain readiness language', () async {
    final screen = await File(
      'lib/features/chat/presentation/chat_screen.dart',
    ).readAsString();
    final l10n =
        jsonDecode(await File('lib/l10n/app_en.arb').readAsString())
            as Map<String, dynamic>;

    expect(screen, isNot(contains('firstRunStatusProvider')));
    expect(screen, isNot(contains('moduleProvisioning.matrix')));
    expect(screen, isNot(contains('matrixProvisioning')));
    expect(screen, isNot(contains('chatSecurityProvider')));
    expect(screen, isNot(contains('ChatSecurityBanner')));
    expect(screen, isNot(contains('chat_security_provider.dart')));
    expect(screen, isNot(contains('chat_security_banner.dart')));

    final memberChatCopy = <String>[
      l10n['chatConnectingLabel'] as String,
      l10n['chatConnectingHint'] as String,
      l10n['chatConnectButton'] as String,
      l10n['chatStaleRoomsGuidance'] as String,
      l10n['helpChatBody'] as String,
    ].join('\n');

    for (final forbidden in <String>[
      'Connect'
          ' Matrix',
      'Connecting'
          ' to Matrix',
      'refresh'
          ' Matrix',
      'connect Mat'
          'rix if asked',
      'homes'
          'erver',
      'raw pr'
          'ovider',
      'provider d'
          'iagnostics',
      'credentia'
          'l-bearing',
      'Bea'
          'rer ',
      'access'
          '_token',
    ]) {
      expect(memberChatCopy, isNot(contains(forbidden)), reason: forbidden);
    }
  });
}

Iterable<String> _dartFilesUnder(String directoryPath) {
  return Directory(directoryPath)
      .listSync(recursive: true)
      .whereType<File>()
      .where((file) => file.path.endsWith('.dart'))
      .map((file) => file.path.replaceAll(r'\', '/'));
}
