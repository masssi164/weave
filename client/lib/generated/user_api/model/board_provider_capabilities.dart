//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class BoardProviderCapabilities {
  /// Returns a new [BoardProviderCapabilities] instance.
  BoardProviderCapabilities({
    this.enabled,
    this.provider,
    this.supportSafeSummary,
    this.supported = const {},
    this.unsupported = const {},
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? enabled;

  BoardProviderCapabilitiesProviderEnum? provider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? supportSafeSummary;

  Set<BoardProviderCapabilitiesSupportedEnum> supported;

  Set<BoardProviderCapabilitiesUnsupportedEnum> unsupported;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BoardProviderCapabilities &&
          other.enabled == enabled &&
          other.provider == provider &&
          other.supportSafeSummary == supportSafeSummary &&
          _deepEquality.equals(other.supported, supported) &&
          _deepEquality.equals(other.unsupported, unsupported);

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (enabled == null ? 0 : enabled!.hashCode) +
      (provider == null ? 0 : provider!.hashCode) +
      (supportSafeSummary == null ? 0 : supportSafeSummary!.hashCode) +
      (supported.hashCode) +
      (unsupported.hashCode);

  @override
  String toString() =>
      'BoardProviderCapabilities[enabled=$enabled, provider=$provider, supportSafeSummary=$supportSafeSummary, supported=$supported, unsupported=$unsupported]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.enabled != null) {
      json[r'enabled'] = this.enabled;
    } else {
      json[r'enabled'] = null;
    }
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
    }
    if (this.supportSafeSummary != null) {
      json[r'supportSafeSummary'] = this.supportSafeSummary;
    } else {
      json[r'supportSafeSummary'] = null;
    }
    json[r'supported'] = this.supported.toList(growable: false);
    json[r'unsupported'] = this.unsupported.toList(growable: false);
    return json;
  }

  /// Returns a new [BoardProviderCapabilities] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static BoardProviderCapabilities? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "BoardProviderCapabilities[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "BoardProviderCapabilities[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return BoardProviderCapabilities(
        enabled: mapValueOfType<bool>(json, r'enabled'),
        provider:
            BoardProviderCapabilitiesProviderEnum.fromJson(json[r'provider']),
        supportSafeSummary: mapValueOfType<String>(json, r'supportSafeSummary'),
        supported: BoardProviderCapabilitiesSupportedEnum.listFromJson(
                json[r'supported'])
            .toSet(),
        unsupported: BoardProviderCapabilitiesUnsupportedEnum.listFromJson(
                json[r'unsupported'])
            .toSet(),
      );
    }
    return null;
  }

  static List<BoardProviderCapabilities> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardProviderCapabilities>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardProviderCapabilities.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, BoardProviderCapabilities> mapFromJson(dynamic json) {
    final map = <String, BoardProviderCapabilities>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = BoardProviderCapabilities.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of BoardProviderCapabilities-objects as value to a dart map
  static Map<String, List<BoardProviderCapabilities>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<BoardProviderCapabilities>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = BoardProviderCapabilities.listFromJson(
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

class BoardProviderCapabilitiesProviderEnum {
  /// Instantiate a new enum with the provided [value].
  const BoardProviderCapabilitiesProviderEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const vikunja = BoardProviderCapabilitiesProviderEnum._(r'vikunja');
  static const openproject =
      BoardProviderCapabilitiesProviderEnum._(r'openproject');
  static const nextcloudDeck =
      BoardProviderCapabilitiesProviderEnum._(r'nextcloud-deck');
  static const placeholderBoards =
      BoardProviderCapabilitiesProviderEnum._(r'placeholder-boards');
  static const inMemory = BoardProviderCapabilitiesProviderEnum._(r'in-memory');
  static const unknown = BoardProviderCapabilitiesProviderEnum._(r'unknown');

  /// List of all possible values in this [enum][BoardProviderCapabilitiesProviderEnum].
  static const values = <BoardProviderCapabilitiesProviderEnum>[
    vikunja,
    openproject,
    nextcloudDeck,
    placeholderBoards,
    inMemory,
    unknown,
  ];

  static BoardProviderCapabilitiesProviderEnum? fromJson(dynamic value) =>
      BoardProviderCapabilitiesProviderEnumTypeTransformer().decode(value);

  static List<BoardProviderCapabilitiesProviderEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardProviderCapabilitiesProviderEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardProviderCapabilitiesProviderEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [BoardProviderCapabilitiesProviderEnum] to String,
/// and [decode] dynamic data back to [BoardProviderCapabilitiesProviderEnum].
class BoardProviderCapabilitiesProviderEnumTypeTransformer {
  factory BoardProviderCapabilitiesProviderEnumTypeTransformer() =>
      _instance ??=
          const BoardProviderCapabilitiesProviderEnumTypeTransformer._();

  const BoardProviderCapabilitiesProviderEnumTypeTransformer._();

  String encode(BoardProviderCapabilitiesProviderEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a BoardProviderCapabilitiesProviderEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  BoardProviderCapabilitiesProviderEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'vikunja':
          return BoardProviderCapabilitiesProviderEnum.vikunja;
        case r'openproject':
          return BoardProviderCapabilitiesProviderEnum.openproject;
        case r'nextcloud-deck':
          return BoardProviderCapabilitiesProviderEnum.nextcloudDeck;
        case r'placeholder-boards':
          return BoardProviderCapabilitiesProviderEnum.placeholderBoards;
        case r'in-memory':
          return BoardProviderCapabilitiesProviderEnum.inMemory;
        case r'unknown':
          return BoardProviderCapabilitiesProviderEnum.unknown;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [BoardProviderCapabilitiesProviderEnumTypeTransformer] instance.
  static BoardProviderCapabilitiesProviderEnumTypeTransformer? _instance;
}

class BoardProviderCapabilitiesSupportedEnum {
  /// Instantiate a new enum with the provided [value].
  const BoardProviderCapabilitiesSupportedEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const comments = BoardProviderCapabilitiesSupportedEnum._(r'comments');
  static const attachments =
      BoardProviderCapabilitiesSupportedEnum._(r'attachments');
  static const nonDestructiveArchive =
      BoardProviderCapabilitiesSupportedEnum._(r'non_destructive_archive');
  static const statusUpdates =
      BoardProviderCapabilitiesSupportedEnum._(r'status_updates');
  static const decisionLinks =
      BoardProviderCapabilitiesSupportedEnum._(r'decision_links');
  static const webhookEvents =
      BoardProviderCapabilitiesSupportedEnum._(r'webhook_events');
  static const incrementalSync =
      BoardProviderCapabilitiesSupportedEnum._(r'incremental_sync');
  static const checklists =
      BoardProviderCapabilitiesSupportedEnum._(r'checklists');
  static const customFields =
      BoardProviderCapabilitiesSupportedEnum._(r'custom_fields');
  static const accessibleNonDragMoves =
      BoardProviderCapabilitiesSupportedEnum._(r'accessible_non_drag_moves');

  /// List of all possible values in this [enum][BoardProviderCapabilitiesSupportedEnum].
  static const values = <BoardProviderCapabilitiesSupportedEnum>[
    comments,
    attachments,
    nonDestructiveArchive,
    statusUpdates,
    decisionLinks,
    webhookEvents,
    incrementalSync,
    checklists,
    customFields,
    accessibleNonDragMoves,
  ];

  static BoardProviderCapabilitiesSupportedEnum? fromJson(dynamic value) =>
      BoardProviderCapabilitiesSupportedEnumTypeTransformer().decode(value);

  static List<BoardProviderCapabilitiesSupportedEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardProviderCapabilitiesSupportedEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardProviderCapabilitiesSupportedEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [BoardProviderCapabilitiesSupportedEnum] to String,
/// and [decode] dynamic data back to [BoardProviderCapabilitiesSupportedEnum].
class BoardProviderCapabilitiesSupportedEnumTypeTransformer {
  factory BoardProviderCapabilitiesSupportedEnumTypeTransformer() =>
      _instance ??=
          const BoardProviderCapabilitiesSupportedEnumTypeTransformer._();

  const BoardProviderCapabilitiesSupportedEnumTypeTransformer._();

  String encode(BoardProviderCapabilitiesSupportedEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a BoardProviderCapabilitiesSupportedEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  BoardProviderCapabilitiesSupportedEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'comments':
          return BoardProviderCapabilitiesSupportedEnum.comments;
        case r'attachments':
          return BoardProviderCapabilitiesSupportedEnum.attachments;
        case r'non_destructive_archive':
          return BoardProviderCapabilitiesSupportedEnum.nonDestructiveArchive;
        case r'status_updates':
          return BoardProviderCapabilitiesSupportedEnum.statusUpdates;
        case r'decision_links':
          return BoardProviderCapabilitiesSupportedEnum.decisionLinks;
        case r'webhook_events':
          return BoardProviderCapabilitiesSupportedEnum.webhookEvents;
        case r'incremental_sync':
          return BoardProviderCapabilitiesSupportedEnum.incrementalSync;
        case r'checklists':
          return BoardProviderCapabilitiesSupportedEnum.checklists;
        case r'custom_fields':
          return BoardProviderCapabilitiesSupportedEnum.customFields;
        case r'accessible_non_drag_moves':
          return BoardProviderCapabilitiesSupportedEnum.accessibleNonDragMoves;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [BoardProviderCapabilitiesSupportedEnumTypeTransformer] instance.
  static BoardProviderCapabilitiesSupportedEnumTypeTransformer? _instance;
}

class BoardProviderCapabilitiesUnsupportedEnum {
  /// Instantiate a new enum with the provided [value].
  const BoardProviderCapabilitiesUnsupportedEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const comments =
      BoardProviderCapabilitiesUnsupportedEnum._(r'comments');
  static const attachments =
      BoardProviderCapabilitiesUnsupportedEnum._(r'attachments');
  static const nonDestructiveArchive =
      BoardProviderCapabilitiesUnsupportedEnum._(r'non_destructive_archive');
  static const statusUpdates =
      BoardProviderCapabilitiesUnsupportedEnum._(r'status_updates');
  static const decisionLinks =
      BoardProviderCapabilitiesUnsupportedEnum._(r'decision_links');
  static const webhookEvents =
      BoardProviderCapabilitiesUnsupportedEnum._(r'webhook_events');
  static const incrementalSync =
      BoardProviderCapabilitiesUnsupportedEnum._(r'incremental_sync');
  static const checklists =
      BoardProviderCapabilitiesUnsupportedEnum._(r'checklists');
  static const customFields =
      BoardProviderCapabilitiesUnsupportedEnum._(r'custom_fields');
  static const accessibleNonDragMoves =
      BoardProviderCapabilitiesUnsupportedEnum._(r'accessible_non_drag_moves');

  /// List of all possible values in this [enum][BoardProviderCapabilitiesUnsupportedEnum].
  static const values = <BoardProviderCapabilitiesUnsupportedEnum>[
    comments,
    attachments,
    nonDestructiveArchive,
    statusUpdates,
    decisionLinks,
    webhookEvents,
    incrementalSync,
    checklists,
    customFields,
    accessibleNonDragMoves,
  ];

  static BoardProviderCapabilitiesUnsupportedEnum? fromJson(dynamic value) =>
      BoardProviderCapabilitiesUnsupportedEnumTypeTransformer().decode(value);

  static List<BoardProviderCapabilitiesUnsupportedEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <BoardProviderCapabilitiesUnsupportedEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = BoardProviderCapabilitiesUnsupportedEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [BoardProviderCapabilitiesUnsupportedEnum] to String,
/// and [decode] dynamic data back to [BoardProviderCapabilitiesUnsupportedEnum].
class BoardProviderCapabilitiesUnsupportedEnumTypeTransformer {
  factory BoardProviderCapabilitiesUnsupportedEnumTypeTransformer() =>
      _instance ??=
          const BoardProviderCapabilitiesUnsupportedEnumTypeTransformer._();

  const BoardProviderCapabilitiesUnsupportedEnumTypeTransformer._();

  String encode(BoardProviderCapabilitiesUnsupportedEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a BoardProviderCapabilitiesUnsupportedEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  BoardProviderCapabilitiesUnsupportedEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'comments':
          return BoardProviderCapabilitiesUnsupportedEnum.comments;
        case r'attachments':
          return BoardProviderCapabilitiesUnsupportedEnum.attachments;
        case r'non_destructive_archive':
          return BoardProviderCapabilitiesUnsupportedEnum.nonDestructiveArchive;
        case r'status_updates':
          return BoardProviderCapabilitiesUnsupportedEnum.statusUpdates;
        case r'decision_links':
          return BoardProviderCapabilitiesUnsupportedEnum.decisionLinks;
        case r'webhook_events':
          return BoardProviderCapabilitiesUnsupportedEnum.webhookEvents;
        case r'incremental_sync':
          return BoardProviderCapabilitiesUnsupportedEnum.incrementalSync;
        case r'checklists':
          return BoardProviderCapabilitiesUnsupportedEnum.checklists;
        case r'custom_fields':
          return BoardProviderCapabilitiesUnsupportedEnum.customFields;
        case r'accessible_non_drag_moves':
          return BoardProviderCapabilitiesUnsupportedEnum
              .accessibleNonDragMoves;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [BoardProviderCapabilitiesUnsupportedEnumTypeTransformer] instance.
  static BoardProviderCapabilitiesUnsupportedEnumTypeTransformer? _instance;
}
