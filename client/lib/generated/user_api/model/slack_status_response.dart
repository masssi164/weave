//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SlackStatusResponse {
  /// Returns a new [SlackStatusResponse] instance.
  SlackStatusResponse({
    this.actions = const [],
    this.channelMappingConfigured,
    this.degradedStates = const [],
    this.enabled,
    this.oauthConfigured,
    this.productionCallsAllowed,
    this.readiness,
    this.signingConfigured,
    this.status,
    this.tokenReferenceConfigured,
  });

  List<String> actions;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? channelMappingConfigured;

  List<String> degradedStates;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? oauthConfigured;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? productionCallsAllowed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? readiness;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? signingConfigured;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? status;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? tokenReferenceConfigured;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SlackStatusResponse &&
          _deepEquality.equals(other.actions, actions) &&
          other.channelMappingConfigured == channelMappingConfigured &&
          _deepEquality.equals(other.degradedStates, degradedStates) &&
          other.enabled == enabled &&
          other.oauthConfigured == oauthConfigured &&
          other.productionCallsAllowed == productionCallsAllowed &&
          other.readiness == readiness &&
          other.signingConfigured == signingConfigured &&
          other.status == status &&
          other.tokenReferenceConfigured == tokenReferenceConfigured;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actions.hashCode) +
      (channelMappingConfigured == null
          ? 0
          : channelMappingConfigured!.hashCode) +
      (degradedStates.hashCode) +
      (enabled == null ? 0 : enabled!.hashCode) +
      (oauthConfigured == null ? 0 : oauthConfigured!.hashCode) +
      (productionCallsAllowed == null ? 0 : productionCallsAllowed!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (signingConfigured == null ? 0 : signingConfigured!.hashCode) +
      (status == null ? 0 : status!.hashCode) +
      (tokenReferenceConfigured == null
          ? 0
          : tokenReferenceConfigured!.hashCode);

  @override
  String toString() =>
      'SlackStatusResponse[actions=$actions, channelMappingConfigured=$channelMappingConfigured, degradedStates=$degradedStates, enabled=$enabled, oauthConfigured=$oauthConfigured, productionCallsAllowed=$productionCallsAllowed, readiness=$readiness, signingConfigured=$signingConfigured, status=$status, tokenReferenceConfigured=$tokenReferenceConfigured]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'actions'] = this.actions;
    if (this.channelMappingConfigured != null) {
      json[r'channelMappingConfigured'] = this.channelMappingConfigured;
    } else {
      json[r'channelMappingConfigured'] = null;
    }
    json[r'degradedStates'] = this.degradedStates;
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    if (this.oauthConfigured != null) {
      json[r'oauthConfigured'] = this.oauthConfigured;
    } else {
      json[r'oauthConfigured'] = null;
    }
    if (this.productionCallsAllowed != null) {
      json[r'productionCallsAllowed'] = this.productionCallsAllowed;
    } else {
      json[r'productionCallsAllowed'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.signingConfigured != null) {
      json[r'signingConfigured'] = this.signingConfigured;
    } else {
      json[r'signingConfigured'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    if (this.tokenReferenceConfigured != null) {
      json[r'tokenReferenceConfigured'] = this.tokenReferenceConfigured;
    } else {
      json[r'tokenReferenceConfigured'] = null;
    }
    return json;
  }

  /// Returns a new [SlackStatusResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SlackStatusResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SlackStatusResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SlackStatusResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SlackStatusResponse(
        actions: json[r'actions'] is Iterable
            ? (json[r'actions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        channelMappingConfigured:
            mapValueOfType<bool>(json, r'channelMappingConfigured'),
        degradedStates: json[r'degradedStates'] is Iterable
            ? (json[r'degradedStates'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        enabled: mapValueOfType<bool>(json, r'enabled'),
        oauthConfigured: mapValueOfType<bool>(json, r'oauthConfigured'),
        productionCallsAllowed:
            mapValueOfType<bool>(json, r'productionCallsAllowed'),
        readiness: mapValueOfType<String>(json, r'readiness'),
        signingConfigured: mapValueOfType<bool>(json, r'signingConfigured'),
        status: mapValueOfType<String>(json, r'status'),
        tokenReferenceConfigured:
            mapValueOfType<bool>(json, r'tokenReferenceConfigured'),
      );
    }
    return null;
  }

  static List<SlackStatusResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SlackStatusResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SlackStatusResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SlackStatusResponse> mapFromJson(dynamic json) {
    final map = <String, SlackStatusResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SlackStatusResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SlackStatusResponse-objects as value to a dart map
  static Map<String, List<SlackStatusResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SlackStatusResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SlackStatusResponse.listFromJson(
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
