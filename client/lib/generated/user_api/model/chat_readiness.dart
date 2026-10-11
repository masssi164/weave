//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ChatReadiness {
  /// Returns a new [ChatReadiness] instance.
  ChatReadiness({
    this.checkedAt,
    this.contractVersion,
    this.defaultHistoryPolicy,
    this.domain,
    this.downstreamDiagnosticsExposedToMember,
    this.failClosed,
    this.memberClientMayConfigureProvider,
    this.memberImpact,
    this.memberState,
    this.migrationDryRunRequired,
    this.providerMapping,
    this.supportSafe,
    this.supportSafeDiagnostics = const {},
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? checkedAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? contractVersion;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ChatHistoryPolicy? defaultHistoryPolicy;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? domain;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? downstreamDiagnosticsExposedToMember;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? failClosed;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? memberClientMayConfigureProvider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? memberImpact;

  /// Stable product-level Chat state returned to member clients.
  ChatReadinessMemberStateEnum? memberState;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? migrationDryRunRequired;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  ChatProviderMappingRecord? providerMapping;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  Map<String, Object> supportSafeDiagnostics;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ChatReadiness &&
          other.checkedAt == checkedAt &&
          other.contractVersion == contractVersion &&
          other.defaultHistoryPolicy == defaultHistoryPolicy &&
          other.domain == domain &&
          other.downstreamDiagnosticsExposedToMember ==
              downstreamDiagnosticsExposedToMember &&
          other.failClosed == failClosed &&
          other.memberClientMayConfigureProvider ==
              memberClientMayConfigureProvider &&
          other.memberImpact == memberImpact &&
          other.memberState == memberState &&
          other.migrationDryRunRequired == migrationDryRunRequired &&
          other.providerMapping == providerMapping &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(
              other.supportSafeDiagnostics, supportSafeDiagnostics);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (checkedAt == null ? 0 : checkedAt!.hashCode) +
      (contractVersion == null ? 0 : contractVersion!.hashCode) +
      (defaultHistoryPolicy == null ? 0 : defaultHistoryPolicy!.hashCode) +
      (domain == null ? 0 : domain!.hashCode) +
      (downstreamDiagnosticsExposedToMember == null
          ? 0
          : downstreamDiagnosticsExposedToMember!.hashCode) +
      (failClosed == null ? 0 : failClosed!.hashCode) +
      (memberClientMayConfigureProvider == null
          ? 0
          : memberClientMayConfigureProvider!.hashCode) +
      (memberImpact == null ? 0 : memberImpact!.hashCode) +
      (memberState == null ? 0 : memberState!.hashCode) +
      (migrationDryRunRequired == null
          ? 0
          : migrationDryRunRequired!.hashCode) +
      (providerMapping == null ? 0 : providerMapping!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (supportSafeDiagnostics.hashCode);

  @override
  String toString() =>
      'ChatReadiness[checkedAt=$checkedAt, contractVersion=$contractVersion, defaultHistoryPolicy=$defaultHistoryPolicy, domain=$domain, downstreamDiagnosticsExposedToMember=$downstreamDiagnosticsExposedToMember, failClosed=$failClosed, memberClientMayConfigureProvider=$memberClientMayConfigureProvider, memberImpact=$memberImpact, memberState=$memberState, migrationDryRunRequired=$migrationDryRunRequired, providerMapping=$providerMapping, supportSafe=$supportSafe, supportSafeDiagnostics=$supportSafeDiagnostics]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.checkedAt != null) {
      json[r'checkedAt'] = this.checkedAt!.toUtc().toIso8601String();
    } else {
      json[r'checkedAt'] = null;
    }
    if (this.contractVersion != null) {
      json[r'contractVersion'] = this.contractVersion;
    } else {
      json[r'contractVersion'] = null;
    }
    if (this.defaultHistoryPolicy != null) {
      json[r'defaultHistoryPolicy'] = this.defaultHistoryPolicy;
    } else {
      json[r'defaultHistoryPolicy'] = null;
    }
    if (this.domain != null) {
      json[r'domain'] = this.domain;
    } else {
      json[r'domain'] = null;
    }
    if (this.downstreamDiagnosticsExposedToMember != null) {
      json[r'downstreamDiagnosticsExposedToMember'] =
          this.downstreamDiagnosticsExposedToMember;
    } else {
      json[r'downstreamDiagnosticsExposedToMember'] = null;
    }
    if (this.failClosed != null) {
      json[r'failClosed'] = this.failClosed;
    } else {
      json[r'failClosed'] = null;
    }
    if (this.memberClientMayConfigureProvider != null) {
      json[r'memberClientMayConfigureProvider'] =
          this.memberClientMayConfigureProvider;
    } else {
      json[r'memberClientMayConfigureProvider'] = null;
    }
    if (this.memberImpact != null) {
      json[r'memberImpact'] = this.memberImpact;
    } else {
      json[r'memberImpact'] = null;
    }
    if (this.memberState != null) {
      json[r'memberState'] = this.memberState;
    } else {
      json[r'memberState'] = null;
    }
    if (this.migrationDryRunRequired != null) {
      json[r'migrationDryRunRequired'] = this.migrationDryRunRequired;
    } else {
      json[r'migrationDryRunRequired'] = null;
    }
    if (this.providerMapping != null) {
      json[r'providerMapping'] = this.providerMapping;
    } else {
      json[r'providerMapping'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    json[r'supportSafeDiagnostics'] = this.supportSafeDiagnostics;
    return json;
  }

  /// Returns a new [ChatReadiness] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ChatReadiness? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ChatReadiness[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ChatReadiness[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ChatReadiness(
        checkedAt: mapDateTime(json, r'checkedAt', r''),
        contractVersion: mapValueOfType<String>(json, r'contractVersion'),
        defaultHistoryPolicy:
            ChatHistoryPolicy.fromJson(json[r'defaultHistoryPolicy']),
        domain: mapValueOfType<String>(json, r'domain'),
        downstreamDiagnosticsExposedToMember:
            mapValueOfType<bool>(json, r'downstreamDiagnosticsExposedToMember'),
        failClosed: mapValueOfType<bool>(json, r'failClosed'),
        memberClientMayConfigureProvider:
            mapValueOfType<bool>(json, r'memberClientMayConfigureProvider'),
        memberImpact: mapValueOfType<String>(json, r'memberImpact'),
        memberState:
            ChatReadinessMemberStateEnum.fromJson(json[r'memberState']),
        migrationDryRunRequired:
            mapValueOfType<bool>(json, r'migrationDryRunRequired'),
        providerMapping:
            ChatProviderMappingRecord.fromJson(json[r'providerMapping']),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        supportSafeDiagnostics:
            mapCastOfType<String, Object>(json, r'supportSafeDiagnostics') ??
                const {},
      );
    }
    return null;
  }

  static List<ChatReadiness> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ChatReadiness>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ChatReadiness.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ChatReadiness> mapFromJson(dynamic json) {
    final map = <String, ChatReadiness>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ChatReadiness.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ChatReadiness-objects as value to a dart map
  static Map<String, List<ChatReadiness>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ChatReadiness>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ChatReadiness.listFromJson(
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

/// Stable product-level Chat state returned to member clients.
class ChatReadinessMemberStateEnum {
  /// Instantiate a new enum with the provided [value].
  const ChatReadinessMemberStateEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const available = ChatReadinessMemberStateEnum._(r'available');
  static const degraded = ChatReadinessMemberStateEnum._(r'degraded');
  static const disabledByPolicy =
      ChatReadinessMemberStateEnum._(r'disabled_by_policy');
  static const unavailable = ChatReadinessMemberStateEnum._(r'unavailable');
  static const notConfigured =
      ChatReadinessMemberStateEnum._(r'not_configured');
  static const comingLater = ChatReadinessMemberStateEnum._(r'coming_later');

  /// List of all possible values in this [enum][ChatReadinessMemberStateEnum].
  static const values = <ChatReadinessMemberStateEnum>[
    available,
    degraded,
    disabledByPolicy,
    unavailable,
    notConfigured,
    comingLater,
  ];

  static ChatReadinessMemberStateEnum? fromJson(dynamic value) =>
      ChatReadinessMemberStateEnumTypeTransformer().decode(value);

  static List<ChatReadinessMemberStateEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ChatReadinessMemberStateEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ChatReadinessMemberStateEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ChatReadinessMemberStateEnum] to String,
/// and [decode] dynamic data back to [ChatReadinessMemberStateEnum].
class ChatReadinessMemberStateEnumTypeTransformer {
  factory ChatReadinessMemberStateEnumTypeTransformer() =>
      _instance ??= const ChatReadinessMemberStateEnumTypeTransformer._();

  const ChatReadinessMemberStateEnumTypeTransformer._();

  String encode(ChatReadinessMemberStateEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ChatReadinessMemberStateEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ChatReadinessMemberStateEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'available':
          return ChatReadinessMemberStateEnum.available;
        case r'degraded':
          return ChatReadinessMemberStateEnum.degraded;
        case r'disabled_by_policy':
          return ChatReadinessMemberStateEnum.disabledByPolicy;
        case r'unavailable':
          return ChatReadinessMemberStateEnum.unavailable;
        case r'not_configured':
          return ChatReadinessMemberStateEnum.notConfigured;
        case r'coming_later':
          return ChatReadinessMemberStateEnum.comingLater;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ChatReadinessMemberStateEnumTypeTransformer] instance.
  static ChatReadinessMemberStateEnumTypeTransformer? _instance;
}
