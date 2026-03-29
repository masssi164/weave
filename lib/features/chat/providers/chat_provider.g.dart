// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'chat_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// Manages the list of chat messages.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Matrix SDK calls

@ProviderFor(ChatNotifier)
final chatProvider = ChatNotifierProvider._();

/// Manages the list of chat messages.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Matrix SDK calls
final class ChatNotifierProvider
    extends $AsyncNotifierProvider<ChatNotifier, List<ChatMessage>> {
  /// Manages the list of chat messages.
  ///
  /// Returns an empty list by default — no network calls.
  /// TODO(integration): replace with Matrix SDK calls
  ChatNotifierProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'chatProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$chatNotifierHash();

  @$internal
  @override
  ChatNotifier create() => ChatNotifier();
}

String _$chatNotifierHash() => r'215b6fdcf3a09399b1aa7f7730f66c17426032dd';

/// Manages the list of chat messages.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Matrix SDK calls

abstract class _$ChatNotifier extends $AsyncNotifier<List<ChatMessage>> {
  FutureOr<List<ChatMessage>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<AsyncValue<List<ChatMessage>>, List<ChatMessage>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<List<ChatMessage>>, List<ChatMessage>>,
              AsyncValue<List<ChatMessage>>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
