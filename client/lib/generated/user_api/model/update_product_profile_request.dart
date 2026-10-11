//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class UpdateProductProfileRequest {
  /// Returns a new [UpdateProductProfileRequest] instance.
  UpdateProductProfileRequest({
    this.accessibilityPreferences,
    this.avatar,
    this.displayName,
    this.locale,
    this.profileVisibility,
    this.timezone,
  });

  /// Frontend accessibility preferences. Keep keys and values support-safe and non-secret.
  Map<String, String>? accessibilityPreferences;

  /// Backend-managed avatar reference.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? avatar;

  /// User-visible product display name.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? displayName;

  /// Preferred BCP 47 locale tag.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? locale;

  /// Profile visibility policy for product surfaces.
  UpdateProductProfileRequestProfileVisibilityEnum? profileVisibility;

  /// Preferred IANA timezone.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? timezone;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is UpdateProductProfileRequest &&
          _deepEquality.equals(
              other.accessibilityPreferences, accessibilityPreferences) &&
          other.avatar == avatar &&
          other.displayName == displayName &&
          other.locale == locale &&
          other.profileVisibility == profileVisibility &&
          other.timezone == timezone;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessibilityPreferences.hashCode) +
      (avatar == null ? 0 : avatar!.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (locale == null ? 0 : locale!.hashCode) +
      (profileVisibility == null ? 0 : profileVisibility!.hashCode) +
      (timezone == null ? 0 : timezone!.hashCode);

  @override
  String toString() =>
      'UpdateProductProfileRequest[accessibilityPreferences=$accessibilityPreferences, avatar=$avatar, displayName=$displayName, locale=$locale, profileVisibility=$profileVisibility, timezone=$timezone]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accessibilityPreferences != null) {
      json[r'accessibilityPreferences'] = this.accessibilityPreferences;
    }
    if (this.avatar != null) {
      json[r'avatar'] = this.avatar;
    }
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    }
    if (this.locale != null) {
      json[r'locale'] = this.locale;
    }
    if (this.profileVisibility != null) {
      json[r'profileVisibility'] = this.profileVisibility;
    }
    if (this.timezone != null) {
      json[r'timezone'] = this.timezone;
    }
    return json;
  }

  /// Returns a new [UpdateProductProfileRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static UpdateProductProfileRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "UpdateProductProfileRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "UpdateProductProfileRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return UpdateProductProfileRequest(
        accessibilityPreferences:
            mapCastOfType<String, String>(json, r'accessibilityPreferences'),
        avatar: mapValueOfType<String>(json, r'avatar'),
        displayName: mapValueOfType<String>(json, r'displayName'),
        locale: mapValueOfType<String>(json, r'locale'),
        profileVisibility:
            UpdateProductProfileRequestProfileVisibilityEnum.fromJson(
                json[r'profileVisibility']),
        timezone: mapValueOfType<String>(json, r'timezone'),
      );
    }
    return null;
  }

  static List<UpdateProductProfileRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <UpdateProductProfileRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = UpdateProductProfileRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, UpdateProductProfileRequest> mapFromJson(dynamic json) {
    final map = <String, UpdateProductProfileRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = UpdateProductProfileRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of UpdateProductProfileRequest-objects as value to a dart map
  static Map<String, List<UpdateProductProfileRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<UpdateProductProfileRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = UpdateProductProfileRequest.listFromJson(
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

/// Profile visibility policy for product surfaces.
class UpdateProductProfileRequestProfileVisibilityEnum {
  /// Instantiate a new enum with the provided [value].
  const UpdateProductProfileRequestProfileVisibilityEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const private =
      UpdateProductProfileRequestProfileVisibilityEnum._(r'private');
  static const workspace =
      UpdateProductProfileRequestProfileVisibilityEnum._(r'workspace');
  static const public =
      UpdateProductProfileRequestProfileVisibilityEnum._(r'public');

  /// List of all possible values in this [enum][UpdateProductProfileRequestProfileVisibilityEnum].
  static const values = <UpdateProductProfileRequestProfileVisibilityEnum>[
    private,
    workspace,
    public,
  ];

  static UpdateProductProfileRequestProfileVisibilityEnum? fromJson(
          dynamic value) =>
      UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer()
          .decode(value);

  static List<UpdateProductProfileRequestProfileVisibilityEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <UpdateProductProfileRequestProfileVisibilityEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value =
            UpdateProductProfileRequestProfileVisibilityEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [UpdateProductProfileRequestProfileVisibilityEnum] to String,
/// and [decode] dynamic data back to [UpdateProductProfileRequestProfileVisibilityEnum].
class UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer {
  factory UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer() =>
      _instance ??=
          const UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer
              ._();

  const UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer._();

  String encode(UpdateProductProfileRequestProfileVisibilityEnum data) =>
      data.value;

  /// Decodes a [dynamic value][data] to a UpdateProductProfileRequestProfileVisibilityEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  UpdateProductProfileRequestProfileVisibilityEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'private':
          return UpdateProductProfileRequestProfileVisibilityEnum.private;
        case r'workspace':
          return UpdateProductProfileRequestProfileVisibilityEnum.workspace;
        case r'public':
          return UpdateProductProfileRequestProfileVisibilityEnum.public;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer] instance.
  static UpdateProductProfileRequestProfileVisibilityEnumTypeTransformer?
      _instance;
}
