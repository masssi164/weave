// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'service_endpoint_deriver.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(serviceEndpointDeriver)
final serviceEndpointDeriverProvider = ServiceEndpointDeriverProvider._();

final class ServiceEndpointDeriverProvider
    extends
        $FunctionalProvider<
          ServiceEndpointDeriver,
          ServiceEndpointDeriver,
          ServiceEndpointDeriver
        >
    with $Provider<ServiceEndpointDeriver> {
  ServiceEndpointDeriverProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'serviceEndpointDeriverProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$serviceEndpointDeriverHash();

  @$internal
  @override
  $ProviderElement<ServiceEndpointDeriver> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  ServiceEndpointDeriver create(Ref ref) {
    return serviceEndpointDeriver(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ServiceEndpointDeriver value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ServiceEndpointDeriver>(value),
    );
  }
}

String _$serviceEndpointDeriverHash() =>
    r'e0e633729e0490aaff3e35262a1c31d25885e54d';
