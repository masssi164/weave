//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class WeaveProject {
  /// Returns a new [WeaveProject] instance.
  WeaveProject({
    this.id,
    this.memberRefs = const [],
    this.name,
    this.providerRefs = const [],
    this.visibility,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? id;

  List<String> memberRefs;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? name;

  List<ProviderRef> providerRefs;

  WeaveProjectVisibilityEnum? visibility;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is WeaveProject &&
          other.id == id &&
          _deepEquality.equals(other.memberRefs, memberRefs) &&
          other.name == name &&
          _deepEquality.equals(other.providerRefs, providerRefs) &&
          other.visibility == visibility;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (id == null ? 0 : id!.hashCode) +
      (memberRefs.hashCode) +
      (name == null ? 0 : name!.hashCode) +
      (providerRefs.hashCode) +
      (visibility == null ? 0 : visibility!.hashCode);

  @override
  String toString() =>
      'WeaveProject[id=$id, memberRefs=$memberRefs, name=$name, providerRefs=$providerRefs, visibility=$visibility]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.id != null) {
      json[r'id'] = this.id;
    } else {
      json[r'id'] = null;
    }
    json[r'memberRefs'] = this.memberRefs;
    if (this.name != null) {
      json[r'name'] = this.name;
    } else {
      json[r'name'] = null;
    }
    json[r'providerRefs'] = this.providerRefs;
    if (this.visibility != null) {
      json[r'visibility'] = this.visibility;
    } else {
      json[r'visibility'] = null;
    }
    return json;
  }

  /// Returns a new [WeaveProject] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static WeaveProject? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "WeaveProject[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "WeaveProject[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return WeaveProject(
        id: mapValueOfType<String>(json, r'id'),
        memberRefs: json[r'memberRefs'] is Iterable
            ? (json[r'memberRefs'] as Iterable)
                .cast<String>()
                .toList(growable: false)
            : const [],
        name: mapValueOfType<String>(json, r'name'),
        providerRefs: ProviderRef.listFromJson(json[r'providerRefs']),
        visibility: WeaveProjectVisibilityEnum.fromJson(json[r'visibility']),
      );
    }
    return null;
  }

  static List<WeaveProject> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WeaveProject>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WeaveProject.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, WeaveProject> mapFromJson(dynamic json) {
    final map = <String, WeaveProject>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = WeaveProject.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of WeaveProject-objects as value to a dart map
  static Map<String, List<WeaveProject>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<WeaveProject>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = WeaveProject.listFromJson(
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

class WeaveProjectVisibilityEnum {
  /// Instantiate a new enum with the provided [value].
  const WeaveProjectVisibilityEnum._(this.value);

  /// The underlying value of this enum member.
  final String value;

  @override
  String toString() => value;

  String toJson() => value;

  static const PRIVATE = WeaveProjectVisibilityEnum._(r'PRIVATE');
  static const WORKSPACE = WeaveProjectVisibilityEnum._(r'WORKSPACE');
  static const PUBLIC = WeaveProjectVisibilityEnum._(r'PUBLIC');

  /// List of all possible values in this [enum][WeaveProjectVisibilityEnum].
  static const values = <WeaveProjectVisibilityEnum>[
    PRIVATE,
    WORKSPACE,
    PUBLIC,
  ];

  static WeaveProjectVisibilityEnum? fromJson(dynamic value) =>
      WeaveProjectVisibilityEnumTypeTransformer().decode(value);

  static List<WeaveProjectVisibilityEnum> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <WeaveProjectVisibilityEnum>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = WeaveProjectVisibilityEnum.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }
}

/// Transformation class that can [encode] an instance of [WeaveProjectVisibilityEnum] to String,
/// and [decode] dynamic data back to [WeaveProjectVisibilityEnum].
class WeaveProjectVisibilityEnumTypeTransformer {
  factory WeaveProjectVisibilityEnumTypeTransformer() =>
      _instance ??= const WeaveProjectVisibilityEnumTypeTransformer._();

  const WeaveProjectVisibilityEnumTypeTransformer._();

  String encode(WeaveProjectVisibilityEnum data) => data.value;

  /// Decodes a [dynamic value][data] to a WeaveProjectVisibilityEnum.
  ///
  /// If [allowNull] is true and the [dynamic value][data] cannot be decoded successfully,
  /// then null is returned. However, if [allowNull] is false and the [dynamic value][data]
  /// cannot be decoded successfully, then an [UnimplementedError] is thrown.
  ///
  /// The [allowNull] is very handy when an API changes and a new enum value is added or removed,
  /// and users are still using an old app with the old code.
  WeaveProjectVisibilityEnum? decode(dynamic data, {bool allowNull = true}) {
    if (data != null) {
      switch (data) {
        case r'PRIVATE':
          return WeaveProjectVisibilityEnum.PRIVATE;
        case r'WORKSPACE':
          return WeaveProjectVisibilityEnum.WORKSPACE;
        case r'PUBLIC':
          return WeaveProjectVisibilityEnum.PUBLIC;
        default:
          if (!allowNull) {
            throw ArgumentError('Unknown enum value to decode: $data');
          }
      }
    }
    return null;
  }

  /// Singleton [WeaveProjectVisibilityEnumTypeTransformer] instance.
  static WeaveProjectVisibilityEnumTypeTransformer? _instance;
}
