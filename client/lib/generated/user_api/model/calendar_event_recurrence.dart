//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarEventRecurrence {
  /// Returns a new [CalendarEventRecurrence] instance.
  CalendarEventRecurrence({
    this.additionalDates = const [],
    this.byDay = const [],
    this.byMonth = const [],
    this.byMonthDay = const [],
    this.bySetPos = const [],
    this.count,
    this.excludedDates = const [],
    required this.frequency,
    required this.interval,
    this.until,
    this.weekStart,
  });

  List<CalendarTimeValue> additionalDates;

  List<String> byDay;

  List<int> byMonth;

  List<int> byMonthDay;

  List<int> bySetPos;

  /// Minimum value: 1
  /// Maximum value: 100000
  int? count;

  List<CalendarTimeValue> excludedDates;

  CalendarEventRecurrenceFrequencyEnum frequency;

  /// Minimum value: 1
  /// Maximum value: 1000
  int interval;

  /// Exclusive with count. DATE/FLOATING match start kind; UTC and ZONED use a UTC instant.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  CalendarTimeValue? until;

  String? weekStart;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarEventRecurrence &&
          _deepEquality.equals(other.additionalDates, additionalDates) &&
          _deepEquality.equals(other.byDay, byDay) &&
          _deepEquality.equals(other.byMonth, byMonth) &&
          _deepEquality.equals(other.byMonthDay, byMonthDay) &&
          _deepEquality.equals(other.bySetPos, bySetPos) &&
          other.count == count &&
          _deepEquality.equals(other.excludedDates, excludedDates) &&
          other.frequency == frequency &&
          other.interval == interval &&
          other.until == until &&
          other.weekStart == weekStart;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (additionalDates.hashCode) +
      (byDay.hashCode) +
      (byMonth.hashCode) +
      (byMonthDay.hashCode) +
      (bySetPos.hashCode) +
      (count == null ? 0 : count!.hashCode) +
      (excludedDates.hashCode) +
      (frequency.hashCode) +
      (interval.hashCode) +
      (until == null ? 0 : until!.hashCode) +
      (weekStart == null ? 0 : weekStart!.hashCode);

  @override
  String toString() =>
      'CalendarEventRecurrence[additionalDates=$additionalDates, byDay=$byDay, byMonth=$byMonth, byMonthDay=$byMonthDay, bySetPos=$bySetPos, count=$count, excludedDates=$excludedDates, frequency=$frequency, interval=$interval, until=$until, weekStart=$weekStart]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'additionalDates'] = this.additionalDates;
    json[r'byDay'] = this.byDay;
    json[r'byMonth'] = this.byMonth;
    json[r'byMonthDay'] = this.byMonthDay;
    json[r'bySetPos'] = this.bySetPos;
    if (this.count != null) {
      json[r'count'] = this.count;
    } else {
      json[r'count'] = null;
    }
    json[r'excludedDates'] = this.excludedDates;
    json[r'frequency'] = this.frequency;
    json[r'interval'] = this.interval;
    if (this.until != null) {
      json[r'until'] = this.until;
    } else {
      json[r'until'] = null;
    }
    if (this.weekStart != null) {
      json[r'weekStart'] = this.weekStart;
    } else {
      json[r'weekStart'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarEventRecurrence] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarEventRecurrence? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarEventRecurrence[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarEventRecurrence[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarEventRecurrence(
        additionalDates:
            CalendarTimeValue.listFromJson(json[r'additionalDates']),
        byDay: json[r'byDay'] is Iterable
            ? (json[r'byDay'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        byMonth: json[r'byMonth'] is Iterable
            ? (json[r'byMonth'] as Iterable).cast<int>().toList(growable: false)
            : const [],
        byMonthDay: json[r'byMonthDay'] is Iterable
            ? (json[r'byMonthDay'] as Iterable)
                .cast<int>()
                .toList(growable: false)
            : const [],
        bySetPos: json[r'bySetPos'] is Iterable
            ? (json[r'bySetPos'] as Iterable)
                .cast<int>()
                .toList(growable: false)
            : const [],
        count: mapValueOfType<int>(json, r'count'),
        excludedDates: CalendarTimeValue.listFromJson(json[r'excludedDates']),
        frequency:
            CalendarEventRecurrenceFrequencyEnum.fromJson(json[r'frequency'])!,
        interval: mapValueOfType<int>(json, r'interval')!,
        until: CalendarTimeValue.fromJson(json[r'until']),
        weekStart: mapValueOfType<String>(json, r'weekStart'),
      );
    }
    return null;
  }

  static List<CalendarEventRecurrence> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarEventRecurrence>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarEventRecurrence.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarEventRecurrence> mapFromJson(dynamic json) {
    final map = <String, CalendarEventRecurrence>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarEventRecurrence.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarEventRecurrence-objects as value to a dart map
  static Map<String, List<CalendarEventRecurrence>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarEventRecurrence>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarEventRecurrence.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'additionalDates',
    'byDay',
    'byMonth',
    'byMonthDay',
    'bySetPos',
    'excludedDates',
    'frequency',
    'interval',
  };
}

class CalendarEventRecurrenceFrequencyEnum {
  /// Instantiate a new enum with the provided [value].
  const CalendarEventRecurrenceFrequencyEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const DAILY = CalendarEventRecurrenceFrequencyEnum._(r'DAILY');
  static const WEEKLY = CalendarEventRecurrenceFrequencyEnum._(r'WEEKLY');
  static const MONTHLY = CalendarEventRecurrenceFrequencyEnum._(r'MONTHLY');
  static const YEARLY = CalendarEventRecurrenceFrequencyEnum._(r'YEARLY');

  /// List of all possible values in this [enum][CalendarEventRecurrenceFrequencyEnum].
  static const values = <CalendarEventRecurrenceFrequencyEnum>[
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
  ];

  static CalendarEventRecurrenceFrequencyEnum? fromJson(dynamic value) =>
      CalendarEventRecurrenceFrequencyEnumTypeTransformer().decode(value);

  static List<CalendarEventRecurrenceFrequencyEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarEventRecurrenceFrequencyEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarEventRecurrenceFrequencyEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [CalendarEventRecurrenceFrequencyEnum] to String,
/// and [decode] dynamic data back to [CalendarEventRecurrenceFrequencyEnum].
class CalendarEventRecurrenceFrequencyEnumTypeTransformer {
  factory CalendarEventRecurrenceFrequencyEnumTypeTransformer() => _instance ??=
      const CalendarEventRecurrenceFrequencyEnumTypeTransformer._();

  const CalendarEventRecurrenceFrequencyEnumTypeTransformer._();

  String encode(CalendarEventRecurrenceFrequencyEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a CalendarEventRecurrenceFrequencyEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  CalendarEventRecurrenceFrequencyEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'DAILY':
          return CalendarEventRecurrenceFrequencyEnum.DAILY;
        case r'WEEKLY':
          return CalendarEventRecurrenceFrequencyEnum.WEEKLY;
        case r'MONTHLY':
          return CalendarEventRecurrenceFrequencyEnum.MONTHLY;
        case r'YEARLY':
          return CalendarEventRecurrenceFrequencyEnum.YEARLY;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [CalendarEventRecurrenceFrequencyEnumTypeTransformer] instance.
  static CalendarEventRecurrenceFrequencyEnumTypeTransformer? _instance;
}
