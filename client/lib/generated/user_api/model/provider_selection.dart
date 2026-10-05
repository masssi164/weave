//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class ProviderSelection {
  /// Returns a new [ProviderSelection] instance.
  ProviderSelection({
    this.applied,
    this.category,
    this.choiceModel,
    this.lossyMappingNotes = const [],
    this.migrationDryRunRequired,
    this.providerKey,
    this.secretRef,
    this.selectedAt,
    this.selectedBy,
    this.supportSafe,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? applied;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? category;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? choiceModel;

  List<String> lossyMappingNotes;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? migrationDryRunRequired;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? providerKey;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? secretRef;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  DateTime? selectedAt;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? selectedBy;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? supportSafe;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ProviderSelection &&
          other.applied == applied &&
          other.category == category &&
          other.choiceModel == choiceModel &&
          _deepEquality.equals(other.lossyMappingNotes, lossyMappingNotes) &&
          other.migrationDryRunRequired == migrationDryRunRequired &&
          other.providerKey == providerKey &&
          other.secretRef == secretRef &&
          other.selectedAt == selectedAt &&
          other.selectedBy == selectedBy &&
          other.supportSafe == supportSafe;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (applied == null ? 0 : applied!.hashCode) +
      (category == null ? 0 : category!.hashCode) +
      (choiceModel == null ? 0 : choiceModel!.hashCode) +
      (lossyMappingNotes.hashCode) +
      (migrationDryRunRequired == null
          ? 0
          : migrationDryRunRequired!.hashCode) +
      (providerKey == null ? 0 : providerKey!.hashCode) +
      (secretRef == null ? 0 : secretRef!.hashCode) +
      (selectedAt == null ? 0 : selectedAt!.hashCode) +
      (selectedBy == null ? 0 : selectedBy!.hashCode) +
      (supportSafe == null ? 0 : supportSafe!.hashCode);

  @override
  String toString() =>
      'ProviderSelection[applied=$applied, category=$category, choiceModel=$choiceModel, lossyMappingNotes=$lossyMappingNotes, migrationDryRunRequired=$migrationDryRunRequired, providerKey=$providerKey, secretRef=$secretRef, selectedAt=$selectedAt, selectedBy=$selectedBy, supportSafe=$supportSafe]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.applied != null) {
      json[r'applied'] = this.applied;
    } else {
      json[r'applied'] = null;
    }
    if (this.category != null) {
      json[r'category'] = this.category;
    } else {
      json[r'category'] = null;
    }
    if (this.choiceModel != null) {
      json[r'choiceModel'] = this.choiceModel;
    } else {
      json[r'choiceModel'] = null;
    }
    json[r'lossyMappingNotes'] = this.lossyMappingNotes;
    if (this.migrationDryRunRequired != null) {
      json[r'migrationDryRunRequired'] = this.migrationDryRunRequired;
    } else {
      json[r'migrationDryRunRequired'] = null;
    }
    if (this.providerKey != null) {
      json[r'providerKey'] = this.providerKey;
    } else {
      json[r'providerKey'] = null;
    }
    if (this.secretRef != null) {
      json[r'secretRef'] = this.secretRef;
    } else {
      json[r'secretRef'] = null;
    }
    if (this.selectedAt != null) {
      json[r'selectedAt'] = this.selectedAt!.toUtc().toIso8601String();
    } else {
      json[r'selectedAt'] = null;
    }
    if (this.selectedBy != null) {
      json[r'selectedBy'] = this.selectedBy;
    } else {
      json[r'selectedBy'] = null;
    }
    if (this.supportSafe != null) {
      json[r'supportSafe'] = this.supportSafe;
    } else {
      json[r'supportSafe'] = null;
    }
    return json;
  }

  /// Returns a new [ProviderSelection] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static ProviderSelection? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "ProviderSelection[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "ProviderSelection[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return ProviderSelection(
        applied: mapValueOfType<bool>(json, r'applied'),
        category: mapValueOfType<String>(json, r'category'),
        choiceModel: mapValueOfType<String>(json, r'choiceModel'),
        lossyMappingNotes: json[r'lossyMappingNotes'] is Iterable
            ? (json[r'lossyMappingNotes'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        migrationDryRunRequired:
            mapValueOfType<bool>(json, r'migrationDryRunRequired'),
        providerKey: mapValueOfType<String>(json, r'providerKey'),
        secretRef: mapValueOfType<String>(json, r'secretRef'),
        selectedAt: mapDateTime(json, r'selectedAt', r''),
        selectedBy: mapValueOfType<String>(json, r'selectedBy'),
        supportSafe: mapValueOfType<bool>(json, r'supportSafe'),
      );
    }
    return null;
  }

  static List<ProviderSelection> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <ProviderSelection>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = ProviderSelection.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, ProviderSelection> mapFromJson(dynamic json) {
    final map = <String, ProviderSelection>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = ProviderSelection.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of ProviderSelection-objects as value to a dart map
  static Map<String, List<ProviderSelection>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<ProviderSelection>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = ProviderSelection.listFromJson(
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
