//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//
// @dart=2.18

// ignore_for_file: unused_element, unused_import
// ignore_for_file: always_put_required_named_parameters_first
// ignore_for_file: constant_identifier_names
// ignore_for_file: lines_longer_than_80_chars

part of weave_user_api;

class SlackOutboundMessageResponse {
  /// Returns a new [SlackOutboundMessageResponse] instance.
  SlackOutboundMessageResponse({
    this.channelRef,
    this.deliveryStatus,
    this.dryRunOnly,
    this.idempotencyKey,
    this.payload = const {},
    this.productionCallAttempted,
    this.provider,
    this.workspaceRef,
  });

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? channelRef;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? deliveryStatus;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? dryRunOnly;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? idempotencyKey;

  Map<String, Object> payload;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  bool? productionCallAttempted;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? provider;

  ///
  /// Please note: This property should have been non-nullable! Since the specification file
  /// does not include a default value (using the "default:" property), however, the generated
  /// source code must fall back to having a nullable type.
  /// Consider adding a "default:" property in the specification file to hide this note.
  ///
  String? workspaceRef;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SlackOutboundMessageResponse &&
          other.channelRef == channelRef &&
          other.deliveryStatus == deliveryStatus &&
          other.dryRunOnly == dryRunOnly &&
          other.idempotencyKey == idempotencyKey &&
          _deepEquality.equals(other.payload, payload) &&
          other.productionCallAttempted == productionCallAttempted &&
          other.provider == provider &&
          other.workspaceRef == workspaceRef;

  @override
  int get hashCode =>
      // ignore: unnecessary_parenthesis
      (channelRef == null ? 0 : channelRef!.hashCode) +
      (deliveryStatus == null ? 0 : deliveryStatus!.hashCode) +
      (dryRunOnly == null ? 0 : dryRunOnly!.hashCode) +
      (idempotencyKey == null ? 0 : idempotencyKey!.hashCode) +
      (payload.hashCode) +
      (productionCallAttempted == null
          ? 0
          : productionCallAttempted!.hashCode) +
      (provider == null ? 0 : provider!.hashCode) +
      (workspaceRef == null ? 0 : workspaceRef!.hashCode);

  @override
  String toString() =>
      'SlackOutboundMessageResponse[channelRef=$channelRef, deliveryStatus=$deliveryStatus, dryRunOnly=$dryRunOnly, idempotencyKey=$idempotencyKey, payload=$payload, productionCallAttempted=$productionCallAttempted, provider=$provider, workspaceRef=$workspaceRef]';

  Map<String, dynamic> toJson() {
    final json = <String, dynamic>{};
    if (this.channelRef != null) {
      json[r'channelRef'] = this.channelRef;
    } else {
      json[r'channelRef'] = null;
    }
    if (this.deliveryStatus != null) {
      json[r'deliveryStatus'] = this.deliveryStatus;
    } else {
      json[r'deliveryStatus'] = null;
    }
    if (this.dryRunOnly != null) {
      json[r'dryRunOnly'] = this.dryRunOnly;
    } else {
      json[r'dryRunOnly'] = null;
    }
    if (this.idempotencyKey != null) {
      json[r'idempotencyKey'] = this.idempotencyKey;
    } else {
      json[r'idempotencyKey'] = null;
    }
    json[r'payload'] = this.payload;
    if (this.productionCallAttempted != null) {
      json[r'productionCallAttempted'] = this.productionCallAttempted;
    } else {
      json[r'productionCallAttempted'] = null;
    }
    if (this.provider != null) {
      json[r'provider'] = this.provider;
    } else {
      json[r'provider'] = null;
    }
    if (this.workspaceRef != null) {
      json[r'workspaceRef'] = this.workspaceRef;
    } else {
      json[r'workspaceRef'] = null;
    }
    return json;
  }

  /// Returns a new [SlackOutboundMessageResponse] instance and imports its values from
  /// [value] if it's a [Map], null otherwise.
  // ignore: prefer_constructors_over_static_methods
  static SlackOutboundMessageResponse? fromJson(dynamic value) {
    if (value is Map) {
      final json = value.cast<String, dynamic>();

      // Ensure that the map contains the required keys.
      // Note 1: the values aren't checked for validity beyond being non-null.
      // Note 2: this code is stripped in release mode!
      assert(() {
        requiredKeys.forEach((key) {
          assert(json.containsKey(key),
              'Required key "SlackOutboundMessageResponse[$key]" is missing from JSON.');
          assert(json[key] != null,
              'Required key "SlackOutboundMessageResponse[$key]" has a null value in JSON.');
        });
        return true;
      }());

      return SlackOutboundMessageResponse(
        channelRef: mapValueOfType<String>(json, r'channelRef'),
        deliveryStatus: mapValueOfType<String>(json, r'deliveryStatus'),
        dryRunOnly: mapValueOfType<bool>(json, r'dryRunOnly'),
        idempotencyKey: mapValueOfType<String>(json, r'idempotencyKey'),
        payload: mapCastOfType<String, Object>(json, r'payload') ?? const {},
        productionCallAttempted:
            mapValueOfType<bool>(json, r'productionCallAttempted'),
        provider: mapValueOfType<String>(json, r'provider'),
        workspaceRef: mapValueOfType<String>(json, r'workspaceRef'),
      );
    }
    return null;
  }

  static List<SlackOutboundMessageResponse> listFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final result = <SlackOutboundMessageResponse>[];
    if (json is List && json.isNotEmpty) {
      for (final row in json) {
        final value = SlackOutboundMessageResponse.fromJson(row);
        if (value != null) {
          result.add(value);
        }
      }
    }
    return result.toList(growable: growable);
  }

  static Map<String, SlackOutboundMessageResponse> mapFromJson(dynamic json) {
    final map = <String, SlackOutboundMessageResponse>{};
    if (json is Map && json.isNotEmpty) {
      json = json.cast<String, dynamic>(); // ignore: parameter_assignments
      for (final entry in json.entries) {
        final value = SlackOutboundMessageResponse.fromJson(entry.value);
        if (value != null) {
          map[entry.key] = value;
        }
      }
    }
    return map;
  }

  // maps a json object with a list of SlackOutboundMessageResponse-objects as value to a dart map
  static Map<String, List<SlackOutboundMessageResponse>> mapListFromJson(
    dynamic json, {
    bool growable = false,
  }) {
    final map = <String, List<SlackOutboundMessageResponse>>{};
    if (json is Map && json.isNotEmpty) {
      // ignore: parameter_assignments
      json = json.cast<String, dynamic>();
      for (final entry in json.entries) {
        map[entry.key] = SlackOutboundMessageResponse.listFromJson(
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
