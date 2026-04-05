// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'server_configuration_form_controller.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(ServerConfigurationFormController)
final serverConfigurationFormControllerProvider =
    ServerConfigurationFormControllerProvider._();

final class ServerConfigurationFormControllerProvider
    extends
        $NotifierProvider<
          ServerConfigurationFormController,
          ServerConfigurationFormState
        > {
  ServerConfigurationFormControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'serverConfigurationFormControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() =>
      _$serverConfigurationFormControllerHash();

  @$internal
  @override
  ServerConfigurationFormController create() =>
      ServerConfigurationFormController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ServerConfigurationFormState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ServerConfigurationFormState>(value),
    );
  }
}

String _$serverConfigurationFormControllerHash() =>
    r'7bb36cfdda04c2b5796732627004e31b1eaa4cca';

abstract class _$ServerConfigurationFormController
    extends $Notifier<ServerConfigurationFormState> {
  ServerConfigurationFormState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<ServerConfigurationFormState, ServerConfigurationFormState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                ServerConfigurationFormState,
                ServerConfigurationFormState
              >,
              ServerConfigurationFormState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

@ProviderFor(savedServerConfiguration)
final savedServerConfigurationProvider = SavedServerConfigurationProvider._();

final class SavedServerConfigurationProvider
    extends
        $FunctionalProvider<
          AsyncValue<ServerConfiguration?>,
          ServerConfiguration?,
          FutureOr<ServerConfiguration?>
        >
    with
        $FutureModifier<ServerConfiguration?>,
        $FutureProvider<ServerConfiguration?> {
  SavedServerConfigurationProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'savedServerConfigurationProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$savedServerConfigurationHash();

  @$internal
  @override
  $FutureProviderElement<ServerConfiguration?> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<ServerConfiguration?> create(Ref ref) {
    return savedServerConfiguration(ref);
  }
}

String _$savedServerConfigurationHash() =>
    r'9309037272af71cc09e30180853f6a6c4a849a6f';
