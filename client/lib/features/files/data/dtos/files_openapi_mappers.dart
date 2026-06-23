import 'package:weave/features/files/domain/entities/directory_listing.dart';
import 'package:weave/features/files/domain/entities/file_entry.dart';
import 'package:weave/features/files/domain/entities/files_failure.dart';
import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/domain/entities/openapi_feature_adapter.dart';

const _filesFeatureKey = 'files';

extension FileListResponseOpenApiMapper on openapi.FileListResponse {
  OpenApiResourcePage<FileEntry> toFileEntryPage() {
    return OpenApiResourcePage<FileEntry>(
      featureKey: _filesFeatureKey,
      readiness: const OpenApiFeatureReadiness(
        featureKey: _filesFeatureKey,
        state: OpenApiFeatureCapabilityState.available,
        memberImpact: 'Weave Files are available.',
      ),
      resources: (items ?? const <openapi.FileItemResponse>[])
          .map((item) => item.toDomainEntry())
          .toList(growable: false),
    );
  }

  DirectoryListing toDomainListing() {
    return DirectoryListing(
      path: _fallbackText(path, '/'),
      entries: toFileEntryPage().resources,
    );
  }
}

extension FileItemResponseOpenApiMapper on openapi.FileItemResponse {
  FileEntry toDomainEntry() {
    final entryType = _requiredText(type, 'type');
    return FileEntry(
      id: _requiredText(id, 'id'),
      name: _requiredText(name, 'name'),
      path: _requiredText(path, 'path'),
      isDirectory: entryType == 'folder' || entryType == 'directory',
      modifiedAt: _readDateTime(modifiedAt),
      sizeInBytes: size,
    );
  }
}

String _requiredText(String? value, String key) {
  final trimmed = value?.trim();
  if (trimmed != null && trimmed.isNotEmpty) {
    return trimmed;
  }
  throw FilesFailure.protocol(
    'The Weave backend returned a file item without $key.',
  );
}

String _fallbackText(String? value, String fallback) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? fallback : trimmed;
}

DateTime? _readDateTime(String? value) {
  if (value == null || value.isEmpty) {
    return null;
  }
  return DateTime.tryParse(value);
}
