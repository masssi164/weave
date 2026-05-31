#!/usr/bin/env python3
"""Fixture tests for spec_contract_check.py."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest import mock

import spec_contract_check


VALID_SPEC = """---
id: WEAVE-SPEC-0001
title: Test spec
version: 0.1.0
status: implemented
domain: delivery
owner: delivery-owner
github_issue: null
supersedes: []
depends_on: []
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
---

# Feature specification: Test spec

No unresolved questions.
"""

TRACEABILITY = """spec_id: WEAVE-SPEC-0001
version: 0.1.0
status: implemented
"""

FRAMEWORK_SPEC = VALID_SPEC.replace("WEAVE-SPEC-0001", "WEAVE-SPEC-0000").replace("0001", "0000")
FRAMEWORK_TRACEABILITY = TRACEABILITY.replace("WEAVE-SPEC-0001", "WEAVE-SPEC-0000")


class SpecContractCheckTest(unittest.TestCase):
    def with_repo(self, spec_text: str, dirname: str = "0001-test-spec", traceability: str | None = TRACEABILITY) -> Path:
        tmp = Path(tempfile.mkdtemp(prefix="weave-spec-contract-test-"))
        spec_dir = tmp / "specs" / dirname
        spec_dir.mkdir(parents=True)
        (spec_dir / "spec.md").write_text(spec_text, encoding="utf-8")
        if traceability is not None:
            (spec_dir / "traceability.yaml").write_text(traceability, encoding="utf-8")
        return tmp

    def run_main(self, repo: Path) -> None:
        with mock.patch.object(spec_contract_check, "ROOT", repo), mock.patch.object(
            spec_contract_check, "SPECS_DIR", repo / "specs"
        ):
            spec_contract_check.main()

    def test_valid_implemented_spec_passes(self) -> None:
        self.run_main(self.with_repo(VALID_SPEC))

    def test_implemented_spec_with_clarification_fails(self) -> None:
        repo = self.with_repo(VALID_SPEC.replace("No unresolved questions.", "[NEEDS CLARIFICATION: decide product core]"))
        with self.assertRaises(SystemExit):
            self.run_main(repo)

    def test_draft_spec_with_clarification_passes_without_traceability(self) -> None:
        draft = VALID_SPEC.replace("status: implemented", "status: draft").replace(
            "No unresolved questions.", "[NEEDS CLARIFICATION: decide product core]"
        )
        self.run_main(self.with_repo(draft, traceability=None))

    def test_implemented_spec_with_nested_clarification_fails(self) -> None:
        repo = self.with_repo(VALID_SPEC)
        nested = repo / "specs" / "0001-test-spec" / "contracts" / "contract.md"
        nested.parent.mkdir(parents=True)
        nested.write_text("[NEEDS CLARIFICATION: hidden product question]\n", encoding="utf-8")
        with self.assertRaises(SystemExit):
            self.run_main(repo)

    def test_directory_number_must_match_spec_id(self) -> None:
        repo = self.with_repo(VALID_SPEC, dirname="0002-wrong")
        with self.assertRaises(SystemExit):
            self.run_main(repo)

    def test_duplicate_global_spec_id_fails(self) -> None:
        repo = self.with_repo(VALID_SPEC)
        duplicate_dir = repo / "specs" / "0001-duplicate-spec"
        duplicate_dir.mkdir()
        (duplicate_dir / "spec.md").write_text(VALID_SPEC, encoding="utf-8")
        (duplicate_dir / "traceability.yaml").write_text(TRACEABILITY, encoding="utf-8")
        with self.assertRaises(SystemExit):
            self.run_main(repo)

    def test_frontmatter_accepts_unindented_list_items(self) -> None:
        unindented = VALID_SPEC.replace("  - ./gradlew specContract", "- ./gradlew specContract")
        self.run_main(self.with_repo(unindented))

    def test_framework_reference_does_not_require_framework_artifacts(self) -> None:
        reference_only = VALID_SPEC.replace(
            "No unresolved questions.", "This future spec references WEAVE-SPEC-0000 for process context."
        )
        self.run_main(self.with_repo(reference_only))

    def test_framework_spec_requires_assistant_delivery_artifacts(self) -> None:
        repo = self.with_repo(FRAMEWORK_SPEC, dirname="0000-framework", traceability=FRAMEWORK_TRACEABILITY)
        with self.assertRaises(SystemExit):
            self.run_main(repo)


    def test_framework_spec_fails_with_operator_runtime_config_example(self) -> None:
        repo = self.with_repo(FRAMEWORK_SPEC, dirname="0000-framework", traceability=FRAMEWORK_TRACEABILITY)
        forbidden = repo / ".specify" / "templates" / "weave-agent-team-config.example.json5"
        forbidden.parent.mkdir(parents=True, exist_ok=True)
        forbidden.write_text("{agents:{allowAgents:['live']}}", encoding="utf-8")
        with self.assertRaises(SystemExit):
            self.run_main(repo)

    def test_framework_spec_fails_with_live_allowlist_marker(self) -> None:
        repo = self.with_repo(FRAMEWORK_SPEC, dirname="0000-framework", traceability=FRAMEWORK_TRACEABILITY)
        files = {
            ".specify/memory/constitution.md": "Repo truth over chat memory\nAssistant governance\n",
            ".specify/templates/weave-spec-template.md": "[NEEDS CLARIFICATION:\n",
            ".specify/templates/weave-plan-template.md": "Constitution check\n",
            ".specify/templates/weave-tasks-template.md": "Assistant handoff\n",
            ".specify/templates/weave-agent-briefs.md": "Optimization-Review\nCoding-Harness-Brief\nLive runtime configuration\nallowAgents\n",
            "docs/spec-driven-development.md": "Git-versioned specs are truth\nagent-team-orchestration.md\nDo not add live agent allowlists\n",
            "docs/agent-team-orchestration.md": "Material optimization\nRuntime boundary\nForbidden repo-local content\noperator-runtime JSON examples\n",
        }
        for relative, content in files.items():
            path = repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        with self.assertRaises(SystemExit):
            self.run_main(repo)

    def test_framework_spec_passes_with_required_assistant_delivery_artifacts(self) -> None:
        repo = self.with_repo(FRAMEWORK_SPEC, dirname="0000-framework", traceability=FRAMEWORK_TRACEABILITY)
        files = {
            ".specify/memory/constitution.md": "Repo truth over chat memory\nAssistant governance\n",
            ".specify/templates/weave-spec-template.md": "[NEEDS CLARIFICATION:\n",
            ".specify/templates/weave-plan-template.md": "Constitution check\n",
            ".specify/templates/weave-tasks-template.md": "Assistant handoff\n",
            ".specify/templates/weave-agent-briefs.md": "Optimization-Review\nCoding-Harness-Brief\nLive runtime configuration\n",
            "docs/spec-driven-development.md": "Git-versioned specs are truth\nagent-team-orchestration.md\nDo not add live agent allowlists\n",
            "docs/agent-team-orchestration.md": "Material optimization\nRuntime boundary\nForbidden repo-local content\noperator-runtime JSON examples\n",
        }
        for relative, content in files.items():
            path = repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        self.run_main(repo)


if __name__ == "__main__":
    unittest.main()
