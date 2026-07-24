#!/usr/bin/env python3
"""Generate Flutter/Dart JSON models from the server-owned OpenAPI artifact."""
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from argparse import ArgumentParser
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
OPENAPI = ROOT / "contracts/openapi/weave-openapi.json"
OUT = ROOT / "client/lib/generated/openapi_models.dart"
RESERVED = {
    "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class",
    "const", "continue", "covariant", "default", "deferred", "do", "dynamic", "else",
    "enum", "export", "extends", "extension", "external", "factory", "false", "final",
    "finally", "for", "Function", "get", "hide", "if", "implements", "import", "in",
    "interface", "is", "late", "library", "mixin", "new", "null", "on", "operator",
    "part", "required", "rethrow", "return", "set", "show", "static", "super", "switch",
    "sync", "this", "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield",
}


def pascal(name: str) -> str:
    parts = re.split(r"[^A-Za-z0-9]+", name)
    result = "".join(p[:1].upper() + p[1:] for p in parts if p)
    return result if result and not result[0].isdigit() else f"Model{result}"


def camel(name: str) -> str:
    p = pascal(name)
    c = p[:1].lower() + p[1:]
    return f"{c}_" if c in RESERVED else c


def refs_in(value: Any) -> set[str]:
    found: set[str] = set()
    if isinstance(value, dict):
        ref = value.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/schemas/"):
            found.add(ref.split("/")[-1])
        for child in value.values():
            found.update(refs_in(child))
    elif isinstance(value, list):
        for child in value:
            found.update(refs_in(child))
    return found


def type_for(schema: dict[str, Any] | None, *, required: bool = False) -> str:
    if not schema:
        return "Object?"
    if "$ref" in schema:
        base = pascal(schema["$ref"].split("/")[-1])
        return base if required else f"{base}?"
    if "allOf" in schema and schema["allOf"]:
        return type_for(schema["allOf"][0], required=required)
    if "oneOf" in schema or "anyOf" in schema:
        return "Object?"
    schema_type = schema.get("type")
    nullable = False
    if isinstance(schema_type, list):
        nullable = "null" in schema_type
        schema_type = next((t for t in schema_type if t != "null"), None)
    if schema_type == "array":
        base = f"List<{type_for(schema.get('items')).removesuffix('?')}>"
    elif schema_type == "object" or "properties" in schema:
        additional = schema.get("additionalProperties")
        if "properties" not in schema and additional:
            base = "Map<String, Object?>"
        else:
            base = "Map<String, Object?>"
    elif schema_type in {"integer", "number"}:
        base = "num" if schema_type == "number" else "int"
    elif schema_type == "boolean":
        base = "bool"
    elif schema_type == "string":
        base = "String"
    else:
        base = "Object?"
    if nullable and not base.endswith("?"):
        return f"{base}?"
    if base.endswith("?"):
        return base
    return base if required else f"{base}?"


def read_expr(field_type: str, key: str) -> str:
    value = f"json[{json.dumps(key)}]"
    if field_type == "String": return f"{value} as String"
    if field_type == "String?": return f"{value} as String?"
    if field_type == "bool": return f"{value} as bool"
    if field_type == "bool?": return f"{value} as bool?"
    if field_type == "int": return f"({value} as num).toInt()"
    if field_type == "int?": return f"({value} as num?)?.toInt()"
    if field_type == "num": return f"{value} as num"
    if field_type == "num?": return f"{value} as num?"
    if field_type.startswith("List<"):
        nullable = field_type.endswith("?")
        inner = field_type[len("List<"):-2 if nullable else -1]
        if inner in {"String", "bool", "int", "num", "Object"}:
            conv = "e"
            if inner == "int": conv = "(e as num).toInt()"
            elif inner != "Object": conv = f"e as {inner}"
            cast = f"{value} as List<dynamic>{'?' if nullable else ''}"
            return f"({cast}){'?' if nullable else ''}.map((e) => {conv}).toList()"
        cast = f"{value} as List<dynamic>{'?' if nullable else ''}"
        return f"({cast}){'?' if nullable else ''}.map((e) => {inner}.fromJson(e as Map<String, dynamic>)).toList()"
    if field_type.startswith("Map<"):
        nullable = field_type.endswith("?")
        return f"({value} as Map<String, dynamic>{'?' if nullable else ''}){'?' if nullable else ''}.cast<String, Object?>()"
    if not field_type.endswith("?") and field_type not in {"Object"}:
        return f"{field_type}.fromJson({value} as Map<String, dynamic>)"
    if field_type.endswith("?") and field_type[:-1] not in {"Object"}:
        cls = field_type[:-1]
        return f"{value} == null ? null : {cls}.fromJson({value} as Map<String, dynamic>)"
    return value


def emit_class(name: str, schema: dict[str, Any]) -> list[str]:
    cls = pascal(name)
    props = schema.get("properties") if isinstance(schema.get("properties"), dict) else {}
    required_props = set(schema.get("required", [])) if isinstance(schema.get("required"), list) else set()
    lines = [f"class {cls} {{"]
    params = [
        f"{'required ' if k in required_props else ''}this.{camel(k)}"
        for k in sorted(props)
    ]
    lines.append(f"  const {cls}({{{', '.join(params)}}});" if params else f"  const {cls}();")
    lines.append("")
    lines.append(f"  factory {cls}.fromJson(Map<String, dynamic> json) => {cls}(")
    for k, v in sorted(props.items()):
        lines.append(f"    {camel(k)}: {read_expr(type_for(v, required=k in required_props), k)},")
    lines.append("  );")
    lines.append("")
    for k, v in sorted(props.items()):
        lines.append(f"  final {type_for(v, required=k in required_props)} {camel(k)};")
    if props: lines.append("")
    lines.append("  Map<String, dynamic> toJson() => {")
    for k in sorted(props):
        n = camel(k)
        lines.append(f"    {json.dumps(k)}: _openApiJsonValue({n}),")
    lines.append("  };")
    lines.append("}")
    return lines


def render() -> str:
    document = json.loads(OPENAPI.read_text())
    schemas = document["components"]["schemas"]
    lines = [
        "// Generated by tools/generate_client_openapi_models.py from contracts/openapi/weave-openapi.json.",
        "// Do not edit by hand; run ./gradlew generateClientOpenApiModels.",
        "// ignore_for_file: avoid_dynamic_calls, unnecessary_cast, document_ignores",
        "",
        "Object? _openApiJsonValue(Object? value) {",
        "  if (value is List) {",
        "    return value.map(_openApiJsonValue).toList();",
        "  }",
        "  if (value is Map) {",
        "    return value.cast<String, Object?>();",
        "  }",
        "  try {",
        "    return (value as dynamic).toJson() as Object?;",
        "  } on NoSuchMethodError {",
        "    return value;",
        "  }",
        "}",
        "",
    ]
    for name in sorted(schemas):
        schema = schemas[name]
        if isinstance(schema, dict) and (schema.get("type") == "object" or "properties" in schema):
            lines.extend(emit_class(name, schema))
            lines.append("")
    return "\n".join(lines)


def formatted(value: str) -> str:
    with tempfile.TemporaryDirectory(prefix="weave-openapi-dart-") as temporary:
        path = Path(temporary) / OUT.name
        path.write_text(value)
        subprocess.run(
            ["dart", "format", str(path)],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        return path.read_text()


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail without changing files when the checked-in Dart models are stale",
    )
    args = parser.parse_args()
    generated = formatted(render())
    if args.check:
        if not OUT.is_file() or OUT.read_text() != generated:
            print(
                "Flutter OpenAPI generated models are stale. "
                "Run ./gradlew generateClientOpenApiModels and commit the result.",
                file=sys.stderr,
            )
            return 1
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(generated)
    print(f"Generated {OUT.relative_to(ROOT)}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
