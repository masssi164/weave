import 'package:weave/features/server_config/domain/entities/server_configuration.dart';

/// Safe, UI-facing preview of the admin-owned Keycloak realm import baseline.
///
/// This model deliberately describes readiness only. It does not carry secrets,
/// credentials, generated JSON, or any action that would mutate a realm.
class AdminRealmImportPreview {
  const AdminRealmImportPreview({
    required this.items,
    required this.expectedRoles,
  });

  factory AdminRealmImportPreview.fromConfiguration(
    ServerConfiguration? configuration,
  ) {
    final issuer = configuration?.oidcIssuerUrl.toString().trim();
    final clientId = configuration?.oidcClientRegistration.clientId.trim();
    final backendApi = configuration?.serviceEndpoints.backendApiBaseUrl
        .toString()
        .trim();
    final matrix = configuration?.serviceEndpoints.matrixHomeserverUrl
        .toString()
        .trim();
    final nextcloud = configuration?.serviceEndpoints.nextcloudBaseUrl
        .toString()
        .trim();

    return AdminRealmImportPreview(
      expectedRoles: const <String>['owner', 'admin', 'member', 'guest'],
      items: <AdminRealmImportChecklistItem>[
        AdminRealmImportChecklistItem(
          key: AdminRealmImportChecklistKey.realmAuthority,
          ready: issuer != null && issuer.isNotEmpty,
          value: issuer,
        ),
        AdminRealmImportChecklistItem(
          key: AdminRealmImportChecklistKey.oidcClient,
          ready: clientId != null && clientId.isNotEmpty,
          value: clientId,
        ),
        AdminRealmImportChecklistItem(
          key: AdminRealmImportChecklistKey.productApi,
          ready: backendApi != null && backendApi.isNotEmpty,
          value: backendApi,
        ),
        AdminRealmImportChecklistItem(
          key: AdminRealmImportChecklistKey.moduleEndpoints,
          ready:
              matrix != null &&
              matrix.isNotEmpty &&
              nextcloud != null &&
              nextcloud.isNotEmpty,
          value: matrix != null && nextcloud != null
              ? '$matrix • $nextcloud'
              : null,
        ),
        const AdminRealmImportChecklistItem(
          key: AdminRealmImportChecklistKey.roleBaseline,
          ready: true,
          value: 'owner, admin, member, guest',
        ),
      ],
    );
  }

  final List<AdminRealmImportChecklistItem> items;
  final List<String> expectedRoles;

  bool get readyForSafePreview => items.every((item) => item.ready);
}

enum AdminRealmImportChecklistKey {
  realmAuthority,
  oidcClient,
  productApi,
  moduleEndpoints,
  roleBaseline,
}

class AdminRealmImportChecklistItem {
  const AdminRealmImportChecklistItem({
    required this.key,
    required this.ready,
    this.value,
  });

  final AdminRealmImportChecklistKey key;
  final bool ready;
  final String? value;
}
