//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderRef {
  /// Returns a new [ProviderRef] instance.
  ProviderRef({
    this.etag,
    this.externalId,
    this.externalUrl,
    this.lastSyncedAt,
    this.provider,
    this.version,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? etag;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? externalId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? externalUrl;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? lastSyncedAt;

  ProviderRefProviderEnum? provider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? version;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderRef &&
          other.etag == etag &&
          other.externalId == externalId &&
          other.externalUrl == externalUrl &&
          other.lastSyncedAt == lastSyncedAt &&
          other.provider == provider &&
          other.version == version;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (etag == null ? 0 : etag!.hashCode) +
      (externalId == null ? 0 : externalId!.hashCode) +
      (externalUrl == null ? 0 : externalUrl!.hashCode) +
      (lastSyncedAt == null ? 0 : lastSyncedAt!.hashCode) +
      (provider == null ? 0 : provider!.hashCode) +
      (version == null ? 0 : version!.hashCode);

  @override
  String toString() =>
      'ProviderRef[etag=$etag, externalId=$externalId, externalUrl=$externalUrl, lastSyncedAt=$lastSyncedAt, provider=$provider, version=$version]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.etag != null) {
      json[r'etag'] = this.etag;
    } else {
      json[r'etag'] = null;
    }
    if (this.externalId != null) {
      json[r'externalId'] = this.externalId;
    } else {
      json[r'externalId'] = null;
    }
    if (this.externalUrl != null) {
      json[r'externalUrl'] = this.externalUrl;
    } else {
      json[r'externalUrl'] = null;
    }
    if (this.lastSyncedAt != null) {
      json[r'lastSyncedAt'] = this.lastSyncedAt!.toUtc().toIso8601String();
    } else {
      json[r'lastSyncedAt'] = null;
    }
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
    }
    if (this.version != null) {
      json[r'version'] = this.version;
    } else {
      json[r'version'] = null;
    }
    return json;
  }

  /// Returns a new [ProviderRef] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderRef? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderRef[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderRef[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderRef(
        etag: mapValueOfType<String>(json, r'etag'),
        externalId: mapValueOfType<String>(json, r'externalId'),
        externalUrl: mapValueOfType<String>(json, r'externalUrl'),
        lastSyncedAt: mapDateTime(json, r'lastSyncedAt', r''),
        provider: ProviderRefProviderEnum.fromJson(json[r'provider']),
        version: mapValueOfType<String>(json, r'version'),
      );
    }
    return null;
  }

  static List<ProviderRef> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderRef>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderRef.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderRef> mapFromJson(dynamic json) {
    final map = <String, ProviderRef>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderRef.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderRef-objects as value to a dart map
  static Map<String, List<ProviderRef>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderRef>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderRef.listFromJson(
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

class ProviderRefProviderEnum {
  /// Instantiate a new enum with the provided [value].
  const ProviderRefProviderEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const vikunja = ProviderRefProviderEnum._(r'vikunja');
  static const openproject = ProviderRefProviderEnum._(r'openproject');
  static const nextcloudDeck = ProviderRefProviderEnum._(r'nextcloud-deck');
  static const placeholderBoards =
      ProviderRefProviderEnum._(r'placeholder-boards');
  static const inMemory = ProviderRefProviderEnum._(r'in-memory');
  static const unknown = ProviderRefProviderEnum._(r'unknown');

  /// List of all possible values in this [enum][ProviderRefProviderEnum].
  static const values = <ProviderRefProviderEnum>[
    vikunja,
    openproject,
    nextcloudDeck,
    placeholderBoards,
    inMemory,
    unknown,
  ];

  static ProviderRefProviderEnum? fromJson(dynamic value) =>
      ProviderRefProviderEnumTypeTransformer().decode(value);

  static List<ProviderRefProviderEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderRefProviderEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderRefProviderEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ProviderRefProviderEnum] to String,
/// and [decode] dynamic data back to [ProviderRefProviderEnum].
class ProviderRefProviderEnumTypeTransformer {
  factory ProviderRefProviderEnumTypeTransformer() =>
      _instance ??= const ProviderRefProviderEnumTypeTransformer._();

  const ProviderRefProviderEnumTypeTransformer._();

  String encode(ProviderRefProviderEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ProviderRefProviderEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ProviderRefProviderEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'vikunja':
          return ProviderRefProviderEnum.vikunja;
        case r'openproject':
          return ProviderRefProviderEnum.openproject;
        case r'nextcloud-deck':
          return ProviderRefProviderEnum.nextcloudDeck;
        case r'placeholder-boards':
          return ProviderRefProviderEnum.placeholderBoards;
        case r'in-memory':
          return ProviderRefProviderEnum.inMemory;
        case r'unknown':
          return ProviderRefProviderEnum.unknown;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ProviderRefProviderEnumTypeTransformer] instance.
  static ProviderRefProviderEnumTypeTransformer? _instance;
}
