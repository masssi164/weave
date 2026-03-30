// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'shared_preferences_server_configuration_repository.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(serverConfigurationRepository)
final serverConfigurationRepositoryProvider =
    ServerConfigurationRepositoryProvider._();

final class ServerConfigurationRepositoryProvider
    extends
        $FunctionalProvider<
          ServerConfigurationRepository,
          ServerConfigurationRepository,
          ServerConfigurationRepository
        >
    with $Provider<ServerConfigurationRepository> {
  ServerConfigurationRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'serverConfigurationRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$serverConfigurationRepositoryHash();

  @$internal
  @override
  $ProviderElement<ServerConfigurationRepository> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  ServerConfigurationRepository create(Ref ref) {
    return serverConfigurationRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ServerConfigurationRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ServerConfigurationRepository>(
        value,
      ),
    );
  }
}

String _$serverConfigurationRepositoryHash() =>
    r'73a2fc5ba3ad5c0b7a66c2d264b399d3c95a74bb';
