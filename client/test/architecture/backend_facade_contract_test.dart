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

  test(
    'calendar member route is removed while backend facade stays fenced',
    () async {
      final provider = await File(
        'lib/features/calendar/presentation/providers/calendar_provider.dart',
      ).readAsString();
      final router = await File(
        'lib/core/router/app_router.dart',
      ).readAsString();

      expect(provider, contains('CalendarFacadeClient'));
      expect(provider, isNot(contains('CalDavClient')));
      expect(provider, isNot(contains('caldav_client.dart')));
      expect(
        File(
          'lib/features/calendar/presentation/calendar_screen.dart',
        ).existsSync(),
        isFalse,
      );
      expect(router, isNot(contains('CalendarScreen')));
      expect(router, isNot(contains('AppRoutes.calendar')));
    },
  );

  test('primary chat provider is wired through the backend Chat facade', () async {
    final source = await File(
      'lib/features/chat/presentation/providers/chat_repository_provider.dart',
    ).readAsString();

    expect(source, contains('BackendChatRepository'));
    expect(source, isNot(contains('FeatureFlags.legacyDirectMatrixChat')));
    expect(source, isNot(contains('MatrixChatRepository(')));
    expect(source, isNot(contains('matrixSessionServiceProvider')));

    final workspaceReadiness = await File(
      'lib/features/app/presentation/providers/workspace_connection_provider.dart',
    ).readAsString();
    expect(
      workspaceReadiness,
      isNot(contains('chatSecurityRepositoryProvider')),
    );
    expect(workspaceReadiness, isNot(contains('MatrixChatSecurityRepository')));

    final securityProvider = await File(
      'lib/features/chat/presentation/providers/chat_security_repository_provider.dart',
    ).readAsString();
    expect(
      securityProvider,
      contains('Diagnostic-only Matrix E2EE/security seam'),
    );
    expect(securityProvider, contains('until #895'));
  });

  test(
    'normal member surfaces do not mount Matrix security diagnostics',
    () async {
      final chatScreen = await File(
        'lib/features/chat/presentation/chat_screen.dart',
      ).readAsString();
      final settingsScreen = await File(
        'lib/features/settings/presentation/settings_screen.dart',
      ).readAsString();

      for (final source in <String>[chatScreen, settingsScreen]) {
        expect(source, isNot(contains('chatSecurityProvider')));
        expect(source, isNot(contains('ChatSecurityBanner')));
        expect(source, isNot(contains('ChatSecuritySettingsSection')));
        expect(source, isNot(contains('chat_security_provider.dart')));
        expect(source, isNot(contains('chat_security_banner.dart')));
        expect(source, isNot(contains('chat_security_settings_section.dart')));
        expect(
          source,
          isNot(contains('chat_security_repository_provider.dart')),
        );
      }
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
