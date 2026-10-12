//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class FilesUserListResponse {
  /// Returns a new [FilesUserListResponse] instance.
  FilesUserListResponse({
    this.allowedActions = const [],
    this.items = const [],
    required this.parentFileId,
  });

  List<String> allowedActions;

  List<FilesUserItemResponse> items;

  String parentFileId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is FilesUserListResponse &&
          _deepEquality.equals(other.allowedActions, allowedActions) &&
          _deepEquality.equals(other.items, items) &&
          other.parentFileId == parentFileId;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (allowedActions.hashCode) + (items.hashCode) + (parentFileId.hashCode);

  @override
  String toString() =>
      'FilesUserListResponse[allowedActions=$allowedActions, items=$items, parentFileId=$parentFileId]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'allowedActions'] = this.allowedActions;
    json[r'items'] = this.items;
    json[r'parentFileId'] = this.parentFileId;
    return json;
  }

  /// Returns a new [FilesUserListResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static FilesUserListResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "FilesUserListResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "FilesUserListResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return FilesUserListResponse(
        allowedActions: json[r'allowedActions'] is Iterable
            ? (json[r'allowedActions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        items: FilesUserItemResponse.listFromJson(json[r'items']),
        parentFileId: mapValueOfType<String>(json, r'parentFileId')!,
      );
    }
    return null;
  }

  static List<FilesUserListResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <FilesUserListResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = FilesUserListResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, FilesUserListResponse> mapFromJson(dynamic json) {
    final map = <String, FilesUserListResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = FilesUserListResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of FilesUserListResponse-objects as value to a dart map
  static Map<String, List<FilesUserListResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<FilesUserListResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = FilesUserListResponse.listFromJson(
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
    'items',
    'parentFileId',
  };
}
