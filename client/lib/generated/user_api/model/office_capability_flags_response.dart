//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficeCapabilityFlagsResponse {
  /// Returns a new [OfficeCapabilityFlagsResponse] instance.
  OfficeCapabilityFlagsResponse({
    this.comment,
    this.edit,
    this.formFill,
    this.review,
    this.view,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? comment;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? edit;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? formFill;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? review;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? view;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficeCapabilityFlagsResponse &&
          other.comment == comment &&
          other.edit == edit &&
          other.formFill == formFill &&
          other.review == review &&
          other.view == view;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (comment == null ? 0 : comment!.hashCode) +
      (edit == null ? 0 : edit!.hashCode) +
      (formFill == null ? 0 : formFill!.hashCode) +
      (review == null ? 0 : review!.hashCode) +
      (view == null ? 0 : view!.hashCode);

  @override
  String toString() =>
      'OfficeCapabilityFlagsResponse[comment=$comment, edit=$edit, formFill=$formFill, review=$review, view=$view]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.comment != null) {
      json[r'comment'] = this.comment;
    } else {
      json[r'comment'] = null;
    }
    if (this.edit != null) {
      json[r'edit'] = this.edit;
    } else {
      json[r'edit'] = null;
    }
    if (this.formFill != null) {
      json[r'formFill'] = this.formFill;
    } else {
      json[r'formFill'] = null;
    }
    if (this.review != null) {
      json[r'review'] = this.review;
    } else {
      json[r'review'] = null;
    }
    if (this.view != null) {
      json[r'view'] = this.view;
    } else {
      json[r'view'] = null;
    }
    return json;
  }

  /// Returns a new [OfficeCapabilityFlagsResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficeCapabilityFlagsResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficeCapabilityFlagsResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficeCapabilityFlagsResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficeCapabilityFlagsResponse(
        comment: mapValueOfType<bool>(json, r'comment'),
        edit: mapValueOfType<bool>(json, r'edit'),
        formFill: mapValueOfType<bool>(json, r'formFill'),
        review: mapValueOfType<bool>(json, r'review'),
        view: mapValueOfType<bool>(json, r'view'),
      );
    }
    return null;
  }

  static List<OfficeCapabilityFlagsResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficeCapabilityFlagsResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficeCapabilityFlagsResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficeCapabilityFlagsResponse> mapFromJson(dynamic json) {
    final map = <String, OfficeCapabilityFlagsResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficeCapabilityFlagsResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficeCapabilityFlagsResponse-objects as value to a dart map
  static Map<String, List<OfficeCapabilityFlagsResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficeCapabilityFlagsResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficeCapabilityFlagsResponse.listFromJson(
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
