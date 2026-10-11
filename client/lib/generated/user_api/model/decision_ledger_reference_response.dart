//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DecisionLedgerReferenceResponse {
  /// Returns a new [DecisionLedgerReferenceResponse] instance.
  DecisionLedgerReferenceResponse({
    this.excerpt,
    this.label,
    this.ref,
    this.type,
  });

  /// Short support-safe evidence excerpt.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? excerpt;

  /// Member-visible support-safe source label.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  /// Stable Weave reference, never a raw provider id or URL.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? ref;

  /// Reference type in Weave vocabulary.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? type;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DecisionLedgerReferenceResponse &&
          other.excerpt == excerpt &&
          other.label == label &&
          other.ref == ref &&
          other.type == type;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (excerpt == null ? 0 : excerpt!.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (ref == null ? 0 : ref!.hashCode) +
      (type == null ? 0 : type!.hashCode);

  @override
  String toString() =>
      'DecisionLedgerReferenceResponse[excerpt=$excerpt, label=$label, ref=$ref, type=$type]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.excerpt != null) {
      json[r'excerpt'] = this.excerpt;
    } else {
      json[r'excerpt'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    if (this.ref != null) {
      json[r'ref'] = this.ref;
    } else {
      json[r'ref'] = null;
    }
    if (this.type != null) {
      json[r'type'] = this.type;
    } else {
      json[r'type'] = null;
    }
    return json;
  }

  /// Returns a new [DecisionLedgerReferenceResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DecisionLedgerReferenceResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DecisionLedgerReferenceResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DecisionLedgerReferenceResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DecisionLedgerReferenceResponse(
        excerpt: mapValueOfType<String>(json, r'excerpt'),
        label: mapValueOfType<String>(json, r'label'),
        ref: mapValueOfType<String>(json, r'ref'),
        type: mapValueOfType<String>(json, r'type'),
      );
    }
    return null;
  }

  static List<DecisionLedgerReferenceResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DecisionLedgerReferenceResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DecisionLedgerReferenceResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DecisionLedgerReferenceResponse> mapFromJson(
      dynamic json) {
    final map = <String, DecisionLedgerReferenceResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DecisionLedgerReferenceResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DecisionLedgerReferenceResponse-objects as value to a dart map
  static Map<String, List<DecisionLedgerReferenceResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DecisionLedgerReferenceResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DecisionLedgerReferenceResponse.listFromJson(
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
