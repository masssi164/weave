//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DecisionLedgerEvidencePostureResponse {
  /// Returns a new [DecisionLedgerEvidencePostureResponse] instance.
  DecisionLedgerEvidencePostureResponse({
    this.auditRefs = const [],
    this.exportPosture,
    this.provenance,
    this.supportSafe,
  });

  /// Support-safe audit references for this decision snapshot.
  List<String> auditRefs;

  /// Export posture summary for decisions/evidence records.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? exportPosture;

  /// Human-readable provenance summary without raw provider identifiers.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? provenance;

  /// Whether the posture remains safe for member/support surfaces.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DecisionLedgerEvidencePostureResponse &&
          _deepEquality.equals(other.auditRefs, auditRefs) &&
          other.exportPosture == exportPosture &&
          other.provenance == provenance &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (auditRefs.hashCode) +
      (exportPosture == null ? 0 : exportPosture!.hashCode) +
      (provenance == null ? 0 : provenance!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'DecisionLedgerEvidencePostureResponse[auditRefs=$auditRefs, exportPosture=$exportPosture, provenance=$provenance, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'auditRefs'] = this.auditRefs;
    if (this.exportPosture != null) {
      json[r'exportPosture'] = this.exportPosture;
    } else {
      json[r'exportPosture'] = null;
    }
    if (this.provenance != null) {
      json[r'provenance'] = this.provenance;
    } else {
      json[r'provenance'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [DecisionLedgerEvidencePostureResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DecisionLedgerEvidencePostureResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DecisionLedgerEvidencePostureResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DecisionLedgerEvidencePostureResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DecisionLedgerEvidencePostureResponse(
        auditRefs: json[r'auditRefs'] is Iterable
            ? (json[r'auditRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        exportPosture: mapValueOfType<String>(json, r'exportPosture'),
        provenance: mapValueOfType<String>(json, r'provenance'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<DecisionLedgerEvidencePostureResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DecisionLedgerEvidencePostureResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DecisionLedgerEvidencePostureResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DecisionLedgerEvidencePostureResponse> mapFromJson(
      dynamic json) {
    final map = <String, DecisionLedgerEvidencePostureResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value =
            DecisionLedgerEvidencePostureResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DecisionLedgerEvidencePostureResponse-objects as value to a dart map
  static Map<String, List<DecisionLedgerEvidencePostureResponse>>
      mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DecisionLedgerEvidencePostureResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DecisionLedgerEvidencePostureResponse.listFromJson(
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
