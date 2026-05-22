import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/settings/domain/entities/admin_realm_import_preview.dart';

import '../../../helpers/server_config_test_data.dart';

void main() {
  group('AdminRealmImportPreview', () {
    test('fails closed when no admin setup configuration has been saved', () {
      final preview = AdminRealmImportPreview.fromConfiguration(null);

      expect(preview.readyForSafePreview, isFalse);
      expect(
        preview.items.where((item) => item.ready),
        hasLength(1),
        reason: 'Only the static role baseline is ready without setup values.',
      );
      expect(preview.expectedRoles, ['owner', 'admin', 'member', 'guest']);
    });

    test('is ready for safe preview when all public setup values exist', () {
      final preview = AdminRealmImportPreview.fromConfiguration(
        buildTestConfiguration(),
      );

      expect(preview.readyForSafePreview, isTrue);
      expect(preview.items.every((item) => item.ready), isTrue);
      expect(
        preview.items
            .singleWhere(
              (item) => item.key == AdminRealmImportChecklistKey.oidcClient,
            )
            .value,
        'weave-app',
      );
    });

    test('marks missing OIDC client id as action required', () {
      final configuration = buildTestConfiguration().copyWith(
        oidcClientRegistration: const OidcClientRegistration.manual(
          clientId: ' ',
        ),
      );

      final preview = AdminRealmImportPreview.fromConfiguration(configuration);

      expect(preview.readyForSafePreview, isFalse);
      expect(
        preview.items
            .singleWhere(
              (item) => item.key == AdminRealmImportChecklistKey.oidcClient,
            )
            .ready,
        isFalse,
      );
    });
  });
}
