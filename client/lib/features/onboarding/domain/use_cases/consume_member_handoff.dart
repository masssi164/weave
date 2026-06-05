import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/server_config/domain/entities/oidc_client_registration.dart';
import 'package:weave/features/server_config/domain/entities/oidc_provider_type.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/entities/service_endpoints.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

class ConsumeMemberHandoff {
  const ConsumeMemberHandoff({
    required ServerConfigurationRepository repository,
  }) : _repository = repository;

  final ServerConfigurationRepository _repository;

  Future<MemberHandoff> call(Uri uri) async {
    final handoff = const MemberHandoffParser().parse(uri);
    await _repository.saveConfiguration(
      ServerConfiguration(
        providerType: OidcProviderType.keycloak,
        oidcIssuerUrl: handoff.issuerUrl,
        oidcClientRegistration: const OidcClientRegistration.manual(
          clientId: 'weave-mobile',
        ),
        serviceEndpoints: ServiceEndpoints(
          matrixHomeserverUrl: handoff.providerNeutralServiceUrl,
          nextcloudBaseUrl: handoff.providerNeutralServiceUrl,
          backendApiBaseUrl: handoff.backendApiBaseUrl,
        ),
      ),
    );
    return handoff;
  }
}
