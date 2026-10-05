//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarTimeValue {
  /// Returns a new [CalendarTimeValue] instance.
  CalendarTimeValue({
    this.date,
    this.instant,
    required this.kind,
    this.localDateTime,
    this.timeZone,
  });

  DateTime? date;

  /// Second-precision UTC instant ending in Z.
  DateTime? instant;

  CalendarTimeValueKindEnum kind;

  String? localDateTime;

  /// IANA timezone; present only for ZONED.
  String? timeZone;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarTimeValue &&
          other.date == date &&
          other.instant == instant &&
          other.kind == kind &&
          other.localDateTime == localDateTime &&
          other.timeZone == timeZone;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (date == null ? 0 : date!.hashCode) +
      (instant == null ? 0 : instant!.hashCode) +
      (kind.hashCode) +
      (localDateTime == null ? 0 : localDateTime!.hashCode) +
      (timeZone == null ? 0 : timeZone!.hashCode);

  @override
  String toString() =>
      'CalendarTimeValue[date=$date, instant=$instant, kind=$kind, localDateTime=$localDateTime, timeZone=$timeZone]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.date != null) {
      json[r'date'] = _dateFormatter.format(this.date!.toUtc());
    } else {
      json[r'date'] = null;
    }
    if (this.instant != null) {
      json[r'instant'] =
          _isEpochMarker(r'/^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$/')
              ? this.instant!.millisecondsSinceEpoch
              : this.instant!.toUtc().toIso8601String();
    } else {
      json[r'instant'] = null;
    }
    json[r'kind'] = this.kind;
    if (this.localDateTime != null) {
      json[r'localDateTime'] = this.localDateTime;
    } else {
      json[r'localDateTime'] = null;
    }
    if (this.timeZone != null) {
      json[r'timeZone'] = this.timeZone;
    } else {
      json[r'timeZone'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarTimeValue] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarTimeValue? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarTimeValue[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarTimeValue[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarTimeValue(
        date: mapDateTime(json, r'date', r''),
        instant: mapDateTime(json, r'instant',
            r'/^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$/'),
        kind: CalendarTimeValueKindEnum.fromJson(json[r'kind'])!,
        localDateTime: mapValueOfType<String>(json, r'localDateTime'),
        timeZone: mapValueOfType<String>(json, r'timeZone'),
      );
    }
    return null;
  }

  static List<CalendarTimeValue> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarTimeValue>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarTimeValue.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarTimeValue> mapFromJson(dynamic json) {
    final map = <String, CalendarTimeValue>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarTimeValue.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarTimeValue-objects as value to a dart map
  static Map<String, List<CalendarTimeValue>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarTimeValue>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarTimeValue.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'kind',
  };
}

class CalendarTimeValueKindEnum {
  /// Instantiate a new enum with the provided [value].
  const CalendarTimeValueKindEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const DATE = CalendarTimeValueKindEnum._(r'DATE');
  static const FLOATING = CalendarTimeValueKindEnum._(r'FLOATING');
  static const UTC = CalendarTimeValueKindEnum._(r'UTC');
  static const ZONED = CalendarTimeValueKindEnum._(r'ZONED');

  /// List of all possible values in this [enum][CalendarTimeValueKindEnum].
  static const values = <CalendarTimeValueKindEnum>[
    DATE,
    FLOATING,
    UTC,
    ZONED,
  ];

  static CalendarTimeValueKindEnum? fromJson(dynamic value) =>
      CalendarTimeValueKindEnumTypeTransformer().decode(value);

  static List<CalendarTimeValueKindEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarTimeValueKindEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarTimeValueKindEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [CalendarTimeValueKindEnum] to String,
/// and [decode] dynamic data back to [CalendarTimeValueKindEnum].
class CalendarTimeValueKindEnumTypeTransformer {
  factory CalendarTimeValueKindEnumTypeTransformer() =>
      _instance ??= const CalendarTimeValueKindEnumTypeTransformer._();

  const CalendarTimeValueKindEnumTypeTransformer._();

  String encode(CalendarTimeValueKindEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a CalendarTimeValueKindEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  CalendarTimeValueKindEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'DATE':
          return CalendarTimeValueKindEnum.DATE;
        case r'FLOATING':
          return CalendarTimeValueKindEnum.FLOATING;
        case r'UTC':
          return CalendarTimeValueKindEnum.UTC;
        case r'ZONED':
          return CalendarTimeValueKindEnum.ZONED;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [CalendarTimeValueKindEnumTypeTransformer] instance.
  static CalendarTimeValueKindEnumTypeTransformer? _instance;
}
