//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class DomainCapability {
  /// Returns a new [DomainCapability] instance.
  DomainCapability({
    this.capabilities = const [],
    this.domain,
    this.state,
    this.supportReference,
  });

  List<String> capabilities;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? domain;

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
  String? supportReference;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DomainCapability &&
          _deepEquality.equals(other.capabilities, capabilities) &&
          other.domain == domain &&
          other.state == state &&
          other.supportReference == supportReference;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (capabilities.hashCode) +
      (domain == null ? 0 : domain!.hashCode) +
      (state == null ? 0 : state!.hashCode) +
      (supportReference == null ? 0 : supportReference!.hashCode);

  @override
  String toString() =>
      'DomainCapability[capabilities=$capabilities, domain=$domain, state=$state, supportReference=$supportReference]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'capabilities'] = this.capabilities;
    if (this.domain != null) {
      json[r'domain'] = this.domain;
    } else {
      json[r'domain'] = null;
    }
    if (this.state != null) {
      json[r'state'] = this.state;
    } else {
      json[r'state'] = null;
    }
    if (this.supportReference != null) {
      json[r'supportReference'] = this.supportReference;
    } else {
      json[r'supportReference'] = null;
    }
    return json;
  }

  /// Returns a new [DomainCapability] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static DomainCapability? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "DomainCapability[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "DomainCapability[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return DomainCapability(
        capabilities: json[r'capabilities'] is Iterable
            ? (json[r'capabilities'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        domain: mapValueOfType<String>(json, r'domain'),
        state: mapValueOfType<String>(json, r'state'),
        supportReference: mapValueOfType<String>(json, r'supportReference'),
      );
    }
    return null;
  }

  static List<DomainCapability> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <DomainCapability>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = DomainCapability.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, DomainCapability> mapFromJson(dynamic json) {
    final map = <String, DomainCapability>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = DomainCapability.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of DomainCapability-objects as value to a dart map
  static Map<String, List<DomainCapability>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<DomainCapability>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = DomainCapability.listFromJson(
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
