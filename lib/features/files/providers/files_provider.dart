import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/files/models/file_entry.dart';

part 'files_provider.g.dart';

/// Manages the list of file entries.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud WebDAV calls
@riverpod
class FilesNotifier extends _$FilesNotifier {
  @override
  Future<List<FileEntry>> build() async => [];
}
