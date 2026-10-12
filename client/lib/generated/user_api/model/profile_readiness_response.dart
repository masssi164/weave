//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProfileReadinessResponse {
  /// Returns a new [ProfileReadinessResponse] instance.
  ProfileReadinessResponse({
    this.backendOwnedFacade,
    this.contractId,
    this.directProviderCallsAllowed,
    this.endpoint,
    this.readiness,
    this.supportSafe,
    this.unsupportedOperations = const [],
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? backendOwnedFacade;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? contractId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? directProviderCallsAllowed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? endpoint;

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
  bool? supportSafe;

  List<String> unsupportedOperations;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProfileReadinessResponse &&
          other.backendOwnedFacade == backendOwnedFacade &&
          other.contractId == contractId &&
          other.directProviderCallsAllowed == directProviderCallsAllowed &&
          other.endpoint == endpoint &&
          other.readiness == readiness &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(
              other.unsupportedOperations, unsupportedOperations);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (backendOwnedFacade == null ? 0 : backendOwnedFacade!.hashCode) +
      (contractId == null ? 0 : contractId!.hashCode) +
      (directProviderCallsAllowed == null
          ? 0
          : directProviderCallsAllowed!.hashCode) +
      (endpoint == null ? 0 : endpoint!.hashCode) +
      (readiness == null ? 0 : readiness!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (unsupportedOperations.hashCode);

  @override
  String toString() =>
      'ProfileReadinessResponse[backendOwnedFacade=$backendOwnedFacade, contractId=$contractId, directProviderCallsAllowed=$directProviderCallsAllowed, endpoint=$endpoint, readiness=$readiness, supportSafe=$supportSafe, unsupportedOperations=$unsupportedOperations]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.backendOwnedFacade != null) {
      json[r'backendOwnedFacade'] = this.backendOwnedFacade;
    } else {
      json[r'backendOwnedFacade'] = null;
    }
    if (this.contractId != null) {
      json[r'contractId'] = this.contractId;
    } else {
      json[r'contractId'] = null;
    }
    if (this.directProviderCallsAllowed != null) {
      json[r'directProviderCallsAllowed'] = this.directProviderCallsAllowed;
    } else {
      json[r'directProviderCallsAllowed'] = null;
    }
    if (this.endpoint != null) {
      json[r'endpoint'] = this.endpoint;
    } else {
      json[r'endpoint'] = null;
    }
    if (this.readiness != null) {
      json[r'readiness'] = this.readiness;
    } else {
      json[r'readiness'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    json[r'unsupportedOperations'] = this.unsupportedOperations;
    return json;
  }

  /// Returns a new [ProfileReadinessResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProfileReadinessResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProfileReadinessResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProfileReadinessResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProfileReadinessResponse(
        backendOwnedFacade: mapValueOfType<bool>(json, r'backendOwnedFacade'),
        contractId: mapValueOfType<String>(json, r'contractId'),
        directProviderCallsAllowed:
            mapValueOfType<bool>(json, r'directProviderCallsAllowed'),
        endpoint: mapValueOfType<String>(json, r'endpoint'),
        readiness: mapValueOfType<String>(json, r'readiness'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        unsupportedOperations: json[r'unsupportedOperations'] is Iterable
            ? (json[r'unsupportedOperations'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
      );
    }
    return null;
  }

  static List<ProfileReadinessResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProfileReadinessResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProfileReadinessResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProfileReadinessResponse> mapFromJson(dynamic json) {
    final map = <String, ProfileReadinessResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProfileReadinessResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProfileReadinessResponse-objects as value to a dart map
  static Map<String, List<ProfileReadinessResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProfileReadinessResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProfileReadinessResponse.listFromJson(
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
