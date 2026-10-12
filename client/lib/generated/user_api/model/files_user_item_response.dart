//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FilesUserItemResponse {
  /// Returns a new [FilesUserItemResponse] instance.
  FilesUserItemResponse({
    this.allowedActions = const [],
    required this.displayPath,
    required this.fileId,
    required this.kind,
    this.mediaType,
    this.modifiedAt,
    required this.name,
    required this.parentFileId,
    required this.revision,
    required this.size,
  });

  /// Actions currently authorized at the Weave member/resource boundary and supported by the active provider. A later provider state change may still reject an operation.
  List<String> allowedActions;

  /// Display-only product path; never use as an identity.
  String displayPath;

  String fileId;

  FilesUserItemResponseKindEnum kind;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? mediaType;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? modifiedAt;

  String name;

  String parentFileId;

  /// Opaque resource revision; not a provider version.
  String revision;

  int size;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FilesUserItemResponse &&
          _deepEquality.equals(other.allowedActions, allowedActions) &&
          other.displayPath == displayPath &&
          other.fileId == fileId &&
          other.kind == kind &&
          other.mediaType == mediaType &&
          other.modifiedAt == modifiedAt &&
          other.name == name &&
          other.parentFileId == parentFileId &&
          other.revision == revision &&
          other.size == size;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (allowedActions.hashCode) +
      (displayPath.hashCode) +
      (fileId.hashCode) +
      (kind.hashCode) +
      (mediaType == null ? 0 : mediaType!.hashCode) +
      (modifiedAt == null ? 0 : modifiedAt!.hashCode) +
      (name.hashCode) +
      (parentFileId.hashCode) +
      (revision.hashCode) +
      (size.hashCode);

  @override
  String toString() =>
      'FilesUserItemResponse[allowedActions=$allowedActions, displayPath=$displayPath, fileId=$fileId, kind=$kind, mediaType=$mediaType, modifiedAt=$modifiedAt, name=$name, parentFileId=$parentFileId, revision=$revision, size=$size]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'allowedActions'] = this.allowedActions;
    json[r'displayPath'] = this.displayPath;
    json[r'fileId'] = this.fileId;
    json[r'kind'] = this.kind;
    if (this.mediaType != null) {
      json[r'mediaType'] = this.mediaType;
    } else {
      json[r'mediaType'] = null;
    }
    if (this.modifiedAt != null) {
      json[r'modifiedAt'] = this.modifiedAt!.toUtc().toIso8601String();
    } else {
      json[r'modifiedAt'] = null;
    }
    json[r'name'] = this.name;
    json[r'parentFileId'] = this.parentFileId;
    json[r'revision'] = this.revision;
    json[r'size'] = this.size;
    return json;
  }

  /// Returns a new [FilesUserItemResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FilesUserItemResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FilesUserItemResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FilesUserItemResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FilesUserItemResponse(
        allowedActions: json[r'allowedActions'] is Iterable
            ? (json[r'allowedActions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        displayPath: mapValueOfType<String>(json, r'displayPath')!,
        fileId: mapValueOfType<String>(json, r'fileId')!,
        kind: FilesUserItemResponseKindEnum.fromJson(json[r'kind'])!,
        mediaType: mapValueOfType<String>(json, r'mediaType'),
        modifiedAt: mapDateTime(json, r'modifiedAt', r''),
        name: mapValueOfType<String>(json, r'name')!,
        parentFileId: mapValueOfType<String>(json, r'parentFileId')!,
        revision: mapValueOfType<String>(json, r'revision')!,
        size: mapValueOfType<int>(json, r'size')!,
      );
    }
    return null;
  }

  static List<FilesUserItemResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FilesUserItemResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FilesUserItemResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FilesUserItemResponse> mapFromJson(dynamic json) {
    final map = <String, FilesUserItemResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FilesUserItemResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FilesUserItemResponse-objects as value to a dart map
  static Map<String, List<FilesUserItemResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FilesUserItemResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FilesUserItemResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'allowedActions',
    'displayPath',
    'fileId',
    'kind',
    'name',
    'parentFileId',
    'revision',
    'size',
  };
}

class FilesUserItemResponseKindEnum {
  /// Instantiate a new enum with the provided [value].
  const FilesUserItemResponseKindEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const file = FilesUserItemResponseKindEnum._(r'file');
  static const folder = FilesUserItemResponseKindEnum._(r'folder');

  /// List of all possible values in this [enum][FilesUserItemResponseKindEnum].
  static const values = <FilesUserItemResponseKindEnum>[
    file,
    folder,
  ];

  static FilesUserItemResponseKindEnum? fromJson(dynamic value) =>
      FilesUserItemResponseKindEnumTypeTransformer().decode(value);

  static List<FilesUserItemResponseKindEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FilesUserItemResponseKindEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FilesUserItemResponseKindEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [FilesUserItemResponseKindEnum] to String,
/// and [decode] dynamic data back to [FilesUserItemResponseKindEnum].
class FilesUserItemResponseKindEnumTypeTransformer {
  factory FilesUserItemResponseKindEnumTypeTransformer() =>
      _instance ??= const FilesUserItemResponseKindEnumTypeTransformer._();

  const FilesUserItemResponseKindEnumTypeTransformer._();

  String encode(FilesUserItemResponseKindEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a FilesUserItemResponseKindEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  FilesUserItemResponseKindEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'file':
          return FilesUserItemResponseKindEnum.file;
        case r'folder':
          return FilesUserItemResponseKindEnum.folder;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [FilesUserItemResponseKindEnumTypeTransformer] instance.
  static FilesUserItemResponseKindEnumTypeTransformer? _instance;
}
