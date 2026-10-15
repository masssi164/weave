import 'package:weave/core/failures/app_failure.dart';
import 'package:weave/features/onboarding/domain/entities/member_handoff.dart';
import 'package:weave/features/onboarding/domain/use_cases/discover_organization_access.dart';
import 'package:weave/features/server_config/domain/entities/server_configuration.dart';
import 'package:weave/features/server_config/domain/repositories/server_configuration_repository.dart';

/// Refreshes the same public generated PlatformApi contract after authentication.
/// Saved endpoints are derived transport values, never bootstrap authority.
class RefreshOrganizationManifest {
  const RefreshOrganizationManifest({
    required AppStartDiscoveryClient discoveryClient,
    required ServerConfigurationRepository repository,
    DateTime Function()? now,
  }) : _discoveryClient = discoveryClient,
       _repository = repository,
       _now = now;

  final AppStartDiscoveryClient _discoveryClient;
  final ServerConfigurationRepository _repository;
  final DateTime Function()? _now;

  // An outage may preserve a previously validated profile for at most one day.
  static const maximumCacheAge = Duration(hours: 24);

  Future<ServerConfiguration> call(ServerConfiguration saved) async {
    final origin = saved.organizationOrigin;
    if (origin == null) {
      throw const AppFailure.validation(
        'The saved profile needs a new organization address.',
      );
    }
    final access = const OrganizationAccessParser().parse(origin);
    final now = (_now?.call() ?? DateTime.now()).toUtc();
    late AppStartConfiguration current;
    try {
      current = await _discoveryClient.fetch(access);
    } on AppFailure catch (failure) {
      // TLS, schema, identity and HTTP failures must not bless a cached profile.
      // Only transport unavailability permits bounded continuity.
      final transient =
          failure.type == AppFailureType.bootstrap &&
          ['TIMEOUT', 'DNS', 'NETWORK'].any(failure.message.contains);
      final validatedAt = saved.manifestValidatedAt;
      final manifest = saved.validatedManifest;
      if (!transient ||
          manifest == null ||
          validatedAt == null ||
          validatedAt.isAfter(now) ||
          now.difference(validatedAt) > maximumCacheAge) {
        rethrow;
      }
      current = _discoveryClient.validateManifest(manifest, access);
      _requireSameIdentity(saved, current);
      final continued = _derivedConfiguration(saved, current);
      await _repository.saveConfiguration(continued);
      return continued;
    }
    _requireSameIdentity(saved, current);
    final refreshed = _derivedConfiguration(
      saved,
      current,
    ).copyWith(validatedManifest: current.manifest, manifestValidatedAt: now);
    await _repository.saveConfiguration(refreshed);
    return refreshed;
  }

  void _requireSameIdentity(
    ServerConfiguration saved,
    AppStartConfiguration current,
  ) {
    if (saved.oidcIssuerUrl != current.oidcIssuerUrl ||
        saved.oidcClientRegistration.clientId.trim() != current.oidcClientId) {
      throw const AppFailure.validation(
        'The organization authentication profile changed. Reconnect to your organization.',
      );
    }
  }

  ServerConfiguration _derivedConfiguration(
    ServerConfiguration saved,
    AppStartConfiguration current,
  ) => current.toServerConfiguration(
    saved.organizationOrigin!,
    validatedAt: saved.manifestValidatedAt,
  );
}
