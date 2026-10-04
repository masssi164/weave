//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ChatHistoryPolicy {
  /// Returns a new [ChatHistoryPolicy] instance.
  ChatHistoryPolicy({
    this.exportAllowed,
    this.redactOnMembershipRemoval,
    this.retention,
    this.supportSafeNotes = const [],
    this.visibility,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? exportAllowed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? redactOnMembershipRemoval;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? retention;

  List<String> supportSafeNotes;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? visibility;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ChatHistoryPolicy &&
          other.exportAllowed == exportAllowed &&
          other.redactOnMembershipRemoval == redactOnMembershipRemoval &&
          other.retention == retention &&
          _deepEquality.equals(other.supportSafeNotes, supportSafeNotes) &&
          other.visibility == visibility;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (exportAllowed == null ? 0 : exportAllowed!.hashCode) +
      (redactOnMembershipRemoval == null
          ? 0
          : redactOnMembershipRemoval!.hashCode) +
      (retention == null ? 0 : retention!.hashCode) +
      (supportSafeNotes.hashCode) +
      (visibility == null ? 0 : visibility!.hashCode);

  @override
  String toString() =>
      'ChatHistoryPolicy[exportAllowed=$exportAllowed, redactOnMembershipRemoval=$redactOnMembershipRemoval, retention=$retention, supportSafeNotes=$supportSafeNotes, visibility=$visibility]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.exportAllowed != null) {
      json[r'exportAllowed'] = this.exportAllowed;
    } else {
      json[r'exportAllowed'] = null;
    }
    if (this.redactOnMembershipRemoval != null) {
      json[r'redactOnMembershipRemoval'] = this.redactOnMembershipRemoval;
    } else {
      json[r'redactOnMembershipRemoval'] = null;
    }
    if (this.retention != null) {
      json[r'retention'] = this.retention;
    } else {
      json[r'retention'] = null;
    }
    json[r'supportSafeNotes'] = this.supportSafeNotes;
    if (this.visibility != null) {
      json[r'visibility'] = this.visibility;
    } else {
      json[r'visibility'] = null;
    }
    return json;
  }

  /// Returns a new [ChatHistoryPolicy] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ChatHistoryPolicy? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ChatHistoryPolicy[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ChatHistoryPolicy[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ChatHistoryPolicy(
        exportAllowed: mapValueOfType<bool>(json, r'exportAllowed'),
        redactOnMembershipRemoval:
            mapValueOfType<bool>(json, r'redactOnMembershipRemoval'),
        retention: mapValueOfType<String>(json, r'retention'),
        supportSafeNotes: json[r'supportSafeNotes'] is Iterable
            ? (json[r'supportSafeNotes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        visibility: mapValueOfType<String>(json, r'visibility'),
      );
    }
    return null;
  }

  static List<ChatHistoryPolicy> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ChatHistoryPolicy>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ChatHistoryPolicy.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ChatHistoryPolicy> mapFromJson(dynamic json) {
    final map = <String, ChatHistoryPolicy>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ChatHistoryPolicy.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ChatHistoryPolicy-objects as value to a dart map
  static Map<String, List<ChatHistoryPolicy>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ChatHistoryPolicy>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ChatHistoryPolicy.listFromJson(
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
