#!/usr/bin/env python3
"""Generate the pinned Dart User HTTP SDK from the server-owned contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contracts/openapi/weave-user-openapi.json"
OUTPUT = ROOT / "client/lib/generated/user_api"
VERSION = "7.17.0"
SHA256 = "25d6bd8273dd2be99979d544b62ea43f0ce1975f1aa582678b5093d1e7fcfce8"
URL = (
    "https://repo.maven.apache.org/maven2/org/openapitools/"
    f"openapi-generator-cli/{VERSION}/openapi-generator-cli-{VERSION}.jar"
)
JAR = ROOT / "build/tools" / f"openapi-generator-cli-{VERSION}.jar"


def generator_jar() -> Path:
    JAR.parent.mkdir(parents=True, exist_ok=True)
    if not JAR.exists() or hashlib.sha256(JAR.read_bytes()).hexdigest() != SHA256:
        with tempfile.NamedTemporaryFile(dir=JAR.parent, delete=False) as download:
            temporary = Path(download.name)
        try:
            urllib.request.urlretrieve(URL, temporary)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
                raise RuntimeError("OpenAPI Generator checksum mismatch")
            temporary.replace(JAR)
        finally:
            temporary.unlink(missing_ok=True)
    return JAR


def generate(destination: Path) -> None:
    with tempfile.TemporaryDirectory(prefix="weave-user-api-") as temporary:
        work = Path(temporary)
        subprocess.run(
            [
                "java",
                "-jar",
                str(generator_jar()),
                "generate",
                "-g",
                "dart",
                "-i",
                str(CONTRACT),
                "-o",
                str(work),
                "--additional-properties=pubName=weave_user_api,pubLibrary=weave_user_api,serializationLibrary=native_serialization",
                "--global-property=apiDocs=false,modelDocs=false,apiTests=false,modelTests=false",
            ],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        shutil.copytree(work / "lib", destination)
    preserve_partial_profile_update(destination)
    correct_enum_map_decoding(destination)
    propagate_binary_upload_errors(destination)
    avoid_implicit_text_body_for_empty_operations(destination)
    preserve_calendar_date_fields(destination)
    enforce_closed_org_manifest(destination)
    subprocess.run(["dart", "format", str(destination)], check=True, stdout=subprocess.DEVNULL)


def preserve_partial_profile_update(destination: Path) -> None:
    """Correct the native Dart generator's optional-map default for PATCH.

    OpenAPI marks accessibilityPreferences optional. Generator 7.17.0 emits
    an empty map and serializes it even when the caller omits the field. That
    would clear existing profile preferences during unrelated updates.
    This narrow, fail-closed transform remains part of deterministic generation.
    """
    model = destination / "model/update_product_profile_request.dart"
    source = model.read_text()
    replacements = {
        "this.accessibilityPreferences = const {},": "this.accessibilityPreferences,",
        "Map<String, String> accessibilityPreferences;": "Map<String, String>? accessibilityPreferences;",
        "json[r'accessibilityPreferences'] = this.accessibilityPreferences;":
            "if (this.accessibilityPreferences != null) { json[r'accessibilityPreferences'] = this.accessibilityPreferences; }",
        "mapCastOfType<String, String>(json, r'accessibilityPreferences') ?? const {}":
            "mapCastOfType<String, String>(json, r'accessibilityPreferences')",
    }
    for before, after in replacements.items():
        if source.count(before) != 1:
            raise RuntimeError(f"OpenAPI Generator PATCH model changed: expected {before!r}")
        source = source.replace(before, after, 1)
    source, omitted = re.subn(
        r"\n    } else {\n      json\[r'(avatar|displayName|locale|profileVisibility|timezone)'\] = null;\n    }",
        "\n    }",
        source,
    )
    if omitted != 5:
        raise RuntimeError("OpenAPI Generator PATCH optional-field serialization changed")
    model.write_text(source)


def correct_enum_map_decoding(destination: Path) -> None:
    """Preserve typed enum values in the generated manifest map."""
    model = destination / "model/organization_manifest_response.dart"
    source = model.read_text()
    before = "mapCastOfType<String, String>(json, r'memberCapabilityStates') ?? const {}"
    after = (
        "(mapCastOfType<String, String>(json, r'memberCapabilityStates') ?? "
        "const <String, String>{}).map((key, value) => MapEntry(key, "
        "OrganizationManifestResponseMemberCapabilityStatesEnum.fromJson(value) "
        "?? (throw FormatException('Unknown member capability state: $value'))))"
    )
    if source.count(before) != 1:
        raise RuntimeError("OpenAPI Generator manifest enum map projection changed")
    model.write_text(source.replace(before, after, 1))


def propagate_binary_upload_errors(destination: Path) -> None:
    """Keep a failed upload byte stream from looking like a completed request.

    Generator 7.17.0 closes the HTTP request when a MultipartFile source emits
    an error, but drops that error. The server could then receive a truncated
    body without the client observing the source failure. Forward the error to
    the request stream so the generated operation fails instead.
    """
    client = destination / "api_client.dart"
    source = client.read_text()
    before = "onError: (Object error, StackTrace trace) => request.sink.close(),"
    after = (
        "onError: (Object error, StackTrace trace) {\n"
        "  request.sink.addError(error, trace);\n"
        "  unawaited(request.sink.close());\n"
        "},"
    )
    if source.count(before) != 1:
        raise RuntimeError("OpenAPI Generator binary upload handling changed")
    client.write_text(source.replace(before, after, 1))


def avoid_implicit_text_body_for_empty_operations(destination: Path) -> None:
    """Keep bodyless generated mutations free of an invented text/plain body.

    OpenAPI Generator 7.17.0 serializes null as an empty string. The Dart HTTP
    client then inserts text/plain on POST, PUT, PATCH and DELETE, contradicting
    code-first operations with no request body. Preserve null through transport.
    """
    client = destination / "api_client.dart"
    source = client.read_text()
    before = "body: msgBody,"
    if source.count(before) != 4:
        raise RuntimeError("OpenAPI Generator empty-body transport changed")
    client.write_text(source.replace(before, "body: body == null ? null : msgBody,"))


def preserve_calendar_date_fields(destination: Path) -> None:
    """Keep date-only values independent of the consumer's host timezone.

    Generator 7.17.0 parses DATE into host-local midnight and converts it to UTC
    during serialization. That changes dates east of UTC and can normalize a
    skipped civil day before the application sees it. Use a neutral UTC carrier
    for date-only wire values, and serialize calendar fields without conversion.
    Date-time parsing retains its existing instant semantics.
    """
    helper = destination / "api_helper.dart"
    source = helper.read_text()
    before = "/// Returns a valid [DateTime] found at the specified Map [key], null otherwise."
    after = """/// Decodes a date-only value without applying the host timezone.
DateTime? mapDateOnly(dynamic map, String key) {
  final dynamic value = map is Map ? map[key] : null;
  if (value is! String || !RegExp(r'^\\d{4}-\\d{2}-\\d{2}$').hasMatch(value)) {
    return null;
  }
  final date = DateTime.tryParse('${value}T00:00:00Z');
  return date != null && date.toIso8601String().substring(0, 10) == value
      ? date
      : null;
}

""" + before
    if source.count(before) != 1:
        raise RuntimeError("OpenAPI Generator date-only decoding changed")
    helper.write_text(source.replace(before, after, 1))

    model = destination / "model/calendar_time_value.dart"
    source = model.read_text()
    before = "_dateFormatter.format(this.date!.toUtc())"
    if source.count(before) != 1:
        raise RuntimeError("OpenAPI Generator Calendar DATE serialization changed")
    source = source.replace(before, "_dateFormatter.format(this.date!)", 1)
    before = "date: mapDateTime(json, r'date', r'')"
    if source.count(before) != 1:
        raise RuntimeError("OpenAPI Generator Calendar DATE decoding changed")
    model.write_text(source.replace(before, "date: mapDateOnly(json, r'date')", 1))


def enforce_closed_org_manifest(destination: Path) -> None:
    """Make the generated OrgManifest decoder fail closed in release builds.

    The native Dart generator silently drops unknown properties and checks
    required properties only with asserts. This narrow transform derives the
    allowed and required wire keys from the server-owned OpenAPI schemas.
    Nested values are still decoded by the generated transport models.
    """
    schemas = json.loads(CONTRACT.read_text())["components"]["schemas"]
    names = ("PlatformConfigResponse", "Oidc", "Protocols", "DomainCapability", "RecoveryAction")
    expected = {
        "PlatformConfigResponse": {"schemaVersion", "organizationOrigin", "userApiBaseUrl", "oidc", "protocols", "releasePosture", "domains", "recoveryActions"},
        "Oidc": {"issuer", "clientId"},
        "Protocols": {"matrixClientServerBaseUrl"},
        "DomainCapability": {"domain", "state", "capabilities", "supportReference"},
        "RecoveryAction": {"code", "label", "supportReference"},
    }
    for name in names:
        if set(schemas[name]["properties"]) != expected[name]:
            raise RuntimeError(f"OrgManifest OpenAPI shape changed for {name}")

    operation = destination / "api/platform_api.dart"
    operation_source = operation.read_text()
    first_operation, next_operation = operation_source.split("  /// Get platform module status", 1)
    status_check = "if (response.statusCode >= HttpStatus.badRequest) {"
    if first_operation.count(status_check) != 1:
        raise RuntimeError("OpenAPI Generator platform config status handling changed")
    first_operation = first_operation.replace(
        status_check, "if (response.statusCode >= HttpStatus.multipleChoices) {", 1
    )
    operation.write_text(first_operation + "  /// Get platform module status" + next_operation)

    def dart_keys(keys: set[str]) -> str:
        return "<String>{" + ", ".join(repr(key) for key in sorted(keys)) + "}"

    definitions = []
    for name in names:
        required = set(schemas[name].get("required", ()))
        definitions.append(
            f"    '{name}': _OrgManifestShape({dart_keys(set(schemas[name]['properties']))}, {dart_keys(required)}),"
        )

    model = destination / "model/platform_config_response.dart"
    source = model.read_text()
    before = "      final json = value.cast<String, dynamic>();"
    after = before + "\n      _validateOrgManifestShape(value);"
    if source.count(before) != 1:
        raise RuntimeError("OpenAPI Generator manifest decoder changed")
    source = source.replace(before, after, 1)
    source += """

// The manifest is a closed public bootstrap document. Validate its wire shape
// before the generated transport decoder can discard an unknown property.
class OrgManifestShapeException implements Exception {
  const OrgManifestShapeException(this.message);
  final String message;
}

class _OrgManifestShape {
  const _OrgManifestShape(this.allowed, this.required);
  final Set<String> allowed;
  final Set<String> required;
}

const _orgManifestShapes = <String, _OrgManifestShape>{
""" + "\n".join(definitions) + """
};

Map<String, dynamic> _orgManifestObject(dynamic value, String name) {
  if (value is! Map<String, dynamic>) {
    throw OrgManifestShapeException('$name must be an object');
  }
  final shape = _orgManifestShapes[name]!;
  if (value.keys.any((key) => !shape.allowed.contains(key))) {
    throw const OrgManifestShapeException('The organization manifest contains unsupported fields');
  }
  for (final key in shape.required) {
    if (!value.containsKey(key) || value[key] == null) {
      final prefix = name == 'PlatformConfigResponse' ? '' : '${name[0].toLowerCase()}${name.substring(1)}.';
      throw OrgManifestShapeException('$prefix$key is required');
    }
  }
  return value;
}

void _validateOrgManifestShape(dynamic value) {
  final manifest = _orgManifestObject(value, 'PlatformConfigResponse');
  _orgManifestObject(manifest['oidc'], 'Oidc');
  _orgManifestObject(manifest['protocols'], 'Protocols');
  final domains = manifest['domains'];
  if (domains is! List || domains.isEmpty) {
    throw const OrgManifestShapeException('domains must be a nonempty array');
  }
  for (final domain in domains) {
    final entry = _orgManifestObject(domain, 'DomainCapability');
    if (entry['capabilities'] is! List ||
        (entry['capabilities'] as List).any((capability) => capability is! String) ||
        (entry.containsKey('supportReference') &&
            entry['supportReference'] is! String)) {
      throw const OrgManifestShapeException('domains entries are incomplete');
    }
  }
  if (manifest.containsKey('recoveryActions')) {
    final actions = manifest['recoveryActions'];
    if (actions is! List) {
      throw const OrgManifestShapeException('recoveryActions must be an array');
    }
    for (final action in actions) {
      final entry = _orgManifestObject(action, 'RecoveryAction');
      if (entry['code'] is! String ||
          entry['label'] is! String ||
          (entry.containsKey('supportReference') &&
              entry['supportReference'] is! String)) {
        throw const OrgManifestShapeException('recoveryActions entries are incomplete');
      }
    }
  }
}
"""
    model.write_text(source)


def same_sources(left: Path, right: Path) -> bool:
    left_files = {file.relative_to(left): file for file in left.rglob("*.dart")}
    right_files = {file.relative_to(right): file for file in right.rglob("*.dart")}
    return left_files.keys() == right_files.keys() and all(
        file.read_bytes() == right_files[name].read_bytes()
        for name, file in left_files.items()
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail on stale generated output")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="weave-user-api-check-") as temporary:
        candidate = Path(temporary) / "lib"
        generate(candidate)
        if args.check:
            if not OUTPUT.exists() or not same_sources(candidate, OUTPUT):
                print("Dart User API SDK is stale; run ./gradlew generateClientUserApi", file=sys.stderr)
                return 1
            print("Dart User API SDK is current")
            return 0
        if OUTPUT.exists():
            shutil.rmtree(OUTPUT)
        shutil.copytree(candidate, OUTPUT)
        print(f"Generated Dart User API SDK at {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
