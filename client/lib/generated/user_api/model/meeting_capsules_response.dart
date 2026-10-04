//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class MeetingCapsulesResponse {
  /// Returns a new [MeetingCapsulesResponse] instance.
  MeetingCapsulesResponse({
    this.capsules = const [],
    this.contextId,
    this.conversationId,
    this.failClosed,
  });

  List<MeetingCapsuleResponse> capsules;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? contextId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? conversationId;

  /// Whether media controls are unavailable until a backend capability is configured.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? failClosed;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MeetingCapsulesResponse &&
          _deepEquality.equals(other.capsules, capsules) &&
          other.contextId == contextId &&
          other.conversationId == conversationId &&
          other.failClosed == failClosed;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (capsules.hashCode) +
      (contextId == null ? 0 : contextId!.hashCode) +
      (conversationId == null ? 0 : conversationId!.hashCode) +
      (failClosed == null ? 0 : failClosed!.hashCode);

  @override
  String toString() =>
      'MeetingCapsulesResponse[capsules=$capsules, contextId=$contextId, conversationId=$conversationId, failClosed=$failClosed]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'capsules'] = this.capsules;
    if (this.contextId != null) {
      json[r'contextId'] = this.contextId;
    } else {
      json[r'contextId'] = null;
    }
    if (this.conversationId != null) {
      json[r'conversationId'] = this.conversationId;
    } else {
      json[r'conversationId'] = null;
    }
    if (this.failClosed != null) {
      json[r'failClosed'] = this.failClosed;
    } else {
      json[r'failClosed'] = null;
    }
    return json;
  }

  /// Returns a new [MeetingCapsulesResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static MeetingCapsulesResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "MeetingCapsulesResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "MeetingCapsulesResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return MeetingCapsulesResponse(
        capsules: MeetingCapsuleResponse.listFromJson(json[r'capsules']),
        contextId: mapValueOfType<String>(json, r'contextId'),
        conversationId: mapValueOfType<String>(json, r'conversationId'),
        failClosed: mapValueOfType<bool>(json, r'failClosed'),
      );
    }
    return null;
  }

  static List<MeetingCapsulesResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <MeetingCapsulesResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = MeetingCapsulesResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, MeetingCapsulesResponse> mapFromJson(dynamic json) {
    final map = <String, MeetingCapsulesResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = MeetingCapsulesResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of MeetingCapsulesResponse-objects as value to a dart map
  static Map<String, List<MeetingCapsulesResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<MeetingCapsulesResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = MeetingCapsulesResponse.listFromJson(
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
