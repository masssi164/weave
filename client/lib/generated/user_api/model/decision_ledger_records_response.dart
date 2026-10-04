//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DecisionLedgerRecordsResponse {
  /// Returns a new [DecisionLedgerRecordsResponse] instance.
  DecisionLedgerRecordsResponse({
    this.backgroundRoomReadingEnabled,
    this.contextId,
    this.conversationId,
    this.evidencePosture,
    this.records = const [],
  });

  /// Whether background room reading was used to populate this response. Always false for Sprint 4.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? backgroundRoomReadingEnabled;

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

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DecisionLedgerEvidencePostureResponse? evidencePosture;

  List<DecisionLedgerRecordResponse> records;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DecisionLedgerRecordsResponse &&
          other.backgroundRoomReadingEnabled == backgroundRoomReadingEnabled &&
          other.contextId == contextId &&
          other.conversationId == conversationId &&
          other.evidencePosture == evidencePosture &&
          _deepEquality.equals(other.records, records);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (backgroundRoomReadingEnabled == null
          ? 0
          : backgroundRoomReadingEnabled!.hashCode) +
      (contextId == null ? 0 : contextId!.hashCode) +
      (conversationId == null ? 0 : conversationId!.hashCode) +
      (evidencePosture == null ? 0 : evidencePosture!.hashCode) +
      (records.hashCode);

  @override
  String toString() =>
      'DecisionLedgerRecordsResponse[backgroundRoomReadingEnabled=$backgroundRoomReadingEnabled, contextId=$contextId, conversationId=$conversationId, evidencePosture=$evidencePosture, records=$records]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.backgroundRoomReadingEnabled != null) {
      json[r'backgroundRoomReadingEnabled'] = this.backgroundRoomReadingEnabled;
    } else {
      json[r'backgroundRoomReadingEnabled'] = null;
    }
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
    if (this.evidencePosture != null) {
      json[r'evidencePosture'] = this.evidencePosture;
    } else {
      json[r'evidencePosture'] = null;
    }
    json[r'records'] = this.records;
    return json;
  }

  /// Returns a new [DecisionLedgerRecordsResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DecisionLedgerRecordsResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DecisionLedgerRecordsResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DecisionLedgerRecordsResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DecisionLedgerRecordsResponse(
        backgroundRoomReadingEnabled:
            mapValueOfType<bool>(json, r'backgroundRoomReadingEnabled'),
        contextId: mapValueOfType<String>(json, r'contextId'),
        conversationId: mapValueOfType<String>(json, r'conversationId'),
        evidencePosture: DecisionLedgerEvidencePostureResponse.fromJson(
            json[r'evidencePosture']),
        records: DecisionLedgerRecordResponse.listFromJson(json[r'records']),
      );
    }
    return null;
  }

  static List<DecisionLedgerRecordsResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DecisionLedgerRecordsResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DecisionLedgerRecordsResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DecisionLedgerRecordsResponse> mapFromJson(dynamic json) {
    final map = <String, DecisionLedgerRecordsResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DecisionLedgerRecordsResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DecisionLedgerRecordsResponse-objects as value to a dart map
  static Map<String, List<DecisionLedgerRecordsResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DecisionLedgerRecordsResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DecisionLedgerRecordsResponse.listFromJson(
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
