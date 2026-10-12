//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ApiErrorResponse {
  /// Returns a new [ApiErrorResponse] instance.
  ApiErrorResponse({
    required this.code,
    this.details = const {},
    this.memberImpact,
    required this.message,
    required this.requestId,
    required this.supportRef,
  });

  /// Stable machine-readable error code.
  String code;

  /// Non-secret diagnostic details for this failure.
  Map<String, Object> details;

  /// Optional member-facing impact state or recovery hint owned by the backend facade.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? memberImpact;

  /// Support-safe explanation of what failed.
  String message;

  /// Correlation identifier for support and log lookup.
  String requestId;

  /// Stable reference members can share with support without provider payloads.
  String supportRef;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ApiErrorResponse &&
          other.code == code &&
          _deepEquality.equals(other.details, details) &&
          other.memberImpact == memberImpact &&
          other.message == message &&
          other.requestId == requestId &&
          other.supportRef == supportRef;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (code.hashCode) +
      (details.hashCode) +
      (memberImpact == null ? 0 : memberImpact!.hashCode) +
      (message.hashCode) +
      (requestId.hashCode) +
      (supportRef.hashCode);

  @override
  String toString() =>
      'ApiErrorResponse[code=$code, details=$details, memberImpact=$memberImpact, message=$message, requestId=$requestId, supportRef=$supportRef]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'code'] = this.code;
    json[r'details'] = this.details;
    if (this.memberImpact != null) {
      json[r'memberImpact'] = this.memberImpact;
    } else {
      json[r'memberImpact'] = null;
    }
    json[r'message'] = this.message;
    json[r'requestId'] = this.requestId;
    json[r'supportRef'] = this.supportRef;
    return json;
  }

  /// Returns a new [ApiErrorResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ApiErrorResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ApiErrorResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ApiErrorResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ApiErrorResponse(
        code: mapValueOfType<String>(json, r'code')!,
        details: mapCastOfType<String, Object>(json, r'details')!,
        memberImpact: mapValueOfType<String>(json, r'memberImpact'),
        message: mapValueOfType<String>(json, r'message')!,
        requestId: mapValueOfType<String>(json, r'requestId')!,
        supportRef: mapValueOfType<String>(json, r'supportRef')!,
      );
    }
    return null;
  }

  static List<ApiErrorResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ApiErrorResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ApiErrorResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ApiErrorResponse> mapFromJson(dynamic json) {
    final map = <String, ApiErrorResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ApiErrorResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ApiErrorResponse-objects as value to a dart map
  static Map<String, List<ApiErrorResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ApiErrorResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ApiErrorResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'code',
    'details',
    'message',
    'requestId',
    'supportRef',
  };
}
