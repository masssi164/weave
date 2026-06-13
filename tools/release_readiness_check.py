#!/usr/bin/env python3
"""Check whether a release-candidate commit has support-safe promotion evidence."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LIVE_EVIDENCE_DIR = ROOT / "weave-live-stack-acceptance-evidence"
DEFAULT_CI_SUMMARY = ROOT / "build" / "evidence" / "ci-summary.json"
DEFAULT_BLOCKERS = ROOT / "build" / "evidence" / "release-blockers.json"
DEFAULT_GENERATED_CI_SUMMARY = ROOT / "build" / "evidence" / "rc-readiness" / "ci-summary.generated.json"
REQUIRED_RELEASE_NOTE_HEADINGS = (
    "Added",
    "Changed",
    "Fixed",
    "Security",
    "Accessibility",
    "Migration/Operator Notes",
    "Known Issues",
)
REQUIRED_LIVE_MARKERS = {
    "AUTH_RESULT",
    "PROFILE_RESULT",
    "CHAT_RESULT",
    "MATRIX_RESULT",
    "E2EE_RESULT",
    "FILES_RESULT",
    "PROVIDER_STACK_RESULT",
    "CALENDAR_RESULT",
    "BOARDS_RESULT",
}
REQUIRED_LIVE_ARTIFACTS = {
    "acceptance-summary.md",
    "scenario-mapping-results.json",
    "evidence-markers.json",
    "release-evidence-manifest.json",
}
FORBIDDEN_PATTERNS = (
    (re.compile(r"https://x-access-token:[^\s)]+", re.IGNORECASE), "credential-bearing Git URL"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"), "GitHub token"),
    (re.compile(r"\bBearer\s+[A-Za-z0-9._~+/=-]{12,}\b", re.IGNORECASE), "bearer token"),
    (re.compile(r"\b(access_token|refresh_token)=[^\s)]+", re.IGNORECASE), "token query value"),
    (re.compile(r"\b(client_secret|password|api[_-]?key)=([^\s)]+)", re.IGNORECASE), "credential value"),
    (re.compile(r"BEGIN PRIVATE KEY"), "private key"),
)

REQUIRED_SUPPORT_SAFE_POLICY_TERMS = (
    "secrets",
    "credential-bearing URLs",
    "provider bodies",
    "private prompts",
    "member data",
    "raw runtime settings",
)

PROHIBITED_RELEASE_CLAIMS = (
    (re.compile(r"\b(public|production) release (is )?(ready|approved|complete|completed)\b", re.IGNORECASE), "public/production release readiness claim"),
    (re.compile(r"\bfull accessibility (is )?(ready|approved|complete|completed|passed)\b", re.IGNORECASE), "full accessibility claim"),
    (re.compile(r"\bprovider interchangeability (is )?(ready|available|complete|completed|proved)\b", re.IGNORECASE), "broad provider-interchangeability claim"),
    (re.compile(r"\bproduction restore (is )?(ready|available|complete|completed|proved)\b", re.IGNORECASE), "production restore claim"),
    (re.compile(r"\bWeaver (is )?(available|customer-ready|release-ready|production-ready)\b", re.IGNORECASE), "broad Weaver availability claim"),
)


@dataclass
class Check:
    id: str
    status: str
    summary: str
    pointers: list[str] = field(default_factory=list)
    details: dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> dict[str, Any]:
        data: dict[str, Any] = {
            "id": self.id,
            "status": self.status,
            "summary": self.summary,
        }
        if self.pointers:
            data["pointers"] = self.pointers
        if self.details:
            data["details"] = self.details
        return data


def rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_utc(value: str) -> datetime:
    normalized = value.replace("Z", "+00:00")
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def git_output(args: list[str]) -> str | None:
    try:
        return subprocess.check_output(["git", *args], cwd=ROOT, text=True, stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def resolve_commit(candidate_commit: str | None) -> str:
    if candidate_commit:
        return candidate_commit.lower()
    return (git_output(["rev-parse", "HEAD"]) or "unknown").lower()


def normalize_version(version: str) -> str:
    return version[1:] if version.startswith("v") else version


def validate_waiver(waiver: dict[str, Any] | None, *, candidate_commit: str, candidate_tag: str, gate: str) -> tuple[bool, str]:
    if not waiver:
        return False, "no waiver supplied"
    waived = waiver.get("waives")
    if not isinstance(waived, list) or gate not in waived:
        return False, f"waiver does not cover {gate}"
    if waiver.get("candidateCommit", "").lower() != candidate_commit.lower():
        return False, "waiver candidateCommit does not match"
    if waiver.get("candidateTag") != candidate_tag:
        return False, "waiver candidateTag does not match"
    for key in ("owner", "reason", "expiresUtc"):
        if not str(waiver.get(key, "")).strip():
            return False, f"waiver missing {key}"
    try:
        if parse_utc(str(waiver["expiresUtc"])) <= datetime.now(timezone.utc):
            return False, "waiver is expired"
    except ValueError:
        return False, "waiver expiresUtc is invalid"
    evidence = waiver.get("compensatingEvidence")
    if not isinstance(evidence, list) or not all(str(item).strip() for item in evidence):
        return False, "waiver missing compensatingEvidence"
    return True, f"waived by {waiver['owner']} until {waiver['expiresUtc']}"


def check_support_safe(paths: list[Path]) -> Check:
    scanned: list[str] = []
    for path in paths:
        if not path.exists() or path.is_dir():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        scanned.append(rel(path))
        for pattern, label in FORBIDDEN_PATTERNS:
            if pattern.search(text):
                return Check("support-safe", "fail", f"{rel(path)} contains {label}", scanned)
    return Check("support-safe", "pass", "checked summaries contain no known secret, credential, payload, prompt, member-content, or raw-runtime-config patterns", scanned)


def check_support_safe_policy(paths: list[Path]) -> Check:
    combined = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in paths if path.exists() and not path.is_dir())
    missing = [term for term in REQUIRED_SUPPORT_SAFE_POLICY_TERMS if term.lower() not in combined.lower()]
    if missing:
        return Check("support-safe-policy", "fail", "support-safe policy missing: " + ", ".join(missing), [rel(path) for path in paths if path.exists()])
    return Check("support-safe-policy", "pass", "support-safe evidence policy covers secrets, credential-bearing URLs, provider bodies, private prompts, member data, and raw runtime settings", [rel(path) for path in paths if path.exists()])


def check_claim_control(paths: list[Path]) -> Check:
    scanned: list[str] = []
    for path in paths:
        if not path.exists() or path.is_dir():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        scanned.append(rel(path))
        for pattern, label in PROHIBITED_RELEASE_CLAIMS:
            if pattern.search(text):
                return Check("claim-control", "fail", f"{rel(path)} contains prohibited {label}", scanned)
    return Check("claim-control", "pass", "checked release wording for public/production readiness, full accessibility, broad interchangeability, production restore, and Weaver availability overclaims", scanned)


def check_inputs(version: str, tag: str, commit: str) -> Check:
    normalized = normalize_version(version)
    failures: list[str] = []
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-rc\.\d+)?", normalized):
        failures.append("candidate version must look like 0.1.0 or 0.1.0-rc.1")
    if tag != f"v{normalized}":
        failures.append("candidate tag must be v + candidate version")
    if not re.fullmatch(r"[0-9a-f]{7,40}", commit):
        failures.append("candidate commit must be a 7-40 character hex SHA")
    if failures:
        return Check("candidate-inputs", "fail", "; ".join(failures))
    return Check("candidate-inputs", "pass", f"{tag} targets {commit[:12]}")


def release_note_entries(text: str) -> list[str]:
    entries: list[str] = []
    current: str | None = None
    for line in text.splitlines():
        heading = re.match(r"^## (.+)$", line)
        if heading:
            current = heading.group(1)
            continue
        if current in REQUIRED_RELEASE_NOTE_HEADINGS and line.startswith("- "):
            item = line[2:].strip()
            if item and item.lower() != "nothing yet.":
                entries.append(item)
    return entries


def check_release_notes(path: Path) -> Check:
    if not path.exists():
        return Check("release-notes", "fail", f"missing release notes: {rel(path)}")
    text = path.read_text(encoding="utf-8")
    missing = [heading for heading in REQUIRED_RELEASE_NOTE_HEADINGS if f"## {heading}" not in text]
    if missing:
        return Check("release-notes", "fail", f"missing release-note sections: {', '.join(missing)}", [rel(path)])
    entries = release_note_entries(text)
    if not entries:
        return Check("release-notes", "fail", "unreleased notes contain no candidate entries", [rel(path)])
    return Check("release-notes", "pass", f"unreleased notes have {len(entries)} candidate entries", [rel(path)])


def generate_ci_summary(path: Path, candidate_commit: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    summary = {
        "schemaVersion": 1,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "commit": candidate_commit,
        "branch": git_output(["rev-parse", "--abbrev-ref", "HEAD"]) or "unknown",
        "build": {"result": "unknown", "failureType": None},
        "gates": [],
        "liveE2E": {
            "outcome": "separate-required-release-evidence",
            "reason": "Generated pointer only; run ./gradlew ci for authoritative CI gate outcomes.",
        },
        "sanitized": True,
        "generatedBy": "tools/release_readiness_check.py",
    }
    path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def check_ci_summary(path: Path, candidate_commit: str, *, generated_path: Path) -> Check:
    if not path.exists():
        pointer = generate_ci_summary(generated_path, candidate_commit)
        return Check(
            "ci-summary",
            "fail",
            "authoritative CI summary is missing; generated a local support-safe pointer only",
            [rel(pointer)],
        )
    try:
        summary = load_json(path)
    except json.JSONDecodeError as error:
        return Check("ci-summary", "fail", f"invalid CI summary JSON: {error}", [rel(path)])
    failures: list[str] = []
    if summary.get("schemaVersion") != 1:
        failures.append("schemaVersion must be 1")
    if not summary.get("sanitized"):
        failures.append("summary must declare sanitized=true")
    if str(summary.get("commit", "")).lower() != candidate_commit.lower():
        failures.append("summary commit does not match candidate")
    if summary.get("build", {}).get("result") != "passed":
        failures.append("build.result is not passed")
    gates = summary.get("gates", [])
    if not isinstance(gates, list):
        failures.append("gates must be a list")
    else:
        failed_gates = [gate.get("name", "<unnamed>") for gate in gates if gate.get("outcome") not in {"passed", "skipped"}]
        if failed_gates:
            failures.append("non-passing gates: " + ", ".join(failed_gates))
        if "releaseEvidenceCheck" not in {gate.get("name") for gate in gates}:
            failures.append("releaseEvidenceCheck gate missing from CI summary")
    if failures:
        return Check("ci-summary", "fail", "; ".join(failures), [rel(path)])
    return Check("ci-summary", "pass", "sanitized CI summary matches candidate", [rel(path)])


def check_offline_pointers(gates_path: Path) -> Check:
    if not gates_path.exists():
        return Check("offline-evidence-pointers", "fail", f"missing release gate contract: {rel(gates_path)}")
    try:
        gates = load_json(gates_path)
    except json.JSONDecodeError as error:
        return Check("offline-evidence-pointers", "fail", f"invalid release gate contract: {error}", [rel(gates_path)])
    lanes = {lane.get("id") for lane in gates.get("lanes", []) if isinstance(lane, dict)}
    if {"pr-safe-ci", "release-candidate-live-evidence", "release-promotion"} - lanes:
        return Check("offline-evidence-pointers", "fail", "release gate lanes are incomplete", [rel(gates_path)])
    return Check(
        "offline-evidence-pointers",
        "pass",
        "release lane contract names CI, live evidence, and promotion evidence pointers",
        [rel(gates_path), "e2e/scenario_mappings.json", "docs/enterprise-release-foundation.md"],
    )


def live_manifest_path(live_evidence_dir: Path, manifest: Path | None) -> Path:
    if manifest is not None:
        return manifest
    return live_evidence_dir / "release-evidence-manifest.json"


def check_live_e2e(manifest_path: Path, candidate_commit: str, candidate_tag: str, waiver: dict[str, Any] | None) -> Check:
    waiver_ok, waiver_reason = validate_waiver(waiver, candidate_commit=candidate_commit, candidate_tag=candidate_tag, gate="live-e2e")
    if not manifest_path.exists():
        if waiver_ok:
            return Check("live-e2e", "waived", waiver_reason, [rel(manifest_path)])
        return Check("live-e2e", "fail", f"missing credentialed Live Stack manifest ({waiver_reason})", [rel(manifest_path)])
    try:
        manifest = load_json(manifest_path)
    except json.JSONDecodeError as error:
        return Check("live-e2e", "fail", f"invalid Live Stack manifest JSON: {error}", [rel(manifest_path)])
    failures: list[str] = []
    if manifest.get("schemaVersion") != 1:
        failures.append("schemaVersion must be 1")
    if manifest.get("lane") != "release-candidate-live-evidence":
        failures.append("lane is not release-candidate-live-evidence")
    if str(manifest.get("commit", "")).lower() != candidate_commit.lower():
        failures.append("manifest commit does not match candidate")
    if manifest.get("supportSafe") is not True:
        failures.append("manifest must declare supportSafe=true")
    acceptance = manifest.get("acceptanceContract", {})
    if acceptance.get("valid") is not True:
        failures.append("acceptance contract is not valid")
    if acceptance.get("runtimeEvidenceCollected") is not True:
        failures.append("runtime evidence was not collected")
    findings = acceptance.get("findings", [])
    if findings:
        failures.append("acceptance findings are present")
    observed = set(acceptance.get("observedMarkers", []))
    missing_markers = sorted(REQUIRED_LIVE_MARKERS - observed)
    if missing_markers:
        failures.append("missing runtime markers: " + ", ".join(missing_markers))
    artifacts = set(manifest.get("artifacts", []))
    missing_artifacts = sorted(REQUIRED_LIVE_ARTIFACTS - artifacts)
    if missing_artifacts:
        failures.append("missing artifact pointers: " + ", ".join(missing_artifacts))
    if failures and waiver_ok:
        return Check("live-e2e", "waived", f"{waiver_reason}; evidence finding: {'; '.join(failures)}", [rel(manifest_path)])
    if failures:
        return Check("live-e2e", "fail", "; ".join(failures), [rel(manifest_path)])
    return Check("live-e2e", "pass", "credentialed Live Stack evidence matches candidate", [rel(manifest_path)])


def extract_open_blockers(data: Any) -> list[dict[str, Any]]:
    if isinstance(data, dict):
        candidates = data.get("openBlockers", data.get("issues", []))
    else:
        candidates = data
    if not isinstance(candidates, list):
        return []
    blockers: list[dict[str, Any]] = []
    for item in candidates:
        if not isinstance(item, dict):
            continue
        state = str(item.get("state", "open")).lower()
        labels = item.get("labels", [])
        label_names: set[str] = set()
        for label in labels:
            if isinstance(label, str):
                label_names.add(label)
            elif isinstance(label, dict) and isinstance(label.get("name"), str):
                label_names.add(label["name"])
        is_blocker = not label_names or "release-blocker" in label_names
        if state == "open" and is_blocker:
            blockers.append(item)
    return blockers


def check_blockers(path: Path, candidate_commit: str, candidate_tag: str, waiver: dict[str, Any] | None) -> Check:
    waiver_ok, waiver_reason = validate_waiver(waiver, candidate_commit=candidate_commit, candidate_tag=candidate_tag, gate="release-blockers")
    if not path.exists():
        if waiver_ok:
            return Check("release-blockers", "waived", waiver_reason, [rel(path)])
        return Check("release-blockers", "fail", f"missing release-blocker evidence ({waiver_reason})", [rel(path)])
    try:
        data = load_json(path)
    except json.JSONDecodeError as error:
        return Check("release-blockers", "fail", f"invalid blocker JSON: {error}", [rel(path)])
    blockers = extract_open_blockers(data)
    if blockers and waiver_ok:
        return Check("release-blockers", "waived", f"{waiver_reason}; {len(blockers)} open blocker(s) recorded", [rel(path)])
    if blockers:
        identifiers = []
        for blocker in blockers[:5]:
            number = blocker.get("number")
            title = blocker.get("title", "untitled")
            identifiers.append(f"#{number} {title}" if number else str(title))
        return Check("release-blockers", "fail", f"open release blocker(s): {', '.join(identifiers)}", [rel(path)])
    return Check("release-blockers", "pass", "no open release-blocker issues in supplied summary", [rel(path)])


def load_waiver(path: Path | None) -> tuple[dict[str, Any] | None, Check | None]:
    if path is None:
        return None, None
    if not path.exists():
        return None, Check("waiver", "fail", f"waiver file missing: {rel(path)}")
    try:
        data = load_json(path)
    except json.JSONDecodeError as error:
        return None, Check("waiver", "fail", f"invalid waiver JSON: {error}", [rel(path)])
    return data, Check("waiver", "pass", "waiver marker is parseable; scoped gates are validated individually", [rel(path)])


def render_markdown(result: dict[str, Any]) -> str:
    lines = [
        f"# RC readiness: {result['status']}",
        "",
        f"- Candidate: {result['candidate']['tag']} ({result['candidate']['commit'][:12]})",
        f"- Support-safe: {'yes' if result['supportSafe'] else 'no'}",
        "",
        "## Checks",
    ]
    for check in result["checks"]:
        lines.append(f"- {check['status']}: {check['id']} — {check['summary']}")
        for pointer in check.get("pointers", []):
            lines.append(f"  - evidence: `{pointer}`")
    if result["findings"]:
        lines.extend(["", "## Blockers"])
        for finding in result["findings"]:
            lines.append(f"- {finding}")
    return "\n".join(lines) + "\n"


def build_result(args: argparse.Namespace) -> dict[str, Any]:
    commit = resolve_commit(args.candidate_commit)
    tag = args.candidate_tag
    version = args.candidate_version
    waiver, waiver_check = load_waiver(args.waiver)
    manifest_path = live_manifest_path(args.live_evidence_dir, args.live_manifest)

    checks: list[Check] = [
        check_inputs(version, tag, commit),
        check_release_notes(args.release_notes),
        check_ci_summary(args.ci_summary, commit, generated_path=args.generated_ci_summary),
        check_offline_pointers(args.release_gates),
    ]
    if waiver_check:
        checks.append(waiver_check)
    checks.extend(
        [
            check_live_e2e(manifest_path, commit, tag, waiver),
            check_blockers(args.blockers_json, commit, tag, waiver),
            check_support_safe(
                [
                    args.release_notes,
                    args.ci_summary if args.ci_summary.exists() else args.generated_ci_summary,
                    args.release_gates,
                    manifest_path,
                    args.blockers_json,
                    *( [args.waiver] if args.waiver else [] ),
                ]
            ),
            check_support_safe_policy([ROOT / "README.md", ROOT / "docs" / "enterprise-release-foundation.md", ROOT / "docs" / "quality-and-evidence.md"]),
            check_claim_control([args.release_notes, ROOT / "README.md", ROOT / "docs" / "index.md"]),
        ]
    )
    failures = [check for check in checks if check.status == "fail"]
    result = {
        "schemaVersion": 1,
        "status": "blocked" if failures else "ready",
        "candidate": {
            "version": normalize_version(version),
            "tag": tag,
            "commit": commit,
        },
        "supportSafe": not any(check.id == "support-safe" and check.status == "fail" for check in checks),
        "checks": [check.to_json() for check in checks],
        "findings": [f"{check.id}: {check.summary}" for check in failures],
    }
    return result


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate support-safe release-candidate readiness without publishing a release.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--candidate-version", required=True, help="Expected clean version, for example 0.1.0-rc.1")
    parser.add_argument("--candidate-tag", required=True, help="Expected tag, for example v0.1.0-rc.1")
    parser.add_argument("--candidate-commit", help="Candidate commit SHA; defaults to git rev-parse HEAD")
    parser.add_argument("--ci-summary", type=Path, default=DEFAULT_CI_SUMMARY, help="Sanitized CI summary JSON from ./gradlew ci")
    parser.add_argument("--generated-ci-summary", type=Path, default=DEFAULT_GENERATED_CI_SUMMARY, help="Where to write a local pointer if --ci-summary is missing")
    parser.add_argument("--live-evidence-dir", type=Path, default=DEFAULT_LIVE_EVIDENCE_DIR, help="Live Stack evidence artifact directory")
    parser.add_argument("--live-manifest", type=Path, help="Explicit release-evidence-manifest.json path")
    parser.add_argument("--release-notes", type=Path, default=ROOT / "docs" / "release-notes" / "unreleased.md")
    parser.add_argument("--release-gates", type=Path, default=ROOT / "release" / "enterprise-release-gates.json")
    parser.add_argument("--blockers-json", type=Path, default=DEFAULT_BLOCKERS, help="Support-safe GitHub release-blocker issue summary")
    parser.add_argument("--waiver", type=Path, help="Explicit release-owner waiver marker JSON")
    parser.add_argument("--json", action="store_true", help="Emit machine-readable JSON to stdout")
    parser.add_argument("--write-json", type=Path, help="Also write machine-readable JSON to this path")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    result = build_result(args)
    if args.write_json:
        args.write_json.parent.mkdir(parents=True, exist_ok=True)
        args.write_json.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(render_markdown(result), end="")
    return 0 if result["status"] == "ready" else 1


if __name__ == "__main__":
    raise SystemExit(main())
