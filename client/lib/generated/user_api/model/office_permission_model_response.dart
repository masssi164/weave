//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class OfficePermissionModelResponse {
  /// Returns a new [OfficePermissionModelResponse] instance.
  OfficePermissionModelResponse({
    this.canComment,
    this.canEdit,
    this.canFillForms,
    this.canReview,
    this.canView,
    this.reason,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? canComment;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? canEdit;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? canFillForms;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? canReview;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? canView;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? reason;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is OfficePermissionModelResponse &&
          other.canComment == canComment &&
          other.canEdit == canEdit &&
          other.canFillForms == canFillForms &&
          other.canReview == canReview &&
          other.canView == canView &&
          other.reason == reason;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (canComment == null ? 0 : canComment!.hashCode) +
      (canEdit == null ? 0 : canEdit!.hashCode) +
      (canFillForms == null ? 0 : canFillForms!.hashCode) +
      (canReview == null ? 0 : canReview!.hashCode) +
      (canView == null ? 0 : canView!.hashCode) +
      (reason == null ? 0 : reason!.hashCode);

  @override
  String toString() =>
      'OfficePermissionModelResponse[canComment=$canComment, canEdit=$canEdit, canFillForms=$canFillForms, canReview=$canReview, canView=$canView, reason=$reason]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.canComment != null) {
      json[r'canComment'] = this.canComment;
    } else {
      json[r'canComment'] = null;
    }
    if (this.canEdit != null) {
      json[r'canEdit'] = this.canEdit;
    } else {
      json[r'canEdit'] = null;
    }
    if (this.canFillForms != null) {
      json[r'canFillForms'] = this.canFillForms;
    } else {
      json[r'canFillForms'] = null;
    }
    if (this.canReview != null) {
      json[r'canReview'] = this.canReview;
    } else {
      json[r'canReview'] = null;
    }
    if (this.canView != null) {
      json[r'canView'] = this.canView;
    } else {
      json[r'canView'] = null;
    }
    if (this.reason != null) {
      json[r'reason'] = this.reason;
    } else {
      json[r'reason'] = null;
    }
    return json;
  }

  /// Returns a new [OfficePermissionModelResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static OfficePermissionModelResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "OfficePermissionModelResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "OfficePermissionModelResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return OfficePermissionModelResponse(
        canComment: mapValueOfType<bool>(json, r'canComment'),
        canEdit: mapValueOfType<bool>(json, r'canEdit'),
        canFillForms: mapValueOfType<bool>(json, r'canFillForms'),
        canReview: mapValueOfType<bool>(json, r'canReview'),
        canView: mapValueOfType<bool>(json, r'canView'),
        reason: mapValueOfType<String>(json, r'reason'),
      );
    }
    return null;
  }

  static List<OfficePermissionModelResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <OfficePermissionModelResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = OfficePermissionModelResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, OfficePermissionModelResponse> mapFromJson(dynamic json) {
    final map = <String, OfficePermissionModelResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = OfficePermissionModelResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of OfficePermissionModelResponse-objects as value to a dart map
  static Map<String, List<OfficePermissionModelResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<OfficePermissionModelResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = OfficePermissionModelResponse.listFromJson(
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
