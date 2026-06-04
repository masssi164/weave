#!/usr/bin/env python3
"""Validate the professional Weave E2E suite catalog.

The acceptance-contract Dart guard proves every Gherkin scenario has executable
markers. This check adds the professional structure guard: every mapped scenario
must also be deliberately classified by suite, persona, domain, execution lane,
and assertion focus.
"""

from __future__ import annotations

import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
MAPPING = ROOT / "e2e" / "scenario_mappings.json"
CATALOG = ROOT / "e2e" / "suites" / "scenario_catalog.json"
SPEC_LOCK = ROOT / "specs" / "weave-specs.lock.json"

VALID_PERSONAS = {"member", "admin", "operator", "external_guest", "weaver_user"}
VALID_LEVELS = {"live-e2e", "offline-contract"}
MIN_LIVE_RUNTIME = 1


def fail(message: str) -> None:
    print(f"e2e-structure-check: {message}", file=sys.stderr)
    sys.exit(1)


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        fail(f"missing required file {display_path(path)}")
    try:
        decoded = json.loads(path.read_text())
    except json.JSONDecodeError as exc:
        fail(f"{display_path(path)} is not valid JSON: {exc}")
    if not isinstance(decoded, dict):
        fail(f"{display_path(path)} must contain a JSON object")
    return decoded


def pinned_spec_manifest_path() -> Path:
    lock = load_json(SPEC_LOCK)
    spec_corpus = lock.get("specCorpus")
    if not isinstance(spec_corpus, dict):
        fail("specs/weave-specs.lock.json must contain specCorpus")
    local_path = spec_corpus.get("localPath")
    manifest = spec_corpus.get("manifest")
    if not isinstance(local_path, str) or not local_path:
        fail("specs/weave-specs.lock.json specCorpus.localPath must be non-empty")
    if not isinstance(manifest, str) or not manifest:
        fail("specs/weave-specs.lock.json specCorpus.manifest must be non-empty")
    return (ROOT / local_path / manifest).resolve()


def ensure_list(value: Any, name: str) -> list[Any]:
    if not isinstance(value, list):
        fail(f"{name} must be a list")
    return value


def main() -> int:
    mapping = load_json(MAPPING)
    catalog = load_json(CATALOG)
    if catalog.get("schemaVersion") != 1:
        fail("scenario_catalog.json schemaVersion must be 1")

    mapped_scenarios = ensure_list(mapping.get("scenarios"), "mapping.scenarios")
    catalog_scenarios = ensure_list(catalog.get("scenarios"), "catalog.scenarios")
    suites = ensure_list(catalog.get("suites"), "catalog.suites")
    required_domains = set(ensure_list(catalog.get("requiredDomains"), "catalog.requiredDomains"))

    if not required_domains:
        fail("catalog.requiredDomains must not be empty")

    spec_manifest = load_json(pinned_spec_manifest_path())
    manifest_domains = set()
    for entry in ensure_list(spec_manifest.get("entries"), "specManifest.entries"):
        if not isinstance(entry, dict):
            continue
        if entry.get("path", "").startswith("domains/"):
            manifest_domains.add(str(entry["path"]).split("/")[1])
    missing_spec_domains = {
        domain
        for domain in required_domains
        if domain not in {"provider-portability", "operator-release"} and domain not in manifest_domains
    }
    if missing_spec_domains:
        fail(
            "catalog.requiredDomains references domains not present in spec manifest: "
            + ", ".join(sorted(missing_spec_domains))
        )

    suite_by_id: dict[str, dict[str, Any]] = {}
    for suite in suites:
        if not isinstance(suite, dict):
            fail("each suite must be an object")
        suite_id = suite.get("id")
        if not isinstance(suite_id, str) or not suite_id:
            fail("each suite requires a non-empty id")
        if suite_id in suite_by_id:
            fail(f"duplicate suite id {suite_id}")
        if suite.get("evidenceMode") not in {"live-runtime", "offline-spec"}:
            fail(f"suite {suite_id} has invalid evidenceMode")
        if not isinstance(suite.get("executionLane"), str) or not suite["executionLane"]:
            fail(f"suite {suite_id} needs an executionLane")
        suite_by_id[suite_id] = suite

    mapping_by_tag: dict[str, dict[str, Any]] = {}
    mapping_tag_counts: Counter[str] = Counter()
    live_runtime_count = 0
    for scenario in mapped_scenarios:
        if not isinstance(scenario, dict):
            fail("each mapped scenario must be an object")
        tag = scenario.get("tag")
        if not isinstance(tag, str) or not tag.startswith("@"):
            fail("each mapped scenario needs a stable @tag")
        mapping_tag_counts[tag] += 1
        mapping_by_tag[tag] = scenario
        if scenario.get("evidenceMode") == "live-runtime":
            live_runtime_count += 1
    duplicates = [tag for tag, count in mapping_tag_counts.items() if count > 1]
    if duplicates:
        fail("duplicate mapped scenario tags: " + ", ".join(sorted(duplicates)))
    if live_runtime_count < MIN_LIVE_RUNTIME:
        fail("catalog requires at least one live-runtime scenario mapping")

    catalog_by_tag: dict[str, dict[str, Any]] = {}
    catalog_tag_counts: Counter[str] = Counter()
    domains_seen: set[str] = set()
    personas_seen: set[str] = set()
    suite_usage: Counter[str] = Counter()
    mode_by_level = {"live-e2e": "live-runtime", "offline-contract": "offline-spec"}

    for scenario in catalog_scenarios:
        if not isinstance(scenario, dict):
            fail("each catalog scenario must be an object")
        tag = scenario.get("tag")
        if not isinstance(tag, str) or not tag.startswith("@"):
            fail("each catalog scenario needs a stable @tag")
        catalog_tag_counts[tag] += 1
        catalog_by_tag[tag] = scenario

        mapped = mapping_by_tag.get(tag)
        if mapped is None:
            fail(f"catalog scenario {tag} is not present in scenario_mappings.json")

        suite_id = scenario.get("suiteId")
        if suite_id not in suite_by_id:
            fail(f"catalog scenario {tag} references unknown suite {suite_id!r}")
        suite_usage[str(suite_id)] += 1

        test_level = scenario.get("testLevel")
        if test_level not in VALID_LEVELS:
            fail(f"catalog scenario {tag} has invalid testLevel {test_level!r}")
        expected_mode = mode_by_level[str(test_level)]
        if mapped.get("evidenceMode") != expected_mode:
            fail(
                f"catalog scenario {tag} testLevel {test_level} expects {expected_mode}, "
                f"but mapping uses {mapped.get('evidenceMode')}"
            )
        suite_mode = suite_by_id[str(suite_id)]["evidenceMode"]
        if suite_mode != mapped.get("evidenceMode"):
            fail(
                f"catalog scenario {tag} suite {suite_id} uses {suite_mode}, "
                f"but mapping uses {mapped.get('evidenceMode')}"
            )

        personas = ensure_list(scenario.get("personas"), f"catalog scenario {tag}.personas")
        if not personas or any(persona not in VALID_PERSONAS for persona in personas):
            fail(f"catalog scenario {tag} personas must be within {sorted(VALID_PERSONAS)}")
        personas_seen.update(str(persona) for persona in personas)

        domains = ensure_list(scenario.get("domains"), f"catalog scenario {tag}.domains")
        if not domains:
            fail(f"catalog scenario {tag} must name at least one domain")
        unknown_domains = [domain for domain in domains if domain not in required_domains]
        if unknown_domains:
            fail(f"catalog scenario {tag} has unknown domains: {', '.join(unknown_domains)}")
        domains_seen.update(str(domain) for domain in domains)

        assertion_focus = ensure_list(
            scenario.get("assertionFocus"), f"catalog scenario {tag}.assertionFocus"
        )
        if not assertion_focus or any(not isinstance(item, str) or not item for item in assertion_focus):
            fail(f"catalog scenario {tag} needs non-empty assertionFocus entries")

    duplicate_catalog_tags = [tag for tag, count in catalog_tag_counts.items() if count > 1]
    if duplicate_catalog_tags:
        fail("duplicate catalog scenario tags: " + ", ".join(sorted(duplicate_catalog_tags)))

    missing_catalog = sorted(set(mapping_by_tag) - set(catalog_by_tag))
    if missing_catalog:
        fail("mapped scenarios missing from catalog: " + ", ".join(missing_catalog))
    extra_catalog = sorted(set(catalog_by_tag) - set(mapping_by_tag))
    if extra_catalog:
        fail("catalog scenarios missing from mapping: " + ", ".join(extra_catalog))

    unused_suites = sorted(set(suite_by_id) - set(suite_usage))
    if unused_suites:
        fail("catalog suites without scenarios: " + ", ".join(unused_suites))

    missing_domains = sorted(required_domains - domains_seen)
    if missing_domains:
        fail("required domains without scenario coverage: " + ", ".join(missing_domains))
    missing_personas = sorted(VALID_PERSONAS - personas_seen)
    if missing_personas:
        fail("required personas without scenario coverage: " + ", ".join(missing_personas))

    by_mode = Counter(str(item.get("evidenceMode")) for item in mapped_scenarios)
    by_suite = defaultdict(int)
    for scenario in catalog_scenarios:
        by_suite[str(scenario["suiteId"])] += 1

    print(
        "e2e-structure-check: ok "
        f"scenarios={len(mapped_scenarios)} "
        f"live-runtime={by_mode['live-runtime']} "
        f"offline-spec={by_mode['offline-spec']} "
        f"suites={len(suite_by_id)} "
        f"domains={len(domains_seen)} "
        f"personas={','.join(sorted(personas_seen))}"
    )
    for suite_id in sorted(by_suite):
        print(f"e2e-structure-check: suite {suite_id} scenarios={by_suite[suite_id]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
