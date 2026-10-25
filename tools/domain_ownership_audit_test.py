"""Fixture tests for the read-only Server/Domain ownership inventory."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from tools.domain_ownership_audit import audit, expected_owner


class DomainOwnershipAuditTest(unittest.TestCase):
    def test_shared_session_storage_and_future_features_have_distinct_owners(self):
        self.assertEqual(expected_owner("com.x.backend.persistence.jpa.matrix", "MatrixRevokedSessionJpaEntity.java"), "core")
        self.assertEqual(expected_owner("com.x.backend.persistence.jpa.matrix", "MatrixRoomEntity.java"), "chat")
        self.assertEqual(expected_owner("com.x.backend.agentruntime.domain", "RuntimeCell.java"), "agentruntime")
        self.assertEqual(expected_owner("com.x.backend.controller", "CalendarController.java"), "calendar")
        self.assertEqual(expected_owner("com.x.backend.config", "SecurityConfig.java"), "review")

    def test_inventory_flags_legacy_and_core_owners_without_mutations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = [
                ("server/core/src/main/java/com/x/backend/boards/Board.java",
                 "package com.x.backend.boards;\npublic record Board() {}\n"),
                ("server/core/persistence/src/main/java/com/x/backend/persistence/jpa/matrix/MatrixRevokedSessionJpaEntity.java",
                 "package com.x.backend.persistence.jpa.matrix;\n@jakarta.persistence.Entity\npublic class MatrixRevokedSessionJpaEntity {}\n"),
                ("server/domains/files/src/main/java/com/x/backend/files/domain/FilesEntry.java",
                 "package com.x.backend.files.domain;\npublic class FilesEntry {}\n"),
                ("weave-application-core/src/main/java/com/x/backend/boards/Board.java",
                 "package com.x.backend.boards;\npublic record Board() {}\n"),
            ]
            for name, source in paths:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(source, encoding="utf-8")
            result = audit(root)
            self.assertEqual(result["javaSources"], 4)
            self.assertEqual(result["bySuggestedOwner"], {"boards": 2, "core": 1, "files": 1})
            self.assertEqual(result["possibleWrongOwnerPaths"], [paths[0][0]])
            self.assertEqual(result["legacyProjectsPresent"], ["weave-application-core"])
            self.assertIn("com.x.backend.boards.Board", result["possibleDuplicateTypes"])
            self.assertEqual(result["entityCandidates"],
                             [{"path": paths[1][0], "suggestedOwner": "core"}])


if __name__ == "__main__":
    unittest.main()
