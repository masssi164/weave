// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'deck_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(deckClient)
final deckClientProvider = DeckClientProvider._();

final class DeckClientProvider
    extends $FunctionalProvider<DeckClient, DeckClient, DeckClient>
    with $Provider<DeckClient> {
  DeckClientProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'deckClientProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$deckClientHash();

  @$internal
  @override
  $ProviderElement<DeckClient> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  DeckClient create(Ref ref) {
    return deckClient(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(DeckClient value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<DeckClient>(value),
    );
  }
}

String _$deckClientHash() => r'674538eba5104a0e247ea326a3749e68fa97be6d';

@ProviderFor(deckRepository)
final deckRepositoryProvider = DeckRepositoryProvider._();

final class DeckRepositoryProvider
    extends $FunctionalProvider<DeckRepository, DeckRepository, DeckRepository>
    with $Provider<DeckRepository> {
  DeckRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'deckRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$deckRepositoryHash();

  @$internal
  @override
  $ProviderElement<DeckRepository> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  DeckRepository create(Ref ref) {
    return deckRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(DeckRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<DeckRepository>(value),
    );
  }
}

String _$deckRepositoryHash() => r'b0d29974605c11ee6c160f240cfb6764bde16e92';

@ProviderFor(DeckNotifier)
final deckProvider = DeckNotifierProvider._();

final class DeckNotifierProvider
    extends $AsyncNotifierProvider<DeckNotifier, List<DeckBoard>> {
  DeckNotifierProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'deckProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$deckNotifierHash();

  @$internal
  @override
  DeckNotifier create() => DeckNotifier();
}

String _$deckNotifierHash() => r'be57f58592737183eb3b5ff43d61519b8f036609';

abstract class _$DeckNotifier extends $AsyncNotifier<List<DeckBoard>> {
  FutureOr<List<DeckBoard>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<AsyncValue<List<DeckBoard>>, List<DeckBoard>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<List<DeckBoard>>, List<DeckBoard>>,
              AsyncValue<List<DeckBoard>>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
