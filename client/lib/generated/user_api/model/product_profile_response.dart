//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProductProfileResponse {
  /// Returns a new [ProductProfileResponse] instance.
  ProductProfileResponse({
    this.accessibilityPreferences = const {},
    this.avatar,
    this.displayName,
    this.email,
    this.emailVerified,
    this.locale,
    this.moduleSyncStatus,
    this.profileVisibility,
    this.timezone,
    this.userId,
    this.username,
  });

  /// Frontend accessibility preferences owned by the product profile facade.
  Map<String, String> accessibilityPreferences;

  /// Backend-managed avatar reference or upstream picture claim.
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

  /// Email address from the identity authority when available.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? email;

  /// Whether the identity source reports the email address as verified.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? emailVerified;

  /// Preferred locale.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? locale;

  /// Product profile sync status by module.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ModuleSyncStatusResponse? moduleSyncStatus;

  /// Profile visibility policy for product surfaces.
  ProductProfileResponseProfileVisibilityEnum? profileVisibility;

  /// Preferred timezone.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? timezone;

  /// Stable immutable Weave account identifier derived from issuer plus subject, never from email.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? userId;

  /// Stable login/workspace handle.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? username;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProductProfileResponse &&
          _deepEquality.equals(
              other.accessibilityPreferences, accessibilityPreferences) &&
          other.avatar == avatar &&
          other.displayName == displayName &&
          other.email == email &&
          other.emailVerified == emailVerified &&
          other.locale == locale &&
          other.moduleSyncStatus == moduleSyncStatus &&
          other.profileVisibility == profileVisibility &&
          other.timezone == timezone &&
          other.userId == userId &&
          other.username == username;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessibilityPreferences.hashCode) +
      (avatar == null ? 0 : avatar!.hashCode) +
      (displayName == null ? 0 : displayName!.hashCode) +
      (email == null ? 0 : email!.hashCode) +
      (emailVerified == null ? 0 : emailVerified!.hashCode) +
      (locale == null ? 0 : locale!.hashCode) +
      (moduleSyncStatus == null ? 0 : moduleSyncStatus!.hashCode) +
      (profileVisibility == null ? 0 : profileVisibility!.hashCode) +
      (timezone == null ? 0 : timezone!.hashCode) +
      (userId == null ? 0 : userId!.hashCode) +
      (username == null ? 0 : username!.hashCode);

  @override
  String toString() =>
      'ProductProfileResponse[accessibilityPreferences=$accessibilityPreferences, avatar=$avatar, displayName=$displayName, email=$email, emailVerified=$emailVerified, locale=$locale, moduleSyncStatus=$moduleSyncStatus, profileVisibility=$profileVisibility, timezone=$timezone, userId=$userId, username=$username]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'accessibilityPreferences'] = this.accessibilityPreferences;
    if (this.avatar != null) {
      json[r'avatar'] = this.avatar;
    } else {
      json[r'avatar'] = null;
    }
    if (this.displayName != null) {
      json[r'displayName'] = this.displayName;
    } else {
      json[r'displayName'] = null;
    }
    if (this.email != null) {
      json[r'email'] = this.email;
    } else {
      json[r'email'] = null;
    }
    if (this.emailVerified != null) {
      json[r'emailVerified'] = this.emailVerified;
    } else {
      json[r'emailVerified'] = null;
    }
    if (this.locale != null) {
      json[r'locale'] = this.locale;
    } else {
      json[r'locale'] = null;
    }
    if (this.moduleSyncStatus != null) {
      json[r'moduleSyncStatus'] = this.moduleSyncStatus;
    } else {
      json[r'moduleSyncStatus'] = null;
    }
    if (this.profileVisibility != null) {
      json[r'profileVisibility'] = this.profileVisibility;
    } else {
      json[r'profileVisibility'] = null;
    }
    if (this.timezone != null) {
      json[r'timezone'] = this.timezone;
    } else {
      json[r'timezone'] = null;
    }
    if (this.userId != null) {
      json[r'userId'] = this.userId;
    } else {
      json[r'userId'] = null;
    }
    if (this.username != null) {
      json[r'username'] = this.username;
    } else {
      json[r'username'] = null;
    }
    return json;
  }

  /// Returns a new [ProductProfileResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProductProfileResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProductProfileResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProductProfileResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProductProfileResponse(
        accessibilityPreferences:
            mapCastOfType<String, String>(json, r'accessibilityPreferences') ??
                const {},
        avatar: mapValueOfType<String>(json, r'avatar'),
        displayName: mapValueOfType<String>(json, r'displayName'),
        email: mapValueOfType<String>(json, r'email'),
        emailVerified: mapValueOfType<bool>(json, r'emailVerified'),
        locale: mapValueOfType<String>(json, r'locale'),
        moduleSyncStatus:
            ModuleSyncStatusResponse.fromJson(json[r'moduleSyncStatus']),
        profileVisibility: ProductProfileResponseProfileVisibilityEnum.fromJson(
            json[r'profileVisibility']),
        timezone: mapValueOfType<String>(json, r'timezone'),
        userId: mapValueOfType<String>(json, r'userId'),
        username: mapValueOfType<String>(json, r'username'),
      );
    }
    return null;
  }

  static List<ProductProfileResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProductProfileResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProductProfileResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProductProfileResponse> mapFromJson(dynamic json) {
    final map = <String, ProductProfileResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProductProfileResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProductProfileResponse-objects as value to a dart map
  static Map<String, List<ProductProfileResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProductProfileResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProductProfileResponse.listFromJson(
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
class ProductProfileResponseProfileVisibilityEnum {
  /// Instantiate a new enum with the provided [value].
  const ProductProfileResponseProfileVisibilityEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const private =
      ProductProfileResponseProfileVisibilityEnum._(r'private');
  static const workspace =
      ProductProfileResponseProfileVisibilityEnum._(r'workspace');
  static const public =
      ProductProfileResponseProfileVisibilityEnum._(r'public');

  /// List of all possible values in this [enum][ProductProfileResponseProfileVisibilityEnum].
  static const values = <ProductProfileResponseProfileVisibilityEnum>[
    private,
    workspace,
    public,
  ];

  static ProductProfileResponseProfileVisibilityEnum? fromJson(dynamic value) =>
      ProductProfileResponseProfileVisibilityEnumTypeTransformer()
          .decode(value);

  static List<ProductProfileResponseProfileVisibilityEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProductProfileResponseProfileVisibilityEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProductProfileResponseProfileVisibilityEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProductProfileResponseProfileVisibilityEnum] to String,
/// and [decode] dynamic data back to [ProductProfileResponseProfileVisibilityEnum].
class ProductProfileResponseProfileVisibilityEnumTypeTransformer {
  factory ProductProfileResponseProfileVisibilityEnumTypeTransformer() =>
      _instance ??=
          const ProductProfileResponseProfileVisibilityEnumTypeTransformer._();

  const ProductProfileResponseProfileVisibilityEnumTypeTransformer._();

  String encode(ProductProfileResponseProfileVisibilityEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ProductProfileResponseProfileVisibilityEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProductProfileResponseProfileVisibilityEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'private':
          return ProductProfileResponseProfileVisibilityEnum.private;
        case r'workspace':
          return ProductProfileResponseProfileVisibilityEnum.workspace;
        case r'public':
          return ProductProfileResponseProfileVisibilityEnum.public;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProductProfileResponseProfileVisibilityEnumTypeTransformer] instance.
  static ProductProfileResponseProfileVisibilityEnumTypeTransformer? _instance;
}
