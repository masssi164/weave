//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ConnectorManifestValidationRequest {
  /// Returns a new [ConnectorManifestValidationRequest] instance.
  ConnectorManifestValidationRequest({
    this.capabilities = const [],
    this.commands = const [],
    this.cursorRefs = const {},
    required this.id,
    required this.provider,
    this.providerWritesEnabled,
    this.redactionPolicy,
    this.releaseStatus,
    this.secretRefs = const {},
    this.webhookRefs = const {},
  });

  List<String> capabilities;

  List<String> commands;

  Map<String, String> cursorRefs;

  String id;

  String provider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? providerWritesEnabled;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? redactionPolicy;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? releaseStatus;

  Map<String, String> secretRefs;

  Map<String, String> webhookRefs;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ConnectorManifestValidationRequest &&
          _deepEquality.equals(other.capabilities, capabilities) &&
          _deepEquality.equals(other.commands, commands) &&
          _deepEquality.equals(other.cursorRefs, cursorRefs) &&
          other.id == id &&
          other.provider == provider &&
          other.providerWritesEnabled == providerWritesEnabled &&
          other.redactionPolicy == redactionPolicy &&
          other.releaseStatus == releaseStatus &&
          _deepEquality.equals(other.secretRefs, secretRefs) &&
          _deepEquality.equals(other.webhookRefs, webhookRefs);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (capabilities.hashCode) +
      (commands.hashCode) +
      (cursorRefs.hashCode) +
      (id.hashCode) +
      (provider.hashCode) +
      (providerWritesEnabled == null ? 0 : providerWritesEnabled!.hashCode) +
      (redactionPolicy == null ? 0 : redactionPolicy!.hashCode) +
      (releaseStatus == null ? 0 : releaseStatus!.hashCode) +
      (secretRefs.hashCode) +
      (webhookRefs.hashCode);

  @override
  String toString() =>
      'ConnectorManifestValidationRequest[capabilities=$capabilities, commands=$commands, cursorRefs=$cursorRefs, id=$id, provider=$provider, providerWritesEnabled=$providerWritesEnabled, redactionPolicy=$redactionPolicy, releaseStatus=$releaseStatus, secretRefs=$secretRefs, webhookRefs=$webhookRefs]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'capabilities'] = this.capabilities;
    json[r'commands'] = this.commands;
    json[r'cursorRefs'] = this.cursorRefs;
    json[r'id'] = this.id;
    json[r'provider'] = this.provider;
    if (this.providerWritesEnabled != null) {
      json[r'providerWritesEnabled'] = this.providerWritesEnabled;
    } else {
      json[r'providerWritesEnabled'] = null;
    }
    if (this.redactionPolicy != null) {
      json[r'redactionPolicy'] = this.redactionPolicy;
    } else {
      json[r'redactionPolicy'] = null;
    }
    if (this.releaseStatus != null) {
      json[r'releaseStatus'] = this.releaseStatus;
    } else {
      json[r'releaseStatus'] = null;
    }
    json[r'secretRefs'] = this.secretRefs;
    json[r'webhookRefs'] = this.webhookRefs;
    return json;
  }

  /// Returns a new [ConnectorManifestValidationRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ConnectorManifestValidationRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ConnectorManifestValidationRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ConnectorManifestValidationRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ConnectorManifestValidationRequest(
        capabilities: json[r'capabilities'] is Iterable
            ? (json[r'capabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        commands: json[r'commands'] is Iterable
            ? (json[r'commands'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        cursorRefs:
            mapCastOfType<String, String>(json, r'cursorRefs') ?? const {},
        id: mapValueOfType<String>(json, r'id')!,
        provider: mapValueOfType<String>(json, r'provider')!,
        providerWritesEnabled:
            mapValueOfType<bool>(json, r'providerWritesEnabled'),
        redactionPolicy: mapValueOfType<String>(json, r'redactionPolicy'),
        releaseStatus: mapValueOfType<String>(json, r'releaseStatus'),
        secretRefs:
            mapCastOfType<String, String>(json, r'secretRefs') ?? const {},
        webhookRefs:
            mapCastOfType<String, String>(json, r'webhookRefs') ?? const {},
      );
    }
    return null;
  }

  static List<ConnectorManifestValidationRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ConnectorManifestValidationRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ConnectorManifestValidationRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ConnectorManifestValidationRequest> mapFromJson(
      dynamic json) {
    final map = <String, ConnectorManifestValidationRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ConnectorManifestValidationRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ConnectorManifestValidationRequest-objects as value to a dart map
  static Map<String, List<ConnectorManifestValidationRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ConnectorManifestValidationRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ConnectorManifestValidationRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'id',
    'provider',
  };
}
