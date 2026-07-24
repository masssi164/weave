import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('organization access architecture', () {
    test('fresh launch has one Organization Access route', () {
      final routes = File('lib/core/router/app_routes.dart').readAsStringSync();
      final router = File('lib/core/router/app_router.dart').readAsStringSync();

      expect(routes, contains("organizationAccess = '/organization-access'"));
      expect(routes, isNot(contains("welcome = '/welcome'")));
      expect(router, contains('const OrganizationAccessScreen()'));
      expect(router, isNot(contains('SetupFlow')));
      expect(
        File(
          'lib/features/onboarding/presentation/setup_flow.dart',
        ).existsSync(),
        isFalse,
      );
    });

    test('all access transports share discovery and central sign-in', () {
      final accessScreen = File(
        'lib/features/onboarding/presentation/organization_access_screen.dart',
      ).readAsStringSync();
      final handoffScreen = File(
        'lib/features/onboarding/presentation/member_handoff_screen.dart',
      ).readAsStringSync();

      expect(accessScreen, contains('AppRoutes.join'));
      expect(accessScreen, isNot(contains('OidcClient')));
      expect(accessScreen, isNot(contains('ServerConfigurationForm')));

      expect(handoffScreen, contains('discoverOrganizationAccessProvider'));
      expect(handoffScreen, contains('authFlowControllerProvider.notifier'));
      expect(handoffScreen, isNot(contains('signInWithOidcProvider')));
      expect(handoffScreen, isNot(contains('oidcClientProvider')));
    });

    test(
      'discovery stores protocol configuration without provider posture',
      () {
        final accessEntity = File(
          'lib/features/onboarding/domain/entities/member_handoff.dart',
        ).readAsStringSync();
        final discovery = File(
          'lib/features/onboarding/domain/use_cases/'
          'discover_organization_access.dart',
        ).readAsStringSync();
        final providerType = File(
          'lib/features/server_config/domain/entities/oidc_provider_type.dart',
        ).readAsStringSync();

        expect(providerType, contains('OidcProviderType { oidc }'));
        expect(providerType, isNot(contains('keycloak')));
        expect(discovery, contains('OidcProviderType.oidc'));
        expect(discovery, isNot(contains('OidcProviderType.keycloak')));
        expect(accessEntity, isNot(contains('final String profile')));
        expect(accessEntity, isNot(contains('local-lan-dogfood')));
        expect(accessEntity, isNot(contains('local-dogfood')));
      },
    );

    test('member Organization Access copy stays provider-neutral', () {
      final forbidden = RegExp(
        r'Keycloak|realm|OIDC|client ID|provider endpoint|Nextcloud|Matrix',
        caseSensitive: false,
      );
      const memberKeys = <String>[
        'setupTitle',
        'setupMemberHandoffDescription',
        'setupOrganizationUriLabel',
        'setupOrganizationUriHelper',
        'setupOrganizationUriError',
        'setupOrganizationContinueButton',
        'setupOrganizationAccessHelp',
        'memberHandoffLoadingTitle',
        'memberHandoffLoadingHint',
        'memberHandoffReadyTitle',
        'memberHandoffReadyGuidance',
        'memberHandoffErrorTitle',
        'memberHandoffErrorGuidance',
        'memberHandoffSignInRetryGuidance',
      ];

      for (final path in const ['lib/l10n/app_en.arb', 'lib/l10n/app_de.arb']) {
        final messages =
            (jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>)
                .cast<String, Object?>();
        for (final key in memberKeys) {
          expect(messages[key], isA<String>(), reason: '$path misses $key');
          expect(
            messages[key],
            isNot(contains(forbidden)),
            reason: '$path exposes provider configuration through $key',
          );
        }
      }
    });
  });
}
