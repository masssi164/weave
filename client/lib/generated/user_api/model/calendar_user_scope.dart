//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarUserScope {
  /// Returns a new [CalendarUserScope] instance.
  CalendarUserScope({
    this.channelId,
    required this.spaceId,
    this.teamId,
    required this.type,
  });

  String? channelId;

  String spaceId;

  String? teamId;

  CalendarUserScopeTypeEnum type;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarUserScope &&
          other.channelId == channelId &&
          other.spaceId == spaceId &&
          other.teamId == teamId &&
          other.type == type;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (channelId == null ? 0 : channelId!.hashCode) +
      (spaceId.hashCode) +
      (teamId == null ? 0 : teamId!.hashCode) +
      (type.hashCode);

  @override
  String toString() =>
      'CalendarUserScope[channelId=$channelId, spaceId=$spaceId, teamId=$teamId, type=$type]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.channelId != null) {
      json[r'channelId'] = this.channelId;
    } else {
      json[r'channelId'] = null;
    }
    json[r'spaceId'] = this.spaceId;
    if (this.teamId != null) {
      json[r'teamId'] = this.teamId;
    } else {
      json[r'teamId'] = null;
    }
    json[r'type'] = this.type;
    return json;
  }

  /// Returns a new [CalendarUserScope] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarUserScope? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarUserScope[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarUserScope[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarUserScope(
        channelId: mapValueOfType<String>(json, r'channelId'),
        spaceId: mapValueOfType<String>(json, r'spaceId')!,
        teamId: mapValueOfType<String>(json, r'teamId'),
        type: CalendarUserScopeTypeEnum.fromJson(json[r'type'])!,
      );
    }
    return null;
  }

  static List<CalendarUserScope> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserScope>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserScope.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarUserScope> mapFromJson(dynamic json) {
    final map = <String, CalendarUserScope>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarUserScope.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarUserScope-objects as value to a dart map
  static Map<String, List<CalendarUserScope>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarUserScope>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarUserScope.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'spaceId',
    'type',
  };
}

class CalendarUserScopeTypeEnum {
  /// Instantiate a new enum with the provided [value].
  const CalendarUserScopeTypeEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const WORKSPACE = CalendarUserScopeTypeEnum._(r'WORKSPACE');
  static const TEAM = CalendarUserScopeTypeEnum._(r'TEAM');
  static const CHANNEL = CalendarUserScopeTypeEnum._(r'CHANNEL');

  /// List of all possible values in this [enum][CalendarUserScopeTypeEnum].
  static const values = <CalendarUserScopeTypeEnum>[
    WORKSPACE,
    TEAM,
    CHANNEL,
  ];

  static CalendarUserScopeTypeEnum? fromJson(dynamic value) =>
      CalendarUserScopeTypeEnumTypeTransformer().decode(value);

  static List<CalendarUserScopeTypeEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarUserScopeTypeEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarUserScopeTypeEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [CalendarUserScopeTypeEnum] to String,
/// and [decode] dynamic data back to [CalendarUserScopeTypeEnum].
class CalendarUserScopeTypeEnumTypeTransformer {
  factory CalendarUserScopeTypeEnumTypeTransformer() =>
      _instance ??= const CalendarUserScopeTypeEnumTypeTransformer._();

  const CalendarUserScopeTypeEnumTypeTransformer._();

  String encode(CalendarUserScopeTypeEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a CalendarUserScopeTypeEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  CalendarUserScopeTypeEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'WORKSPACE':
          return CalendarUserScopeTypeEnum.WORKSPACE;
        case r'TEAM':
          return CalendarUserScopeTypeEnum.TEAM;
        case r'CHANNEL':
          return CalendarUserScopeTypeEnum.CHANNEL;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [CalendarUserScopeTypeEnumTypeTransformer] instance.
  static CalendarUserScopeTypeEnumTypeTransformer? _instance;
}
