package com.massimotter.weave.backend.boards.local;

import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import java.time.Instant;
import java.util.Set;

/**
 * Planner-like placeholder adapter used to prove the Weave Boards contract is
 * provider-neutral. It intentionally uses the same repository contract as the
 * OpenProject adapter while keeping provider writes local and guarded by the
 * service-layer RBAC/audit path.
 */
public class PlaceholderBoardsRepository extends LocalWorkspaceBoardsRepository {

    public PlaceholderBoardsRepository() {
        super(Instant.parse("2026-05-30T10:00:00Z"));
    }

    @Override
    public BoardProviderCapabilities capabilities() {
        return new BoardProviderCapabilities(
                ProviderKind.PLACEHOLDER,
                true,
                Set.of(
                        BoardCapability.ACCESSIBLE_NON_DRAG_MOVES,
                        BoardCapability.STATUS_UPDATES,
                        BoardCapability.DECISION_LINKS,
                        BoardCapability.COMMENTS,
                        BoardCapability.ATTACHMENTS),
                Set.of(
                        BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                        BoardCapability.WEBHOOK_EVENTS,
                        BoardCapability.INCREMENTAL_SYNC,
                        BoardCapability.CHECKLISTS,
                        BoardCapability.CUSTOM_FIELDS),
                "Placeholder Boards adapter: contract-parity fixture for board/list/task/status/comments/attachments without member-facing provider identity or raw provider payloads.");
    }
}
