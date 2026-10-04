//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderChoiceModelResponse {
  /// Returns a new [ProviderChoiceModelResponse] instance.
  ProviderChoiceModelResponse({
    this.adapters = const [],
    this.adminRiskNotes = const [],
    this.choiceModel,
    this.recommended,
  });

  /// Support-safe adapter keys covered by this choice model.
  List<String> adapters;

  /// Support-safe admin/operator risk notes. No secrets, URLs, tenant IDs, or raw provider errors.
  List<String> adminRiskNotes;

  /// Stable choice model key: recommended_self_hosted_default, external_existing_provider, or managed_cloud_provider.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? choiceModel;

  /// Whether this is the recommended sovereign/default posture.
  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? recommended;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderChoiceModelResponse &&
          _deepEquality.equals(other.adapters, adapters) &&
          _deepEquality.equals(other.adminRiskNotes, adminRiskNotes) &&
          other.choiceModel == choiceModel &&
          other.recommended == recommended;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (adapters.hashCode) +
      (adminRiskNotes.hashCode) +
      (choiceModel == null ? 0 : choiceModel!.hashCode) +
      (recommended == null ? 0 : recommended!.hashCode);

  @override
  String toString() =>
      'ProviderChoiceModelResponse[adapters=$adapters, adminRiskNotes=$adminRiskNotes, choiceModel=$choiceModel, recommended=$recommended]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    json[r'adapters'] = this.adapters;
    json[r'adminRiskNotes'] = this.adminRiskNotes;
    if (this.choiceModel != null) {
      json[r'choiceModel'] = this.choiceModel;
    } else {
      json[r'choiceModel'] = null;
    }
    if (this.recommended != null) {
      json[r'recommended'] = this.recommended;
    } else {
      json[r'recommended'] = null;
    }
    return json;
  }

  /// Returns a new [ProviderChoiceModelResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderChoiceModelResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderChoiceModelResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderChoiceModelResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderChoiceModelResponse(
        adapters: json[r'adapters'] is Iterable
            ? (json[r'adapters'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        adminRiskNotes: json[r'adminRiskNotes'] is Iterable
            ? (json[r'adminRiskNotes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        choiceModel: mapValueOfType<String>(json, r'choiceModel'),
        recommended: mapValueOfType<bool>(json, r'recommended'),
      );
    }
    return null;
  }

  static List<ProviderChoiceModelResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderChoiceModelResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderChoiceModelResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderChoiceModelResponse> mapFromJson(dynamic json) {
    final map = <String, ProviderChoiceModelResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderChoiceModelResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderChoiceModelResponse-objects as value to a dart map
  static Map<String, List<ProviderChoiceModelResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderChoiceModelResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderChoiceModelResponse.listFromJson(
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
