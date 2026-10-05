//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardsSyncMetadataResponse {
  /// Returns a new [BoardsSyncMetadataResponse] instance.
  BoardsSyncMetadataResponse({
    this.contextScoped,
    this.lastSyncedAt,
    this.mode,
    this.nextCursors = const {},
    this.provider,
    this.supportSafe,
    this.userWriteAudited,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? contextScoped;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? lastSyncedAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? mode;

  Map<String, String> nextCursors;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? provider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? userWriteAudited;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BoardsSyncMetadataResponse &&
          other.contextScoped == contextScoped &&
          other.lastSyncedAt == lastSyncedAt &&
          other.mode == mode &&
          _deepEquality.equals(other.nextCursors, nextCursors) &&
          other.provider == provider &&
          other.supportSafe == supportSafe &&
          other.userWriteAudited == userWriteAudited;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (contextScoped == null ? 0 : contextScoped!.hashCode) +
      (lastSyncedAt == null ? 0 : lastSyncedAt!.hashCode) +
      (mode == null ? 0 : mode!.hashCode) +
      (nextCursors.hashCode) +
      (provider == null ? 0 : provider!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (userWriteAudited == null ? 0 : userWriteAudited!.hashCode);

  @override
  String toString() =>
      'BoardsSyncMetadataResponse[contextScoped=$contextScoped, lastSyncedAt=$lastSyncedAt, mode=$mode, nextCursors=$nextCursors, provider=$provider, supportSafe=$supportSafe, userWriteAudited=$userWriteAudited]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.contextScoped != null) {
      json[r'contextScoped'] = this.contextScoped;
    } else {
      json[r'contextScoped'] = null;
    }
    if (this.lastSyncedAt != null) {
      json[r'lastSyncedAt'] = this.lastSyncedAt!.toUtc().toIso8601String();
    } else {
      json[r'lastSyncedAt'] = null;
    }
    if (this.mode != null) {
      json[r'mode'] = this.mode;
    } else {
      json[r'mode'] = null;
    }
    json[r'nextCursors'] = this.nextCursors;
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    if (this.userWriteAudited != null) {
      json[r'userWriteAudited'] = this.userWriteAudited;
    } else {
      json[r'userWriteAudited'] = null;
    }
    return json;
  }

  /// Returns a new [BoardsSyncMetadataResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static BoardsSyncMetadataResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "BoardsSyncMetadataResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "BoardsSyncMetadataResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return BoardsSyncMetadataResponse(
        contextScoped: mapValueOfType<bool>(json, r'contextScoped'),
        lastSyncedAt: mapDateTime(json, r'lastSyncedAt', r''),
        mode: mapValueOfType<String>(json, r'mode'),
        nextCursors:
            mapCastOfType<String, String>(json, r'nextCursors') ?? const {},
        provider: mapValueOfType<String>(json, r'provider'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        userWriteAudited: mapValueOfType<bool>(json, r'userWriteAudited'),
      );
    }
    return null;
  }

  static List<BoardsSyncMetadataResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardsSyncMetadataResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardsSyncMetadataResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, BoardsSyncMetadataResponse> mapFromJson(dynamic json) {
    final map = <String, BoardsSyncMetadataResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = BoardsSyncMetadataResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of BoardsSyncMetadataResponse-objects as value to a dart map
  static Map<String, List<BoardsSyncMetadataResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<BoardsSyncMetadataResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = BoardsSyncMetadataResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{};
}
