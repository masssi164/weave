//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class MeetingCapsuleCreateRequest {
  /// Returns a new [MeetingCapsuleCreateRequest] instance.
  MeetingCapsuleCreateRequest({
    this.agendaItems = const [],
    this.followUpRefs = const [],
    required this.title,
  });

  List<String> agendaItems;

  List<String> followUpRefs;

  String title;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MeetingCapsuleCreateRequest &&
          _deepEquality.equals(other.agendaItems, agendaItems) &&
          _deepEquality.equals(other.followUpRefs, followUpRefs) &&
          other.title == title;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (agendaItems.hashCode) + (followUpRefs.hashCode) + (title.hashCode);

  @override
  String toString() =>
      'MeetingCapsuleCreateRequest[agendaItems=$agendaItems, followUpRefs=$followUpRefs, title=$title]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'agendaItems'] = this.agendaItems;
    json[r'followUpRefs'] = this.followUpRefs;
    json[r'title'] = this.title;
    return json;
  }

  /// Returns a new [MeetingCapsuleCreateRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static MeetingCapsuleCreateRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "MeetingCapsuleCreateRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "MeetingCapsuleCreateRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return MeetingCapsuleCreateRequest(
        agendaItems: json[r'agendaItems'] is Iterable
            ? (json[r'agendaItems'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        followUpRefs: json[r'followUpRefs'] is Iterable
            ? (json[r'followUpRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        title: mapValueOfType<String>(json, r'title')!,
      );
    }
    return null;
  }

  static List<MeetingCapsuleCreateRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <MeetingCapsuleCreateRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = MeetingCapsuleCreateRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, MeetingCapsuleCreateRequest> mapFromJson(dynamic json) {
    final map = <String, MeetingCapsuleCreateRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = MeetingCapsuleCreateRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of MeetingCapsuleCreateRequest-objects as value to a dart map
  static Map<String, List<MeetingCapsuleCreateRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<MeetingCapsuleCreateRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = MeetingCapsuleCreateRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'title',
  };
}
