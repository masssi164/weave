// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'files_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(webDavClient)
final webDavClientProvider = WebDavClientProvider._();

final class WebDavClientProvider
    extends $FunctionalProvider<WebDavClient, WebDavClient, WebDavClient>
    with $Provider<WebDavClient> {
  WebDavClientProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'webDavClientProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$webDavClientHash();

  @$internal
  @override
  $ProviderElement<WebDavClient> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  WebDavClient create(Ref ref) {
    return webDavClient(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(WebDavClient value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<WebDavClient>(value),
    );
  }
}

String _$webDavClientHash() => r'7e6f4c4fd3011802b25b705e69a05bdfb00d2768';

@ProviderFor(filesRepository)
final filesRepositoryProvider = FilesRepositoryProvider._();

final class FilesRepositoryProvider
    extends
        $FunctionalProvider<FilesRepository, FilesRepository, FilesRepository>
    with $Provider<FilesRepository> {
  FilesRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'filesRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$filesRepositoryHash();

  @$internal
  @override
  $ProviderElement<FilesRepository> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  FilesRepository create(Ref ref) {
    return filesRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(FilesRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<FilesRepository>(value),
    );
  }
}

String _$filesRepositoryHash() => r'022ae5116b0040abcef45b3fd029f48cc68a7016';

@ProviderFor(FilesNotifier)
final filesProvider = FilesNotifierProvider._();

final class FilesNotifierProvider
    extends $AsyncNotifierProvider<FilesNotifier, List<FileEntry>> {
  FilesNotifierProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'filesProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$filesNotifierHash();

  @$internal
  @override
  FilesNotifier create() => FilesNotifier();
}

String _$filesNotifierHash() => r'5ba86d7f9dcdeea71780607a7ef23775276856a4';

abstract class _$FilesNotifier extends $AsyncNotifier<List<FileEntry>> {
  FutureOr<List<FileEntry>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<AsyncValue<List<FileEntry>>, List<FileEntry>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<List<FileEntry>>, List<FileEntry>>,
              AsyncValue<List<FileEntry>>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
