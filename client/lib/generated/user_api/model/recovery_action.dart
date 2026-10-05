//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class RecoveryAction {
  /// Returns a new [RecoveryAction] instance.
  RecoveryAction({
    this.code,
    this.label,
    this.supportReference,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? code;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? supportReference;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RecoveryAction &&
          other.code == code &&
          other.label == label &&
          other.supportReference == supportReference;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (code == null ? 0 : code!.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (supportReference == null ? 0 : supportReference!.hashCode);

  @override
  String toString() =>
      'RecoveryAction[code=$code, label=$label, supportReference=$supportReference]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.code != null) {
      json[r'code'] = this.code;
    } else {
      json[r'code'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    if (this.supportReference != null) {
      json[r'supportReference'] = this.supportReference;
    } else {
      json[r'supportReference'] = null;
    }
    return json;
  }

  /// Returns a new [RecoveryAction] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static RecoveryAction? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "RecoveryAction[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "RecoveryAction[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return RecoveryAction(
        code: mapValueOfType<String>(json, r'code'),
        label: mapValueOfType<String>(json, r'label'),
        supportReference: mapValueOfType<String>(json, r'supportReference'),
      );
    }
    return null;
  }

  static List<RecoveryAction> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <RecoveryAction>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = RecoveryAction.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, RecoveryAction> mapFromJson(dynamic json) {
    final map = <String, RecoveryAction>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = RecoveryAction.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of RecoveryAction-objects as value to a dart map
  static Map<String, List<RecoveryAction>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<RecoveryAction>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = RecoveryAction.listFromJson(
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
