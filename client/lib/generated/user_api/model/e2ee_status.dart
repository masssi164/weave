//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class E2eeStatus {
  /// Returns a new [E2eeStatus] instance.
  E2eeStatus({
    this.accessibilityReviewed,
    this.action,
    this.deviceVerificationValidated,
    this.encryptedRoomsValidated,
    this.keyBackupValidated,
    this.lostDeviceRecoveryValidated,
    this.multiDeviceValidated,
    this.source_,
    this.status,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? accessibilityReviewed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? action;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? deviceVerificationValidated;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? encryptedRoomsValidated;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? keyBackupValidated;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? lostDeviceRecoveryValidated;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? multiDeviceValidated;

  /// Support-safe source of this status; never a room message-body inspection source.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? source_;

  /// validated only when encrypted rooms, device verification, recovery, lost-device, multi-device, and accessibility gates are complete.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? status;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is E2eeStatus &&
          other.accessibilityReviewed == accessibilityReviewed &&
          other.action == action &&
          other.deviceVerificationValidated == deviceVerificationValidated &&
          other.encryptedRoomsValidated == encryptedRoomsValidated &&
          other.keyBackupValidated == keyBackupValidated &&
          other.lostDeviceRecoveryValidated == lostDeviceRecoveryValidated &&
          other.multiDeviceValidated == multiDeviceValidated &&
          other.source_ == source_ &&
          other.status == status;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessibilityReviewed == null ? 0 : accessibilityReviewed!.hashCode) +
      (action == null ? 0 : action!.hashCode) +
      (deviceVerificationValidated == null
          ? 0
          : deviceVerificationValidated!.hashCode) +
      (encryptedRoomsValidated == null
          ? 0
          : encryptedRoomsValidated!.hashCode) +
      (keyBackupValidated == null ? 0 : keyBackupValidated!.hashCode) +
      (lostDeviceRecoveryValidated == null
          ? 0
          : lostDeviceRecoveryValidated!.hashCode) +
      (multiDeviceValidated == null ? 0 : multiDeviceValidated!.hashCode) +
      (source_ == null ? 0 : source_!.hashCode) +
      (status == null ? 0 : status!.hashCode);

  @override
  String toString() =>
      'E2eeStatus[accessibilityReviewed=$accessibilityReviewed, action=$action, deviceVerificationValidated=$deviceVerificationValidated, encryptedRoomsValidated=$encryptedRoomsValidated, keyBackupValidated=$keyBackupValidated, lostDeviceRecoveryValidated=$lostDeviceRecoveryValidated, multiDeviceValidated=$multiDeviceValidated, source_=$source_, status=$status]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accessibilityReviewed != null) {
      json[r'accessibilityReviewed'] = this.accessibilityReviewed;
    } else {
      json[r'accessibilityReviewed'] = null;
    }
    if (this.action != null) {
      json[r'action'] = this.action;
    } else {
      json[r'action'] = null;
    }
    if (this.deviceVerificationValidated != null) {
      json[r'deviceVerificationValidated'] = this.deviceVerificationValidated;
    } else {
      json[r'deviceVerificationValidated'] = null;
    }
    if (this.encryptedRoomsValidated != null) {
      json[r'encryptedRoomsValidated'] = this.encryptedRoomsValidated;
    } else {
      json[r'encryptedRoomsValidated'] = null;
    }
    if (this.keyBackupValidated != null) {
      json[r'keyBackupValidated'] = this.keyBackupValidated;
    } else {
      json[r'keyBackupValidated'] = null;
    }
    if (this.lostDeviceRecoveryValidated != null) {
      json[r'lostDeviceRecoveryValidated'] = this.lostDeviceRecoveryValidated;
    } else {
      json[r'lostDeviceRecoveryValidated'] = null;
    }
    if (this.multiDeviceValidated != null) {
      json[r'multiDeviceValidated'] = this.multiDeviceValidated;
    } else {
      json[r'multiDeviceValidated'] = null;
    }
    if (this.source_ != null) {
      json[r'source'] = this.source_;
    } else {
      json[r'source'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    return json;
  }

  /// Returns a new [E2eeStatus] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static E2eeStatus? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "E2eeStatus[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "E2eeStatus[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return E2eeStatus(
        accessibilityReviewed:
            mapValueOfType<bool>(json, r'accessibilityReviewed'),
        action: mapValueOfType<String>(json, r'action'),
        deviceVerificationValidated:
            mapValueOfType<bool>(json, r'deviceVerificationValidated'),
        encryptedRoomsValidated:
            mapValueOfType<bool>(json, r'encryptedRoomsValidated'),
        keyBackupValidated: mapValueOfType<bool>(json, r'keyBackupValidated'),
        lostDeviceRecoveryValidated:
            mapValueOfType<bool>(json, r'lostDeviceRecoveryValidated'),
        multiDeviceValidated:
            mapValueOfType<bool>(json, r'multiDeviceValidated'),
        source_: mapValueOfType<String>(json, r'source'),
        status: mapValueOfType<String>(json, r'status'),
      );
    }
    return null;
  }

  static List<E2eeStatus> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <E2eeStatus>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = E2eeStatus.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, E2eeStatus> mapFromJson(dynamic json) {
    final map = <String, E2eeStatus>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = E2eeStatus.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of E2eeStatus-objects as value to a dart map
  static Map<String, List<E2eeStatus>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<E2eeStatus>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = E2eeStatus.listFromJson(
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
