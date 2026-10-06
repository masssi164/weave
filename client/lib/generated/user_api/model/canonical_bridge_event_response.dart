//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CanonicalBridgeEventResponse {
  /// Returns a new [CanonicalBridgeEventResponse] instance.
  CanonicalBridgeEventResponse({
    this.actorRef,
    this.direction,
    this.dryRunOnly,
    this.idempotencyKey,
    this.payload = const {},
    this.provider,
    this.roomRef,
    this.type,
    this.workspaceRef,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? actorRef;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? direction;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? dryRunOnly;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? idempotencyKey;

  Map<String, Object> payload;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? provider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? roomRef;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? type;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? workspaceRef;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CanonicalBridgeEventResponse &&
          other.actorRef == actorRef &&
          other.direction == direction &&
          other.dryRunOnly == dryRunOnly &&
          other.idempotencyKey == idempotencyKey &&
          _deepEquality.equals(other.payload, payload) &&
          other.provider == provider &&
          other.roomRef == roomRef &&
          other.type == type &&
          other.workspaceRef == workspaceRef;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (actorRef == null ? 0 : actorRef!.hashCode) +
      (direction == null ? 0 : direction!.hashCode) +
      (dryRunOnly == null ? 0 : dryRunOnly!.hashCode) +
      (idempotencyKey == null ? 0 : idempotencyKey!.hashCode) +
      (payload.hashCode) +
      (provider == null ? 0 : provider!.hashCode) +
      (roomRef == null ? 0 : roomRef!.hashCode) +
      (type == null ? 0 : type!.hashCode) +
      (workspaceRef == null ? 0 : workspaceRef!.hashCode);

  @override
  String toString() =>
      'CanonicalBridgeEventResponse[actorRef=$actorRef, direction=$direction, dryRunOnly=$dryRunOnly, idempotencyKey=$idempotencyKey, payload=$payload, provider=$provider, roomRef=$roomRef, type=$type, workspaceRef=$workspaceRef]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.actorRef != null) {
      json[r'actorRef'] = this.actorRef;
    } else {
      json[r'actorRef'] = null;
    }
    if (this.direction != null) {
      json[r'direction'] = this.direction;
    } else {
      json[r'direction'] = null;
    }
    if (this.dryRunOnly != null) {
      json[r'dryRunOnly'] = this.dryRunOnly;
    } else {
      json[r'dryRunOnly'] = null;
    }
    if (this.idempotencyKey != null) {
      json[r'idempotencyKey'] = this.idempotencyKey;
    } else {
      json[r'idempotencyKey'] = null;
    }
    json[r'payload'] = this.payload;
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
    }
    if (this.roomRef != null) {
      json[r'roomRef'] = this.roomRef;
    } else {
      json[r'roomRef'] = null;
    }
    if (this.type != null) {
      json[r'type'] = this.type;
    } else {
      json[r'type'] = null;
    }
    if (this.workspaceRef != null) {
      json[r'workspaceRef'] = this.workspaceRef;
    } else {
      json[r'workspaceRef'] = null;
    }
    return json;
  }

  /// Returns a new [CanonicalBridgeEventResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CanonicalBridgeEventResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CanonicalBridgeEventResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CanonicalBridgeEventResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CanonicalBridgeEventResponse(
        actorRef: mapValueOfType<String>(json, r'actorRef'),
        direction: mapValueOfType<String>(json, r'direction'),
        dryRunOnly: mapValueOfType<bool>(json, r'dryRunOnly'),
        idempotencyKey: mapValueOfType<String>(json, r'idempotencyKey'),
        payload: mapCastOfType<String, Object>(json, r'payload') ?? const {},
        provider: mapValueOfType<String>(json, r'provider'),
        roomRef: mapValueOfType<String>(json, r'roomRef'),
        type: mapValueOfType<String>(json, r'type'),
        workspaceRef: mapValueOfType<String>(json, r'workspaceRef'),
      );
    }
    return null;
  }

  static List<CanonicalBridgeEventResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CanonicalBridgeEventResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CanonicalBridgeEventResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CanonicalBridgeEventResponse> mapFromJson(dynamic json) {
    final map = <String, CanonicalBridgeEventResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CanonicalBridgeEventResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CanonicalBridgeEventResponse-objects as value to a dart map
  static Map<String, List<CanonicalBridgeEventResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CanonicalBridgeEventResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CanonicalBridgeEventResponse.listFromJson(
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
