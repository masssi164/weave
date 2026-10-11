//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SlackOutboundMessageRequest {
  /// Returns a new [SlackOutboundMessageRequest] instance.
  SlackOutboundMessageRequest({
    required this.eventId,
    required this.text,
    this.threadTs,
  });

  String eventId;

  String text;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? threadTs;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SlackOutboundMessageRequest &&
          other.eventId == eventId &&
          other.text == text &&
          other.threadTs == threadTs;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (eventId.hashCode) +
      (text.hashCode) +
      (threadTs == null ? 0 : threadTs!.hashCode);

  @override
  String toString() =>
      'SlackOutboundMessageRequest[eventId=$eventId, text=$text, threadTs=$threadTs]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'eventId'] = this.eventId;
    json[r'text'] = this.text;
    if (this.threadTs != null) {
      json[r'threadTs'] = this.threadTs;
    } else {
      json[r'threadTs'] = null;
    }
    return json;
  }

  /// Returns a new [SlackOutboundMessageRequest] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SlackOutboundMessageRequest? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SlackOutboundMessageRequest[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SlackOutboundMessageRequest[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SlackOutboundMessageRequest(
        eventId: mapValueOfType<String>(json, r'eventId')!,
        text: mapValueOfType<String>(json, r'text')!,
        threadTs: mapValueOfType<String>(json, r'threadTs'),
      );
    }
    return null;
  }

  static List<SlackOutboundMessageRequest> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SlackOutboundMessageRequest>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SlackOutboundMessageRequest.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SlackOutboundMessageRequest> mapFromJson(dynamic json) {
    final map = <String, SlackOutboundMessageRequest>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SlackOutboundMessageRequest.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SlackOutboundMessageRequest-objects as value to a dart map
  static Map<String, List<SlackOutboundMessageRequest>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SlackOutboundMessageRequest>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SlackOutboundMessageRequest.listFromJson(
          entry.value,
          growable: growable,
        );
      }
    }
    return map;
  }

  /// The list of required keys that must be present in a JSON.
  static const requiredKeys = <String>{
    'eventId',
    'text',
  };
}
