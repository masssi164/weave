//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SlackOAuthCallbackResponse {
  /// Returns a new [SlackOAuthCallbackResponse] instance.
  SlackOAuthCallbackResponse({
    this.credentialStored,
    this.installed,
    this.message,
    this.provider,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? credentialStored;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? installed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? message;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? provider;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SlackOAuthCallbackResponse &&
          other.credentialStored == credentialStored &&
          other.installed == installed &&
          other.message == message &&
          other.provider == provider;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (credentialStored == null ? 0 : credentialStored!.hashCode) +
      (installed == null ? 0 : installed!.hashCode) +
      (message == null ? 0 : message!.hashCode) +
      (provider == null ? 0 : provider!.hashCode);

  @override
  String toString() =>
      'SlackOAuthCallbackResponse[credentialStored=$credentialStored, installed=$installed, message=$message, provider=$provider]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.credentialStored != null) {
      json[r'credentialStored'] = this.credentialStored;
    } else {
      json[r'credentialStored'] = null;
    }
    if (this.installed != null) {
      json[r'installed'] = this.installed;
    } else {
      json[r'installed'] = null;
    }
    if (this.message != null) {
      json[r'message'] = this.message;
    } else {
      json[r'message'] = null;
    }
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
    }
    return json;
  }

  /// Returns a new [SlackOAuthCallbackResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SlackOAuthCallbackResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SlackOAuthCallbackResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SlackOAuthCallbackResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SlackOAuthCallbackResponse(
        credentialStored: mapValueOfType<bool>(json, r'credentialStored'),
        installed: mapValueOfType<bool>(json, r'installed'),
        message: mapValueOfType<String>(json, r'message'),
        provider: mapValueOfType<String>(json, r'provider'),
      );
    }
    return null;
  }

  static List<SlackOAuthCallbackResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SlackOAuthCallbackResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SlackOAuthCallbackResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SlackOAuthCallbackResponse> mapFromJson(dynamic json) {
    final map = <String, SlackOAuthCallbackResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SlackOAuthCallbackResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SlackOAuthCallbackResponse-objects as value to a dart map
  static Map<String, List<SlackOAuthCallbackResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SlackOAuthCallbackResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SlackOAuthCallbackResponse.listFromJson(
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
