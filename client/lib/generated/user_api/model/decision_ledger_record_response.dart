//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DecisionLedgerRecordResponse {
  /// Returns a new [DecisionLedgerRecordResponse] instance.
  DecisionLedgerRecordResponse({
    this.authorRef,
    this.contextId,
    this.conversationId,
    this.decidedAt,
    this.followUpRefs = const [],
    this.id,
    this.openQuestions = const [],
    this.references = const [],
    this.risks = const [],
    this.status,
    this.supportSafe,
    this.title,
  });

  /// Weave principal reference, not a raw provider user id.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? authorRef;

  /// Weave Context/Space id that owns the decision.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? contextId;

  /// Stable Weave conversation/channel id.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? conversationId;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? decidedAt;

  List<String> followUpRefs;

  /// Stable Weave decision id.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  List<String> openQuestions;

  List<DecisionLedgerReferenceResponse> references;

  List<String> risks;

  /// Lifecycle state.
  DecisionLedgerRecordResponseStatusEnum? status;

  /// Whether this record is safe for member responses and support bundles.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? title;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DecisionLedgerRecordResponse &&
          other.authorRef == authorRef &&
          other.contextId == contextId &&
          other.conversationId == conversationId &&
          other.decidedAt == decidedAt &&
          _deepEquality.equals(other.followUpRefs, followUpRefs) &&
          other.id == id &&
          _deepEquality.equals(other.openQuestions, openQuestions) &&
          _deepEquality.equals(other.references, references) &&
          _deepEquality.equals(other.risks, risks) &&
          other.status == status &&
          other.supportSafe == supportSafe &&
          other.title == title;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (authorRef == null ? 0 : authorRef!.hashCode) +
      (contextId == null ? 0 : contextId!.hashCode) +
      (conversationId == null ? 0 : conversationId!.hashCode) +
      (decidedAt == null ? 0 : decidedAt!.hashCode) +
      (followUpRefs.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (openQuestions.hashCode) +
      (references.hashCode) +
      (risks.hashCode) +
      (status == null ? 0 : status!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (title == null ? 0 : title!.hashCode);

  @override
  String toString() =>
      'DecisionLedgerRecordResponse[authorRef=$authorRef, contextId=$contextId, conversationId=$conversationId, decidedAt=$decidedAt, followUpRefs=$followUpRefs, id=$id, openQuestions=$openQuestions, references=$references, risks=$risks, status=$status, supportSafe=$supportSafe, title=$title]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.authorRef != null) {
      json[r'authorRef'] = this.authorRef;
    } else {
      json[r'authorRef'] = null;
    }
    if (this.contextId != null) {
      json[r'contextId'] = this.contextId;
    } else {
      json[r'contextId'] = null;
    }
    if (this.conversationId != null) {
      json[r'conversationId'] = this.conversationId;
    } else {
      json[r'conversationId'] = null;
    }
    if (this.decidedAt != null) {
      json[r'decidedAt'] = this.decidedAt!.toUtc().toIso8601String();
    } else {
      json[r'decidedAt'] = null;
    }
    json[r'followUpRefs'] = this.followUpRefs;
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    json[r'openQuestions'] = this.openQuestions;
    json[r'references'] = this.references;
    json[r'risks'] = this.risks;
    if (this.status != null) {
      json[r'status'] = this.status;
    } else {
      json[r'status'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    if (this.title != null) {
      json[r'title'] = this.title;
    } else {
      json[r'title'] = null;
    }
    return json;
  }

  /// Returns a new [DecisionLedgerRecordResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DecisionLedgerRecordResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DecisionLedgerRecordResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DecisionLedgerRecordResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DecisionLedgerRecordResponse(
        authorRef: mapValueOfType<String>(json, r'authorRef'),
        contextId: mapValueOfType<String>(json, r'contextId'),
        conversationId: mapValueOfType<String>(json, r'conversationId'),
        decidedAt: mapDateTime(json, r'decidedAt', r''),
        followUpRefs: json[r'followUpRefs'] is Iterable
            ? (json[r'followUpRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        id: mapValueOfType<String>(json, r'id'),
        openQuestions: json[r'openQuestions'] is Iterable
            ? (json[r'openQuestions'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        references:
            DecisionLedgerReferenceResponse.listFromJson(json[r'references']),
        risks: json[r'risks'] is Iterable
            ? (json[r'risks'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        status:
            DecisionLedgerRecordResponseStatusEnum.fromJson(json[r'status']),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        title: mapValueOfType<String>(json, r'title'),
      );
    }
    return null;
  }

  static List<DecisionLedgerRecordResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DecisionLedgerRecordResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DecisionLedgerRecordResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DecisionLedgerRecordResponse> mapFromJson(dynamic json) {
    final map = <String, DecisionLedgerRecordResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DecisionLedgerRecordResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DecisionLedgerRecordResponse-objects as value to a dart map
  static Map<String, List<DecisionLedgerRecordResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DecisionLedgerRecordResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DecisionLedgerRecordResponse.listFromJson(
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

/// Lifecycle state.
class DecisionLedgerRecordResponseStatusEnum {
  /// Instantiate a new enum with the provided [value].
  const DecisionLedgerRecordResponseStatusEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const proposed = DecisionLedgerRecordResponseStatusEnum._(r'proposed');
  static const accepted = DecisionLedgerRecordResponseStatusEnum._(r'accepted');
  static const superseded =
      DecisionLedgerRecordResponseStatusEnum._(r'superseded');
  static const rejected = DecisionLedgerRecordResponseStatusEnum._(r'rejected');

  /// List of all possible values in this [enum][DecisionLedgerRecordResponseStatusEnum].
  static const values = <DecisionLedgerRecordResponseStatusEnum>[
    proposed,
    accepted,
    superseded,
    rejected,
  ];

  static DecisionLedgerRecordResponseStatusEnum? fromJson(dynamic value) =>
      DecisionLedgerRecordResponseStatusEnumTypeTransformer().decode(value);

  static List<DecisionLedgerRecordResponseStatusEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DecisionLedgerRecordResponseStatusEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DecisionLedgerRecordResponseStatusEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [DecisionLedgerRecordResponseStatusEnum] to String,
/// and [decode] dynamic data back to [DecisionLedgerRecordResponseStatusEnum].
class DecisionLedgerRecordResponseStatusEnumTypeTransformer {
  factory DecisionLedgerRecordResponseStatusEnumTypeTransformer() =>
      _instance ??=
          const DecisionLedgerRecordResponseStatusEnumTypeTransformer._();

  const DecisionLedgerRecordResponseStatusEnumTypeTransformer._();

  String encode(DecisionLedgerRecordResponseStatusEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a DecisionLedgerRecordResponseStatusEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  DecisionLedgerRecordResponseStatusEnum? decode(dynamic data,
      {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'proposed':
          return DecisionLedgerRecordResponseStatusEnum.proposed;
        case r'accepted':
          return DecisionLedgerRecordResponseStatusEnum.accepted;
        case r'superseded':
          return DecisionLedgerRecordResponseStatusEnum.superseded;
        case r'rejected':
          return DecisionLedgerRecordResponseStatusEnum.rejected;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [DecisionLedgerRecordResponseStatusEnumTypeTransformer] instance.
  static DecisionLedgerRecordResponseStatusEnumTypeTransformer? _instance;
}
