//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class MeetingCapsuleResponse {
  /// Returns a new [MeetingCapsuleResponse] instance.
  MeetingCapsuleResponse({
    this.agendaItems = const [],
    this.contextId,
    this.conversationId,
    this.createdAt,
    this.disabledControls = const [],
    this.disabledReason,
    this.followUpRefs = const [],
    this.id,
    this.liveKitProviderDetailsExposed,
    this.matrixE2eeClaimedForMedia,
    this.participants = const [],
    this.recordingEnabled,
    this.state,
    this.supportSafe,
    this.title,
    this.transcriptionEnabled,
  });

  List<String> agendaItems;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? contextId;

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
  DateTime? createdAt;

  List<String> disabledControls;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? disabledReason;

  List<String> followUpRefs;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  /// Whether raw LiveKit/provider setup details are exposed to the client. Always false.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? liveKitProviderDetailsExposed;

  /// Whether Matrix chat E2EE is claimed as media protection. Always false.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? matrixE2eeClaimedForMedia;

  List<String> participants;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? recordingEnabled;

  /// Meeting lifecycle state in Weave vocabulary.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? state;

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

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? transcriptionEnabled;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MeetingCapsuleResponse &&
          _deepEquality.equals(other.agendaItems, agendaItems) &&
          other.contextId == contextId &&
          other.conversationId == conversationId &&
          other.createdAt == createdAt &&
          _deepEquality.equals(other.disabledControls, disabledControls) &&
          other.disabledReason == disabledReason &&
          _deepEquality.equals(other.followUpRefs, followUpRefs) &&
          other.id == id &&
          other.liveKitProviderDetailsExposed ==
              liveKitProviderDetailsExposed &&
          other.matrixE2eeClaimedForMedia == matrixE2eeClaimedForMedia &&
          _deepEquality.equals(other.participants, participants) &&
          other.recordingEnabled == recordingEnabled &&
          other.state == state &&
          other.supportSafe == supportSafe &&
          other.title == title &&
          other.transcriptionEnabled == transcriptionEnabled;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (agendaItems.hashCode) +
      (contextId == null ? 0 : contextId!.hashCode) +
      (conversationId == null ? 0 : conversationId!.hashCode) +
      (createdAt == null ? 0 : createdAt!.hashCode) +
      (disabledControls.hashCode) +
      (disabledReason == null ? 0 : disabledReason!.hashCode) +
      (followUpRefs.hashCode) +
      (id == null ? 0 : id!.hashCode) +
      (liveKitProviderDetailsExposed == null
          ? 0
          : liveKitProviderDetailsExposed!.hashCode) +
      (matrixE2eeClaimedForMedia == null
          ? 0
          : matrixE2eeClaimedForMedia!.hashCode) +
      (participants.hashCode) +
      (recordingEnabled == null ? 0 : recordingEnabled!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode) +
      (title == null ? 0 : title!.hashCode) +
      (transcriptionEnabled == null ? 0 : transcriptionEnabled!.hashCode);

  @override
  String toString() =>
      'MeetingCapsuleResponse[agendaItems=$agendaItems, contextId=$contextId, conversationId=$conversationId, createdAt=$createdAt, disabledControls=$disabledControls, disabledReason=$disabledReason, followUpRefs=$followUpRefs, id=$id, liveKitProviderDetailsExposed=$liveKitProviderDetailsExposed, matrixE2eeClaimedForMedia=$matrixE2eeClaimedForMedia, participants=$participants, recordingEnabled=$recordingEnabled, state=$state, supportSafe=$supportSafe, title=$title, transcriptionEnabled=$transcriptionEnabled]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'agendaItems'] = this.agendaItems;
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
    if (this.createdAt != null) {
      json[r'createdAt'] = this.createdAt!.toUtc().toIso8601String();
    } else {
      json[r'createdAt'] = null;
    }
    json[r'disabledControls'] = this.disabledControls;
    if (this.disabledReason != null) {
      json[r'disabledReason'] = this.disabledReason;
    } else {
      json[r'disabledReason'] = null;
    }
    json[r'followUpRefs'] = this.followUpRefs;
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    if (this.liveKitProviderDetailsExposed != null) {
      json[r'liveKitProviderDetailsExposed'] =
          this.liveKitProviderDetailsExposed;
    } else {
      json[r'liveKitProviderDetailsExposed'] = null;
    }
    if (this.matrixE2eeClaimedForMedia != null) {
      json[r'matrixE2eeClaimedForMedia'] = this.matrixE2eeClaimedForMedia;
    } else {
      json[r'matrixE2eeClaimedForMedia'] = null;
    }
    json[r'participants'] = this.participants;
    if (this.recordingEnabled != null) {
      json[r'recordingEnabled'] = this.recordingEnabled;
    } else {
      json[r'recordingEnabled'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
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
    if (this.transcriptionEnabled != null) {
      json[r'transcriptionEnabled'] = this.transcriptionEnabled;
    } else {
      json[r'transcriptionEnabled'] = null;
    }
    return json;
  }

  /// Returns a new [MeetingCapsuleResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static MeetingCapsuleResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "MeetingCapsuleResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "MeetingCapsuleResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return MeetingCapsuleResponse(
        agendaItems: json[r'agendaItems'] is Iterable
            ? (json[r'agendaItems'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        contextId: mapValueOfType<String>(json, r'contextId'),
        conversationId: mapValueOfType<String>(json, r'conversationId'),
        createdAt: mapDateTime(json, r'createdAt', r''),
        disabledControls: json[r'disabledControls'] is Iterable
            ? (json[r'disabledControls'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        disabledReason: mapValueOfType<String>(json, r'disabledReason'),
        followUpRefs: json[r'followUpRefs'] is Iterable
            ? (json[r'followUpRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        id: mapValueOfType<String>(json, r'id'),
        liveKitProviderDetailsExposed:
            mapValueOfType<bool>(json, r'liveKitProviderDetailsExposed'),
        matrixE2eeClaimedForMedia:
            mapValueOfType<bool>(json, r'matrixE2eeClaimedForMedia'),
        participants: json[r'participants'] is Iterable
            ? (json[r'participants'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        recordingEnabled: mapValueOfType<bool>(json, r'recordingEnabled'),
        state: mapValueOfType<String>(json, r'state'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
        title: mapValueOfType<String>(json, r'title'),
        transcriptionEnabled:
            mapValueOfType<bool>(json, r'transcriptionEnabled'),
      );
    }
    return null;
  }

  static List<MeetingCapsuleResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <MeetingCapsuleResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = MeetingCapsuleResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, MeetingCapsuleResponse> mapFromJson(dynamic json) {
    final map = <String, MeetingCapsuleResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = MeetingCapsuleResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of MeetingCapsuleResponse-objects as value to a dart map
  static Map<String, List<MeetingCapsuleResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<MeetingCapsuleResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = MeetingCapsuleResponse.listFromJson(
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
