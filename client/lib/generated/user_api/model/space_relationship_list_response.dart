//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SpaceRelationshipListResponse {
  /// Returns a new [SpaceRelationshipListResponse] instance.
  SpaceRelationshipListResponse({
    this.nextAfterRelationRef,
    this.relationships = const [],
  });

  /// Opaque cursor for the next bounded direct-relationship page.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? nextAfterRelationRef;

  List<SpaceRelationshipResponse> relationships;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SpaceRelationshipListResponse &&
          other.nextAfterRelationRef == nextAfterRelationRef &&
          _deepEquality.equals(other.relationships, relationships);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (nextAfterRelationRef == null ? 0 : nextAfterRelationRef!.hashCode) +
      (relationships.hashCode);

  @override
  String toString() =>
      'SpaceRelationshipListResponse[nextAfterRelationRef=$nextAfterRelationRef, relationships=$relationships]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.nextAfterRelationRef != null) {
      json[r'nextAfterRelationRef'] = this.nextAfterRelationRef;
    } else {
      json[r'nextAfterRelationRef'] = null;
    }
    json[r'relationships'] = this.relationships;
    return json;
  }

  /// Returns a new [SpaceRelationshipListResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SpaceRelationshipListResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SpaceRelationshipListResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SpaceRelationshipListResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SpaceRelationshipListResponse(
        nextAfterRelationRef:
            mapValueOfType<String>(json, r'nextAfterRelationRef'),
        relationships:
            SpaceRelationshipResponse.listFromJson(json[r'relationships']),
      );
    }
    return null;
  }

  static List<SpaceRelationshipListResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SpaceRelationshipListResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SpaceRelationshipListResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SpaceRelationshipListResponse> mapFromJson(dynamic json) {
    final map = <String, SpaceRelationshipListResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SpaceRelationshipListResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SpaceRelationshipListResponse-objects as value to a dart map
  static Map<String, List<SpaceRelationshipListResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SpaceRelationshipListResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SpaceRelationshipListResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'relationships',
  };
}
