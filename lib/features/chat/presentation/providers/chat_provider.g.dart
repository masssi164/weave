// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'chat_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(matrixClient)
final matrixClientProvider = MatrixClientProvider._();

final class MatrixClientProvider
    extends $FunctionalProvider<MatrixClient, MatrixClient, MatrixClient>
    with $Provider<MatrixClient> {
  MatrixClientProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'matrixClientProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$matrixClientHash();

  @$internal
  @override
  $ProviderElement<MatrixClient> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  MatrixClient create(Ref ref) {
    return matrixClient(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(MatrixClient value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<MatrixClient>(value),
    );
  }
}

String _$matrixClientHash() => r'fb3b4fd33cfff6a356499618cff902419cf6c7cd';

@ProviderFor(chatRepository)
final chatRepositoryProvider = ChatRepositoryProvider._();

final class ChatRepositoryProvider
    extends $FunctionalProvider<ChatRepository, ChatRepository, ChatRepository>
    with $Provider<ChatRepository> {
  ChatRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'chatRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$chatRepositoryHash();

  @$internal
  @override
  $ProviderElement<ChatRepository> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  ChatRepository create(Ref ref) {
    return chatRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ChatRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ChatRepository>(value),
    );
  }
}

String _$chatRepositoryHash() => r'9c5bb99564b7df12f980baa7e1855c3778bc1bfb';

@ProviderFor(ChatNotifier)
final chatProvider = ChatNotifierProvider._();

final class ChatNotifierProvider
    extends $AsyncNotifierProvider<ChatNotifier, List<ChatMessage>> {
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

String _$chatNotifierHash() => r'ae49397c1e5296b206cca287303e26e0e90bec3e';

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
