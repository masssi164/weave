//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class TaskItem {
  /// Returns a new [TaskItem] instance.
  TaskItem({
    this.assigneeRefs = const [],
    this.boardId,
    this.columnId,
    this.completedAt,
    this.decisionRefs = const [],
    this.description,
    this.dueAt,
    this.id,
    this.labelRefs = const [],
    this.position,
    this.priority,
    this.providerRefs = const [],
    this.startAt,
    this.status,
    this.title,
    this.updatedAt,
  });

  List<String> assigneeRefs;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? boardId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? columnId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? completedAt;

  List<String> decisionRefs;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? description;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? dueAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  List<String> labelRefs;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  int? position;

  TaskItemPriorityEnum? priority;

  List<ProviderRef> providerRefs;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? startAt;

  TaskItemStatusEnum? status;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? title;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? updatedAt;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TaskItem &&
          _deepEquality.equals(other.assigneeRefs, assigneeRefs) &&
          other.boardId == boardId &&
          other.columnId == columnId &&
          other.completedAt == completedAt &&
          _deepEquality.equals(other.decisionRefs, decisionRefs) &&
          other.description == description &&
          other.dueAt == dueAt &&
          other.id == id &&
          _deepEquality.equals(other.labelRefs, labelRefs) &&
          other.position == position &&
          other.priority == priority &&
          _deepEquality.equals(other.providerRefs, providerRefs) &&
          other.startAt == startAt &&
          other.status == status &&
          other.title == title &&
          other.updatedAt == updatedAt;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (assigneeRefs.hashCode) +
      (boardId == null ? 0 : boardId!.hashCode) +
      (columnId == null ? 0 : columnId!.hashCode) +
      (completedAt == null ? 0 : completedAt!.hashCode) +
      (decisionRefs.hashCode) +
      (description == null ? 0 : description!.hashCode) +
      (dueAt == null ? 0 : dueAt!.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (labelRefs.hashCode) +
      (position == null ? 0 : position!.hashCode) +
      (priority == null ? 0 : priority!.hashCode) +
      (providerRefs.hashCode) +
      (startAt == null ? 0 : startAt!.hashCode) +
      (status == null ? 0 : status!.hashCode) +
      (title == null ? 0 : title!.hashCode) +
      (updatedAt == null ? 0 : updatedAt!.hashCode);

  @override
  String toString() =>
      'TaskItem[assigneeRefs=$assigneeRefs, boardId=$boardId, columnId=$columnId, completedAt=$completedAt, decisionRefs=$decisionRefs, description=$description, dueAt=$dueAt, id=$id, labelRefs=$labelRefs, position=$position, priority=$priority, providerRefs=$providerRefs, startAt=$startAt, status=$status, title=$title, updatedAt=$updatedAt]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'assigneeRefs'] = this.assigneeRefs;
    if (this.boardId != null) {
      json[r'boardId'] = this.boardId;
    } else {
      json[r'boardId'] = null;
    }
    if (this.columnId != null) {
      json[r'columnId'] = this.columnId;
    } else {
      json[r'columnId'] = null;
    }
    if (this.completedAt != null) {
      json[r'completedAt'] = this.completedAt!.toUtc().toIso8601String();
    } else {
      json[r'completedAt'] = null;
    }
    json[r'decisionRefs'] = this.decisionRefs;
    if (this.description != null) {
      json[r'description'] = this.description;
    } else {
      json[r'description'] = null;
    }
    if (this.dueAt != null) {
      json[r'dueAt'] = this.dueAt!.toUtc().toIso8601String();
    } else {
      json[r'dueAt'] = null;
    }
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    json[r'labelRefs'] = this.labelRefs;
    if (this.position != null) {
      json[r'position'] = this.position;
    } else {
      json[r'position'] = null;
    }
    if (this.priority != null) {
      json[r'priority'] = this.priority;
    } else {
      json[r'priority'] = null;
    }
    json[r'providerRefs'] = this.providerRefs;
    if (this.startAt != null) {
      json[r'startAt'] = this.startAt!.toUtc().toIso8601String();
    } else {
      json[r'startAt'] = null;
    }
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    if (this.title != null) {
      json[r'title'] = this.title;
    } else {
      json[r'title'] = null;
    }
    if (this.updatedAt != null) {
      json[r'updatedAt'] = this.updatedAt!.toUtc().toIso8601String();
    } else {
      json[r'updatedAt'] = null;
    }
    return json;
  }

  /// Returns a new [TaskItem] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static TaskItem? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "TaskItem[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "TaskItem[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return TaskItem(
        assigneeRefs: json[r'assigneeRefs'] is Iterable
            ? (json[r'assigneeRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        boardId: mapValueOfType<String>(json, r'boardId'),
        columnId: mapValueOfType<String>(json, r'columnId'),
        completedAt: mapDateTime(json, r'completedAt', r''),
        decisionRefs: json[r'decisionRefs'] is Iterable
            ? (json[r'decisionRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        description: mapValueOfType<String>(json, r'description'),
        dueAt: mapDateTime(json, r'dueAt', r''),
        id: mapValueOfType<String>(json, r'id'),
        labelRefs: json[r'labelRefs'] is Iterable
            ? (json[r'labelRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        position: mapValueOfType<int>(json, r'position'),
        priority: TaskItemPriorityEnum.fromJson(json[r'priority']),
        providerRefs: ProviderRef.listFromJson(json[r'providerRefs']),
        startAt: mapDateTime(json, r'startAt', r''),
        status: TaskItemStatusEnum.fromJson(json[r'status']),
        title: mapValueOfType<String>(json, r'title'),
        updatedAt: mapDateTime(json, r'updatedAt', r''),
      );
    }
    return null;
  }

  static List<TaskItem> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <TaskItem>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = TaskItem.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, TaskItem> mapFromJson(dynamic json) {
    final map = <String, TaskItem>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = TaskItem.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of TaskItem-objects as value to a dart map
  static Map<String, List<TaskItem>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<TaskItem>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = TaskItem.listFromJson(
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

class TaskItemPriorityEnum {
  /// Instantiate a new enum with the provided [value].
  const TaskItemPriorityEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const LOW = TaskItemPriorityEnum._(r'LOW');
  static const NORMAL = TaskItemPriorityEnum._(r'NORMAL');
  static const HIGH = TaskItemPriorityEnum._(r'HIGH');
  static const URGENT = TaskItemPriorityEnum._(r'URGENT');

  /// List of all possible values in this [enum][TaskItemPriorityEnum].
  static const values = <TaskItemPriorityEnum>[
    LOW,
    NORMAL,
    HIGH,
    URGENT,
  ];

  static TaskItemPriorityEnum? fromJson(dynamic value) =>
      TaskItemPriorityEnumTypeTransformer().decode(value);

  static List<TaskItemPriorityEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <TaskItemPriorityEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = TaskItemPriorityEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [TaskItemPriorityEnum] to String,
/// and [decode] dynamic data back to [TaskItemPriorityEnum].
class TaskItemPriorityEnumTypeTransformer {
  factory TaskItemPriorityEnumTypeTransformer() =>
      _instance ??= const TaskItemPriorityEnumTypeTransformer._();

  const TaskItemPriorityEnumTypeTransformer._();

  String encode(TaskItemPriorityEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a TaskItemPriorityEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  TaskItemPriorityEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'LOW':
          return TaskItemPriorityEnum.LOW;
        case r'NORMAL':
          return TaskItemPriorityEnum.NORMAL;
        case r'HIGH':
          return TaskItemPriorityEnum.HIGH;
        case r'URGENT':
          return TaskItemPriorityEnum.URGENT;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [TaskItemPriorityEnumTypeTransformer] instance.
  static TaskItemPriorityEnumTypeTransformer? _instance;
}

class TaskItemStatusEnum {
  /// Instantiate a new enum with the provided [value].
  const TaskItemStatusEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const open = TaskItemStatusEnum._(r'open');
  static const blocked = TaskItemStatusEnum._(r'blocked');
  static const completed = TaskItemStatusEnum._(r'completed');
  static const archived = TaskItemStatusEnum._(r'archived');

  /// List of all possible values in this [enum][TaskItemStatusEnum].
  static const values = <TaskItemStatusEnum>[
    open,
    blocked,
    completed,
    archived,
  ];

  static TaskItemStatusEnum? fromJson(dynamic value) =>
      TaskItemStatusEnumTypeTransformer().decode(value);

  static List<TaskItemStatusEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <TaskItemStatusEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = TaskItemStatusEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [TaskItemStatusEnum] to String,
/// and [decode] dynamic data back to [TaskItemStatusEnum].
class TaskItemStatusEnumTypeTransformer {
  factory TaskItemStatusEnumTypeTransformer() =>
      _instance ??= const TaskItemStatusEnumTypeTransformer._();

  const TaskItemStatusEnumTypeTransformer._();

  String encode(TaskItemStatusEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a TaskItemStatusEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  TaskItemStatusEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'open':
          return TaskItemStatusEnum.open;
        case r'blocked':
          return TaskItemStatusEnum.blocked;
        case r'completed':
          return TaskItemStatusEnum.completed;
        case r'archived':
          return TaskItemStatusEnum.archived;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [TaskItemStatusEnumTypeTransformer] instance.
  static TaskItemStatusEnumTypeTransformer? _instance;
}
