//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ChatProviderMappingRecord {
  /// Returns a new [ChatProviderMappingRecord] instance.
  ChatProviderMappingRecord({
    this.category,
    this.configured,
    this.downstreamErrorsReturned,
    this.failClosed,
    this.lossyMappingWarnings = const [],
    this.readinessState,
    this.secretsReturned,
    this.selectedByAdmin,
    this.selectedProviderKey,
    this.selectionSource,
    this.supportSafe,
    this.supportSafeDiagnostics = const {},
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? category;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? configured;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? downstreamErrorsReturned;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? failClosed;

  List<String> lossyMappingWarnings;

  /// Stable product-level Chat state returned to member clients.
  ChatProviderMappingRecordReadinessStateEnum? readinessState;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? secretsReturned;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? selectedByAdmin;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? selectedProviderKey;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? selectionSource;

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
      other is ChatProviderMappingRecord &&
          other.category == category &&
          other.configured == configured &&
          other.downstreamErrorsReturned == downstreamErrorsReturned &&
          other.failClosed == failClosed &&
          _deepEquality.equals(
              other.lossyMappingWarnings, lossyMappingWarnings) &&
          other.readinessState == readinessState &&
          other.secretsReturned == secretsReturned &&
          other.selectedByAdmin == selectedByAdmin &&
          other.selectedProviderKey == selectedProviderKey &&
          other.selectionSource == selectionSource &&
          other.supportSafe == supportSafe &&
          _deepEquality.equals(
              other.supportSafeDiagnostics, supportSafeDiagnostics);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (category == null ? 0 : category!.hashCode) +
      (configured == null ? 0 : configured!.hashCode) +
      (downstreamErrorsReturned == null
          ? 0
          : downstreamErrorsReturned!.hashCode) +
      (failClosed == null ? 0 : failClosed!.hashCode) +
      (lossyMappingWarnings.hashCode) +
      (readinessState == null ? 0 : readinessState!.hashCode) +
      (secretsReturned == null ? 0 : secretsReturned!.hashCode) +
      (selectedByAdmin == null ? 0 : selectedByAdmin!.hashCode) +
      (selectedProviderKey == null ? 0 : selectedProviderKey!.hashCode) +
      (selectionSource == null ? 0 : selectionSource!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (supportSafeDiagnostics.hashCode);

  @override
  String toString() =>
      'ChatProviderMappingRecord[category=$category, configured=$configured, downstreamErrorsReturned=$downstreamErrorsReturned, failClosed=$failClosed, lossyMappingWarnings=$lossyMappingWarnings, readinessState=$readinessState, secretsReturned=$secretsReturned, selectedByAdmin=$selectedByAdmin, selectedProviderKey=$selectedProviderKey, selectionSource=$selectionSource, supportSafe=$supportSafe, supportSafeDiagnostics=$supportSafeDiagnostics]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.category != null) {
      json[r'category'] = this.category;
    } else {
      json[r'category'] = null;
    }
    if (this.configured != null) {
      json[r'configured'] = this.configured;
    } else {
      json[r'configured'] = null;
    }
    if (this.downstreamErrorsReturned != null) {
      json[r'downstreamErrorsReturned'] = this.downstreamErrorsReturned;
    } else {
      json[r'downstreamErrorsReturned'] = null;
    }
    if (this.failClosed != null) {
      json[r'failClosed'] = this.failClosed;
    } else {
      json[r'failClosed'] = null;
    }
    json[r'lossyMappingWarnings'] = this.lossyMappingWarnings;
    if (this.readinessState != null) {
      json[r'readinessState'] = this.readinessState;
    } else {
      json[r'readinessState'] = null;
    }
    if (this.secretsReturned != null) {
      json[r'secretsReturned'] = this.secretsReturned;
    } else {
      json[r'secretsReturned'] = null;
    }
    if (this.selectedByAdmin != null) {
      json[r'selectedByAdmin'] = this.selectedByAdmin;
    } else {
      json[r'selectedByAdmin'] = null;
    }
    if (this.selectedProviderKey != null) {
      json[r'selectedProviderKey'] = this.selectedProviderKey;
    } else {
      json[r'selectedProviderKey'] = null;
    }
    if (this.selectionSource != null) {
      json[r'selectionSource'] = this.selectionSource;
    } else {
      json[r'selectionSource'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    json[r'supportSafeDiagnostics'] = this.supportSafeDiagnostics;
    return json;
  }

  /// Returns a new [ChatProviderMappingRecord] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ChatProviderMappingRecord? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ChatProviderMappingRecord[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ChatProviderMappingRecord[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ChatProviderMappingRecord(
        category: mapValueOfType<String>(json, r'category'),
        configured: mapValueOfType<bool>(json, r'configured'),
        downstreamErrorsReturned:
            mapValueOfType<bool>(json, r'downstreamErrorsReturned'),
        failClosed: mapValueOfType<bool>(json, r'failClosed'),
        lossyMappingWarnings: json[r'lossyMappingWarnings'] is Iterable
            ? (json[r'lossyMappingWarnings'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        readinessState: ChatProviderMappingRecordReadinessStateEnum.fromJson(
            json[r'readinessState']),
        secretsReturned: mapValueOfType<bool>(json, r'secretsReturned'),
        selectedByAdmin: mapValueOfType<bool>(json, r'selectedByAdmin'),
        selectedProviderKey:
            mapValueOfType<String>(json, r'selectedProviderKey'),
        selectionSource: mapValueOfType<String>(json, r'selectionSource'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        supportSafeDiagnostics:
            mapCastOfType<String, Object>(json, r'supportSafeDiagnostics') ??
                const {},
      );
    }
    return null;
  }

  static List<ChatProviderMappingRecord> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ChatProviderMappingRecord>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ChatProviderMappingRecord.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ChatProviderMappingRecord> mapFromJson(dynamic json) {
    final map = <String, ChatProviderMappingRecord>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ChatProviderMappingRecord.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ChatProviderMappingRecord-objects as value to a dart map
  static Map<String, List<ChatProviderMappingRecord>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ChatProviderMappingRecord>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ChatProviderMappingRecord.listFromJson(
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
class ChatProviderMappingRecordReadinessStateEnum {
  /// Instantiate a new enum with the provided [value].
  const ChatProviderMappingRecordReadinessStateEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const available =
      ChatProviderMappingRecordReadinessStateEnum._(r'available');
  static const degraded =
      ChatProviderMappingRecordReadinessStateEnum._(r'degraded');
  static const disabledByPolicy =
      ChatProviderMappingRecordReadinessStateEnum._(r'disabled_by_policy');
  static const unavailable =
      ChatProviderMappingRecordReadinessStateEnum._(r'unavailable');
  static const notConfigured =
      ChatProviderMappingRecordReadinessStateEnum._(r'not_configured');
  static const comingLater =
      ChatProviderMappingRecordReadinessStateEnum._(r'coming_later');

  /// List of all possible values in this [enum][ChatProviderMappingRecordReadinessStateEnum].
  static const values = <ChatProviderMappingRecordReadinessStateEnum>[
    available,
    degraded,
    disabledByPolicy,
    unavailable,
    notConfigured,
    comingLater,
  ];

  static ChatProviderMappingRecordReadinessStateEnum? fromJson(dynamic value) =>
      ChatProviderMappingRecordReadinessStateEnumTypeTransformer()
          .decode(value);

  static List<ChatProviderMappingRecordReadinessStateEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ChatProviderMappingRecordReadinessStateEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ChatProviderMappingRecordReadinessStateEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [ChatProviderMappingRecordReadinessStateEnum] to String,
/// and [decode] dynamic data back to [ChatProviderMappingRecordReadinessStateEnum].
class ChatProviderMappingRecordReadinessStateEnumTypeTransformer {
  factory ChatProviderMappingRecordReadinessStateEnumTypeTransformer() =>
      _instance ??=
          const ChatProviderMappingRecordReadinessStateEnumTypeTransformer._();

  const ChatProviderMappingRecordReadinessStateEnumTypeTransformer._();

  String encode(ChatProviderMappingRecordReadinessStateEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a ChatProviderMappingRecordReadinessStateEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  ChatProviderMappingRecordReadinessStateEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'available':
          return ChatProviderMappingRecordReadinessStateEnum.available;
        case r'degraded':
          return ChatProviderMappingRecordReadinessStateEnum.degraded;
        case r'disabled_by_policy':
          return ChatProviderMappingRecordReadinessStateEnum.disabledByPolicy;
        case r'unavailable':
          return ChatProviderMappingRecordReadinessStateEnum.unavailable;
        case r'not_configured':
          return ChatProviderMappingRecordReadinessStateEnum.notConfigured;
        case r'coming_later':
          return ChatProviderMappingRecordReadinessStateEnum.comingLater;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [ChatProviderMappingRecordReadinessStateEnumTypeTransformer] instance.
  static ChatProviderMappingRecordReadinessStateEnumTypeTransformer? _instance;
}
