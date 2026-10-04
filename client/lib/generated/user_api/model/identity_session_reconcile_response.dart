//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class IdentitySessionReconcileResponse {
  /// Returns a new [IdentitySessionReconcileResponse] instance.
  IdentitySessionReconcileResponse({
    required this.reauthorizationRequired,
    required this.state,
  });

  /// Whether the caller must obtain a new OIDC authorization before domain bootstrap.
  bool reauthorizationRequired;

  /// Closed reconciliation state. Access changes require one new OIDC Authorization Code flow with PKCE.
  IdentitySessionReconcileResponseStateEnum state;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is IdentitySessionReconcileResponse &&
          other.reauthorizationRequired == reauthorizationRequired &&
          other.state == state;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (reauthorizationRequired.hashCode) + (state.hashCode);

  @override
  String toString() =>
      'IdentitySessionReconcileResponse[reauthorizationRequired=$reauthorizationRequired, state=$state]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'reauthorizationRequired'] = this.reauthorizationRequired;
    json[r'state'] = this.state;
    return json;
  }

  /// Returns a new [IdentitySessionReconcileResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static IdentitySessionReconcileResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "IdentitySessionReconcileResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "IdentitySessionReconcileResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return IdentitySessionReconcileResponse(
        reauthorizationRequired:
            mapValueOfType<bool>(json, r'reauthorizationRequired')!,
        state:
            IdentitySessionReconcileResponseStateEnum.fromJson(json[r'state'])!,
      );
    }
    return null;
  }

  static List<IdentitySessionReconcileResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <IdentitySessionReconcileResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = IdentitySessionReconcileResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, IdentitySessionReconcileResponse> mapFromJson(
      dynamic json) {
    final map = <String, IdentitySessionReconcileResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = IdentitySessionReconcileResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of IdentitySessionReconcileResponse-objects as value to a dart map
  static Map<String, List<IdentitySessionReconcileResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<IdentitySessionReconcileResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = IdentitySessionReconcileResponse.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'reauthorizationRequired',
    'state',
  };
}

/// Closed reconciliation state. Access changes require one new OIDC Authorization Code flow with PKCE.
class IdentitySessionReconcileResponseStateEnum {
  /// Instantiate a new enum with the provided [value].
  const IdentitySessionReconcileResponseStateEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const unchanged =
      IdentitySessionReconcileResponseStateEnum._(r'unchanged');
  static const accessUpdated =
      IdentitySessionReconcileResponseStateEnum._(r'access_updated');

  /// List of all possible values in this [enum][IdentitySessionReconcileResponseStateEnum].
  static const values = <IdentitySessionReconcileResponseStateEnum>[
    unchanged,
    accessUpdated,
  ];

  static IdentitySessionReconcileResponseStateEnum? fromJson(dynamic value) =>
      IdentitySessionReconcileResponseStateEnumTypeTransformer().decode(value);

  static List<IdentitySessionReconcileResponseStateEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <IdentitySessionReconcileResponseStateEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = IdentitySessionReconcileResponseStateEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [IdentitySessionReconcileResponseStateEnum] to String,
/// and [decode] dynamic data back to [IdentitySessionReconcileResponseStateEnum].
class IdentitySessionReconcileResponseStateEnumTypeTransformer {
  factory IdentitySessionReconcileResponseStateEnumTypeTransformer() =>
      _instance ??=
          const IdentitySessionReconcileResponseStateEnumTypeTransformer._();

  const IdentitySessionReconcileResponseStateEnumTypeTransformer._();

  String encode(IdentitySessionReconcileResponseStateEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a IdentitySessionReconcileResponseStateEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  IdentitySessionReconcileResponseStateEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'unchanged':
          return IdentitySessionReconcileResponseStateEnum.unchanged;
        case r'access_updated':
          return IdentitySessionReconcileResponseStateEnum.accessUpdated;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [IdentitySessionReconcileResponseStateEnumTypeTransformer] instance.
  static IdentitySessionReconcileResponseStateEnumTypeTransformer? _instance;
}
