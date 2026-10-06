//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class Protocols {
  /// Returns a new [Protocols] instance.
  Protocols({
    required this.matrixClientServerBaseUrl,
    required this.matrixOAuthClientId,
    required this.matrixOAuthIssuer,
  });

  String matrixClientServerBaseUrl;

  String matrixOAuthClientId;

  String matrixOAuthIssuer;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Protocols &&
          other.matrixClientServerBaseUrl == matrixClientServerBaseUrl &&
          other.matrixOAuthClientId == matrixOAuthClientId &&
          other.matrixOAuthIssuer == matrixOAuthIssuer;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (matrixClientServerBaseUrl.hashCode) +
      (matrixOAuthClientId.hashCode) +
      (matrixOAuthIssuer.hashCode);

  @override
  String toString() =>
      'Protocols[matrixClientServerBaseUrl=$matrixClientServerBaseUrl, matrixOAuthClientId=$matrixOAuthClientId, matrixOAuthIssuer=$matrixOAuthIssuer]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'matrixClientServerBaseUrl'] = this.matrixClientServerBaseUrl;
    json[r'matrixOAuthClientId'] = this.matrixOAuthClientId;
    json[r'matrixOAuthIssuer'] = this.matrixOAuthIssuer;
    return json;
  }

  /// Returns a new [Protocols] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static Protocols? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "Protocols[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "Protocols[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return Protocols(
        matrixClientServerBaseUrl:
            mapValueOfType<String>(json, r'matrixClientServerBaseUrl')!,
        matrixOAuthClientId:
            mapValueOfType<String>(json, r'matrixOAuthClientId')!,
        matrixOAuthIssuer: mapValueOfType<String>(json, r'matrixOAuthIssuer')!,
      );
    }
    return null;
  }

  static List<Protocols> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <Protocols>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = Protocols.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, Protocols> mapFromJson(dynamic json) {
    final map = <String, Protocols>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = Protocols.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of Protocols-objects as value to a dart map
  static Map<String, List<Protocols>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<Protocols>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = Protocols.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'matrixClientServerBaseUrl',
    'matrixOAuthClientId',
    'matrixOAuthIssuer',
  };
}
