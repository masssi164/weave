import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('member transport has one generated User model source', () {
    expect(
      File('lib/generated/user_api/model/protocols.dart').existsSync(),
      isTrue,
    );
    expect(File('lib/generated/openapi_models.dart').existsSync(), isFalse);
  });

  test(
    'member app cannot acquire provider registry diagnostics with its User session',
    () async {
      final transport = await File(
        'lib/integrations/weave_api/data/services/weave_api_client.dart',
      ).readAsString();
      final providers = await File(
        'lib/integrations/weave_api/presentation/providers/weave_api_provider.dart',
      ).readAsString();
      final settings = await File(
        'lib/features/settings/presentation/settings_screen.dart',
      ).readAsString();
      expect(transport, isNot(contains("['providers', 'status']")));
      expect(transport, isNot(contains('fetchProviderStackStatus')));
      expect(
        providers,
        isNot(contains('weaveApiProviderStackSnapshotProvider')),
      );
      expect(
        settings,
        isNot(contains('weaveApiProviderStackSnapshotProvider')),
      );
      expect(settings, contains('workspaceCapabilitySnapshotProvider'));
      for (final source in Directory(
        'lib',
      ).listSync(recursive: true).whereType<File>()) {
        if (source.path.endsWith('.dart') &&
            !source.path.contains('/generated/')) {
          expect(
            source.readAsStringSync(),
            isNot(contains('generated/admin_api/')),
            reason:
                'The Flutter member app must not import an Admin client: ${source.path}',
          );
        }
      }
    },
  );

  test('legacy direct Nextcloud Flutter integration is removed', () {
    expect(
      Directory('lib/integrations/nextcloud').existsSync(),
      isFalse,
      reason:
          'Normal member Files uses the generated Weave User API; direct Flutter Nextcloud auth/session code must not be reintroduced.',
    );
  });

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
    expect(client, contains('generated/user_api/api.dart'));
    expect(client, contains('user_api.IdentityApi(api).me()'));
    expect(client, contains('user_api.ProfileApi('));
    expect(client, contains('.updateProductProfile('));
    expect(client, isNot(contains('jsonDecode')));
    expect(client, isNot(contains('.fromJson(')));
    expect(mapper, isNot(contains('class UserProfileDto')));
    expect(client, isNot(contains('UserProfileDto.fromJson')));
  });

  test(
    'Chat uses native Matrix SDK without Weave token reuse; Files stays in data',
    () async {
      final chatRepository = await File(
        'lib/features/chat/data/repositories/native_matrix_chat_repository.dart',
      ).readAsString();
      final matrixCoordinator = await File(
        'lib/integrations/rust_matrix_core/data/services/matrix_crypto_session_coordinator.dart',
      ).readAsString();
      final matrixBridge = await File(
        'lib/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart',
      ).readAsString();
      final filesRepository = await File(
        'lib/features/files/data/repositories/backend_files_repository.dart',
      ).readAsString();
      expect(chatRepository, contains('RustMatrixCoreBridge'));
      expect(chatRepository, contains('loadEncryptedRooms'));
      expect(chatRepository, contains('loadEncryptedRoomMessages'));
      expect(chatRepository, contains('sendEncryptedText'));
      expect(chatRepository, isNot(contains('http.Client')));
      expect(chatRepository, isNot(contains('/_matrix/client/')));
      expect(chatRepository, isNot(contains('/api/chat/conversations')));
      expect(chatRepository, isNot(contains('BackendChatRepository')));
      expect(matrixCoordinator, contains('startOAuth('));
      expect(matrixCoordinator, contains('restoreOAuth('));
      // The Weave bearer authorizes current member access through the User API
      // only; the Rust boundary has no token-import argument for Matrix OAuth.
      expect(
        matrixCoordinator,
        contains('weaveAccessToken: authSession.accessToken'),
      );
      expect(
        'authSession.accessToken'.allMatches(matrixCoordinator),
        hasLength(1),
      );
      expect(matrixBridge, isNot(contains('accessToken')));
      expect(
        matrixCoordinator,
        isNot(contains('/_matrix/client/v3/account/whoami')),
      );
      expect(matrixBridge, isNot(contains('initializeClient(')));

      expect(filesRepository, contains('generated/user_api/api.dart'));
      expect(filesRepository, contains('user_api.FilesUserApi('));
      expect(filesRepository, contains('api.listFilesItems('));
      expect(filesRepository, contains('api.createFilesFolder('));
      expect(filesRepository, contains('api.uploadFilesItemContent('));
      expect(
        filesRepository,
        contains('api.downloadFilesItemContentWithHttpInfo('),
      );
      expect(filesRepository, isNot(contains('PROPFIND')));
      expect(filesRepository, isNot(contains('/dav/files')));
      expect(filesRepository, isNot(contains('http.StreamedRequest(')));
      expect(filesRepository, isNot(contains('http.Request(')));
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
        expect(
          source,
          isNot(contains('generated/user_api/')),
          reason:
              '$file must consume feature domain models, not generated transport types.',
        );
        expect(
          source,
          isNot(contains('BackendChatRepository')),
          reason: '$file must not reference the obsolete REST chat repository.',
        );
      }
    },
  );

  test('workspace API transports use server-generated response models', () async {
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

    expect(client, contains('.organizationManifest()'));
    expect(client, contains('.capabilities()'));
    expect(client, contains('user_api.WorkspaceApi('));
    expect(client, contains('.home()'));
    expect(client, isNot(contains('Response.fromJson')));
    expect(
      workspaceHome,
      contains("package:weave/generated/user_api/api.dart"),
    );
    for (final source in <String>[
      workspaceCapabilities,
      workspaceHome,
      organizationManifest,
    ]) {
      expect(source, isNot(contains('class ')));
      expect(source, contains('extension '));
      expect(source, contains('package:weave/generated/user_api/api.dart'));
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
    'primary chat provider wires the native Matrix SDK without a REST chat dependency',
    () async {
      // FLUTTER_MATRIX_BOUNDARY_CONTRACT
      final source = await File(
        'lib/features/chat/presentation/providers/chat_repository_provider.dart',
      ).readAsString();

      expect(source, contains('NativeMatrixChatRepository'));
      expect(source, contains('native Rust/Matrix SDK'));
      expect(source, contains('matrixCryptoSessionCoordinatorProvider'));
      expect(source, contains('Weave User API room bindings remain separate'));
      expect(source, isNot(contains('authSessionRepositoryProvider')));
      expect(source, isNot(contains('/api/chat/')));
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
        contains('Matrix E2EE device, verification, and recovery'),
      );
      expect(
        securityProvider,
        contains('matrixCryptoSessionCoordinatorProvider'),
      );

      final rustManifest = await File(
        '../rust/matrix-client/Cargo.toml',
      ).readAsString();
      final flutterManifest = await File('pubspec.yaml').readAsString();
      expect(rustManifest, contains('matrix-sdk'));
      expect(rustManifest, contains('e2e-encryption'));
      expect(flutterManifest, isNot(contains('\n  matrix:')));
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
      l10n['chatLoadingLabel'] as String,
      l10n['chatErrorSessionRequiredGuidance'] as String,
      l10n['chatStaleRoomsGuidance'] as String,
      l10n['helpChatBody'] as String,
    ].join('\n');

    expect(l10n, isNot(contains('chatConnectButton')));
    expect(screen, isNot(contains('onConnect')));

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
