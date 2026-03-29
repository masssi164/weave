// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'files_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// Manages the list of file entries.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud WebDAV calls

@ProviderFor(FilesNotifier)
const filesProvider = FilesNotifierProvider._();

/// Manages the list of file entries.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud WebDAV calls
final class FilesNotifierProvider
    extends $AsyncNotifierProvider<FilesNotifier, List<FileEntry>> {
  /// Manages the list of file entries.
  ///
  /// Returns an empty list by default — no network calls.
  /// TODO(integration): replace with Nextcloud WebDAV calls
  const FilesNotifierProvider._()
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

String _$filesNotifierHash() => r'b65b0bae4ab1e25af29075219ed7ef635dab7786';

/// Manages the list of file entries.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud WebDAV calls

abstract class _$FilesNotifier extends $AsyncNotifier<List<FileEntry>> {
  FutureOr<List<FileEntry>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final created = build();
    final ref = this.ref as $Ref<AsyncValue<List<FileEntry>>, List<FileEntry>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<List<FileEntry>>, List<FileEntry>>,
              AsyncValue<List<FileEntry>>,
              Object?,
              Object?
            >;
    element.handleValue(ref, created);
  }
}
