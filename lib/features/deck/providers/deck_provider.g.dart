// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'deck_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// Manages the list of deck boards.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud Deck API calls

@ProviderFor(DeckNotifier)
const deckProvider = DeckNotifierProvider._();

/// Manages the list of deck boards.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud Deck API calls
final class DeckNotifierProvider
    extends $AsyncNotifierProvider<DeckNotifier, List<DeckBoard>> {
  /// Manages the list of deck boards.
  ///
  /// Returns an empty list by default — no network calls.
  /// TODO(integration): replace with Nextcloud Deck API calls
  const DeckNotifierProvider._()
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

String _$deckNotifierHash() => r'12add2cbb16503d0f0dbcac946d5b2a9bf1faef8';

/// Manages the list of deck boards.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud Deck API calls

abstract class _$DeckNotifier extends $AsyncNotifier<List<DeckBoard>> {
  FutureOr<List<DeckBoard>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final created = build();
    final ref = this.ref as $Ref<AsyncValue<List<DeckBoard>>, List<DeckBoard>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<List<DeckBoard>>, List<DeckBoard>>,
              AsyncValue<List<DeckBoard>>,
              Object?,
              Object?
            >;
    element.handleValue(ref, created);
  }
}
