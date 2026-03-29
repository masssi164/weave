// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'setup_state_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// Tracks whether the user has completed the onboarding setup flow.
///
/// On first read, the value is loaded from [SharedPreferences].
/// Call [completeSetup] to write `true` and mark setup as done.

@ProviderFor(SetupState)
const setupStateProvider = SetupStateProvider._();

/// Tracks whether the user has completed the onboarding setup flow.
///
/// On first read, the value is loaded from [SharedPreferences].
/// Call [completeSetup] to write `true` and mark setup as done.
final class SetupStateProvider extends $NotifierProvider<SetupState, bool> {
  /// Tracks whether the user has completed the onboarding setup flow.
  ///
  /// On first read, the value is loaded from [SharedPreferences].
  /// Call [completeSetup] to write `true` and mark setup as done.
  const SetupStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'setupStateProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$setupStateHash();

  @$internal
  @override
  SetupState create() => SetupState();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(bool value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<bool>(value),
    );
  }
}

String _$setupStateHash() => r'4f88ecbc82d626c322b034dbec3662ac5d4b8831';

/// Tracks whether the user has completed the onboarding setup flow.
///
/// On first read, the value is loaded from [SharedPreferences].
/// Call [completeSetup] to write `true` and mark setup as done.

abstract class _$SetupState extends $Notifier<bool> {
  bool build();
  @$mustCallSuper
  @override
  void runBuild() {
    final created = build();
    final ref = this.ref as $Ref<bool, bool>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<bool, bool>,
              bool,
              Object?,
              Object?
            >;
    element.handleValue(ref, created);
  }
}

/// Read-only provider for the current setup step index.
///
/// The [SetupFlow] widget manages this locally, but the provider allows
/// other widgets (e.g. a step indicator) to react to step changes.

@ProviderFor(SetupStep)
const setupStepProvider = SetupStepProvider._();

/// Read-only provider for the current setup step index.
///
/// The [SetupFlow] widget manages this locally, but the provider allows
/// other widgets (e.g. a step indicator) to react to step changes.
final class SetupStepProvider extends $NotifierProvider<SetupStep, int> {
  /// Read-only provider for the current setup step index.
  ///
  /// The [SetupFlow] widget manages this locally, but the provider allows
  /// other widgets (e.g. a step indicator) to react to step changes.
  const SetupStepProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'setupStepProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$setupStepHash();

  @$internal
  @override
  SetupStep create() => SetupStep();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(int value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<int>(value),
    );
  }
}

String _$setupStepHash() => r'76eede3b3c84936e794490c7e010c3261ee3ea2f';

/// Read-only provider for the current setup step index.
///
/// The [SetupFlow] widget manages this locally, but the provider allows
/// other widgets (e.g. a step indicator) to react to step changes.

abstract class _$SetupStep extends $Notifier<int> {
  int build();
  @$mustCallSuper
  @override
  void runBuild() {
    final created = build();
    final ref = this.ref as $Ref<int, int>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<int, int>,
              int,
              Object?,
              Object?
            >;
    element.handleValue(ref, created);
  }
}
