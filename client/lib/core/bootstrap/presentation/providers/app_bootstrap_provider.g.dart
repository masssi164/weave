// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_bootstrap_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(AppBootstrap)
final appBootstrapProvider = AppBootstrapProvider._();

final class AppBootstrapProvider
    extends $AsyncNotifierProvider<AppBootstrap, BootstrapState> {
  AppBootstrapProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'appBootstrapProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$appBootstrapHash();

  @$internal
  @override
  AppBootstrap create() => AppBootstrap();
}

String _$appBootstrapHash() => r'c756abb0c42dd67f187b92da294bb0500011bdb7';

abstract class _$AppBootstrap extends $AsyncNotifier<BootstrapState> {
  FutureOr<BootstrapState> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<AsyncValue<BootstrapState>, BootstrapState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<BootstrapState>, BootstrapState>,
              AsyncValue<BootstrapState>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

@ProviderFor(resolvedBootstrapState)
final resolvedBootstrapStateProvider = ResolvedBootstrapStateProvider._();

final class ResolvedBootstrapStateProvider
    extends $FunctionalProvider<BootstrapState, BootstrapState, BootstrapState>
    with $Provider<BootstrapState> {
  ResolvedBootstrapStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'resolvedBootstrapStateProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$resolvedBootstrapStateHash();

  @$internal
  @override
  $ProviderElement<BootstrapState> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  BootstrapState create(Ref ref) {
    return resolvedBootstrapState(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(BootstrapState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<BootstrapState>(value),
    );
  }
}

String _$resolvedBootstrapStateHash() =>
    r'73d4d7b0ee61071cc643e970ed6bb818788b1708';
