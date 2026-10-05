//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class MatrixBackendBoundary {
  /// Returns a new [MatrixBackendBoundary] instance.
  MatrixBackendBoundary({
    this.agentParticipation,
    this.connectorWritePolicy,
    this.messageContentPolicy,
    this.metadataReadable = const [],
    this.serverReadableMessageContent,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? agentParticipation;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? connectorWritePolicy;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? messageContentPolicy;

  /// Support-safe metadata classes backend diagnostics may use without implying message body access.
  List<String> metadataReadable;

  /// False for E2EE-compatible backend behavior; encrypted message bodies are client-readable only.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? serverReadableMessageContent;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is MatrixBackendBoundary &&
          other.agentParticipation == agentParticipation &&
          other.connectorWritePolicy == connectorWritePolicy &&
          other.messageContentPolicy == messageContentPolicy &&
          _deepEquality.equals(other.metadataReadable, metadataReadable) &&
          other.serverReadableMessageContent == serverReadableMessageContent;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (agentParticipation == null ? 0 : agentParticipation!.hashCode) +
      (connectorWritePolicy == null ? 0 : connectorWritePolicy!.hashCode) +
      (messageContentPolicy == null ? 0 : messageContentPolicy!.hashCode) +
      (metadataReadable.hashCode) +
      (serverReadableMessageContent == null
          ? 0
          : serverReadableMessageContent!.hashCode);

  @override
  String toString() =>
      'MatrixBackendBoundary[agentParticipation=$agentParticipation, connectorWritePolicy=$connectorWritePolicy, messageContentPolicy=$messageContentPolicy, metadataReadable=$metadataReadable, serverReadableMessageContent=$serverReadableMessageContent]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.agentParticipation != null) {
      json[r'agentParticipation'] = this.agentParticipation;
    } else {
      json[r'agentParticipation'] = null;
    }
    if (this.connectorWritePolicy != null) {
      json[r'connectorWritePolicy'] = this.connectorWritePolicy;
    } else {
      json[r'connectorWritePolicy'] = null;
    }
    if (this.messageContentPolicy != null) {
      json[r'messageContentPolicy'] = this.messageContentPolicy;
    } else {
      json[r'messageContentPolicy'] = null;
    }
    json[r'metadataReadable'] = this.metadataReadable;
    if (this.serverReadableMessageContent != null) {
      json[r'serverReadableMessageContent'] = this.serverReadableMessageContent;
    } else {
      json[r'serverReadableMessageContent'] = null;
    }
    return json;
  }

  /// Returns a new [MatrixBackendBoundary] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static MatrixBackendBoundary? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "MatrixBackendBoundary[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "MatrixBackendBoundary[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return MatrixBackendBoundary(
        agentParticipation: mapValueOfType<String>(json, r'agentParticipation'),
        connectorWritePolicy:
            mapValueOfType<String>(json, r'connectorWritePolicy'),
        messageContentPolicy:
            mapValueOfType<String>(json, r'messageContentPolicy'),
        metadataReadable: json[r'metadataReadable'] is Iterable
            ? (json[r'metadataReadable'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        serverReadableMessageContent:
            mapValueOfType<bool>(json, r'serverReadableMessageContent'),
      );
    }
    return null;
  }

  static List<MatrixBackendBoundary> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <MatrixBackendBoundary>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = MatrixBackendBoundary.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, MatrixBackendBoundary> mapFromJson(dynamic json) {
    final map = <String, MatrixBackendBoundary>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = MatrixBackendBoundary.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of MatrixBackendBoundary-objects as value to a dart map
  static Map<String, List<MatrixBackendBoundary>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<MatrixBackendBoundary>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = MatrixBackendBoundary.listFromJson(
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
