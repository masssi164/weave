//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class CalendarScopeResponse {
  /// Returns a new [CalendarScopeResponse] instance.
  CalendarScopeResponse({
    this.accessModel,
    this.capabilities = const [],
    this.channelId,
    this.contextId,
    this.id,
    this.label,
    this.teamId,
    this.type,
    this.workspaceId,
  });

  /// Access model for this scope.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? accessModel;

  /// Granted user capabilities for this scope.
  List<String> capabilities;

  /// Channel identifier for channel scopes.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? channelId;

  /// Durable Weave Context/Space identifier backing this calendar scope.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? contextId;

  /// Stable scope identifier.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  /// Human-readable scope label.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? label;

  /// Team identifier for team/channel scopes.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? teamId;

  /// Scope type.
  CalendarScopeResponseTypeEnum? type;

  /// Workspace identifier that owns this scope.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? workspaceId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarScopeResponse &&
          other.accessModel == accessModel &&
          _deepEquality.equals(other.capabilities, capabilities) &&
          other.channelId == channelId &&
          other.contextId == contextId &&
          other.id == id &&
          other.label == label &&
          other.teamId == teamId &&
          other.type == type &&
          other.workspaceId == workspaceId;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (accessModel == null ? 0 : accessModel!.hashCode) +
      (capabilities.hashCode) +
      (channelId == null ? 0 : channelId!.hashCode) +
      (contextId == null ? 0 : contextId!.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (label == null ? 0 : label!.hashCode) +
      (teamId == null ? 0 : teamId!.hashCode) +
      (type == null ? 0 : type!.hashCode) +
      (workspaceId == null ? 0 : workspaceId!.hashCode);

  @override
  String toString() =>
      'CalendarScopeResponse[accessModel=$accessModel, capabilities=$capabilities, channelId=$channelId, contextId=$contextId, id=$id, label=$label, teamId=$teamId, type=$type, workspaceId=$workspaceId]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.accessModel != null) {
      json[r'accessModel'] = this.accessModel;
    } else {
      json[r'accessModel'] = null;
    }
    json[r'capabilities'] = this.capabilities;
    if (this.channelId != null) {
      json[r'channelId'] = this.channelId;
    } else {
      json[r'channelId'] = null;
    }
    if (this.contextId != null) {
      json[r'contextId'] = this.contextId;
    } else {
      json[r'contextId'] = null;
    }
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    if (this.label != null) {
      json[r'label'] = this.label;
    } else {
      json[r'label'] = null;
    }
    if (this.teamId != null) {
      json[r'teamId'] = this.teamId;
    } else {
      json[r'teamId'] = null;
    }
    if (this.type != null) {
      json[r'type'] = this.type;
    } else {
      json[r'type'] = null;
    }
    if (this.workspaceId != null) {
      json[r'workspaceId'] = this.workspaceId;
    } else {
      json[r'workspaceId'] = null;
    }
    return json;
  }

  /// Returns a new [CalendarScopeResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static CalendarScopeResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "CalendarScopeResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "CalendarScopeResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return CalendarScopeResponse(
        accessModel: mapValueOfType<String>(json, r'accessModel'),
        capabilities: json[r'capabilities'] is Iterable
            ? (json[r'capabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        channelId: mapValueOfType<String>(json, r'channelId'),
        contextId: mapValueOfType<String>(json, r'contextId'),
        id: mapValueOfType<String>(json, r'id'),
        label: mapValueOfType<String>(json, r'label'),
        teamId: mapValueOfType<String>(json, r'teamId'),
        type: CalendarScopeResponseTypeEnum.fromJson(json[r'type']),
        workspaceId: mapValueOfType<String>(json, r'workspaceId'),
      );
    }
    return null;
  }

  static List<CalendarScopeResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarScopeResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarScopeResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, CalendarScopeResponse> mapFromJson(dynamic json) {
    final map = <String, CalendarScopeResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = CalendarScopeResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of CalendarScopeResponse-objects as value to a dart map
  static Map<String, List<CalendarScopeResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<CalendarScopeResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = CalendarScopeResponse.listFromJson(
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

/// Scope type.
class CalendarScopeResponseTypeEnum {
  /// Instantiate a new enum with the provided [value].
  const CalendarScopeResponseTypeEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const workspace = CalendarScopeResponseTypeEnum._(r'workspace');
  static const team = CalendarScopeResponseTypeEnum._(r'team');
  static const channel = CalendarScopeResponseTypeEnum._(r'channel');

  /// List of all possible values in this [enum][CalendarScopeResponseTypeEnum].
  static const values = <CalendarScopeResponseTypeEnum>[
    workspace,
    team,
    channel,
  ];

  static CalendarScopeResponseTypeEnum? fromJson(dynamic value) =>
      CalendarScopeResponseTypeEnumTypeTransformer().decode(value);

  static List<CalendarScopeResponseTypeEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <CalendarScopeResponseTypeEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = CalendarScopeResponseTypeEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [CalendarScopeResponseTypeEnum] to String,
/// and [decode] dynamic data back to [CalendarScopeResponseTypeEnum].
class CalendarScopeResponseTypeEnumTypeTransformer {
  factory CalendarScopeResponseTypeEnumTypeTransformer() =>
      _instance ??= const CalendarScopeResponseTypeEnumTypeTransformer._();

  const CalendarScopeResponseTypeEnumTypeTransformer._();

  String encode(CalendarScopeResponseTypeEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a CalendarScopeResponseTypeEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  CalendarScopeResponseTypeEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'workspace':
          return CalendarScopeResponseTypeEnum.workspace;
        case r'team':
          return CalendarScopeResponseTypeEnum.team;
        case r'channel':
          return CalendarScopeResponseTypeEnum.channel;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [CalendarScopeResponseTypeEnumTypeTransformer] instance.
  static CalendarScopeResponseTypeEnumTypeTransformer? _instance;
}
