#!/usr/bin/env python3
"""Deterministic acceptance check for implemented Chat Matrix facade slices."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKERS = [
    "MATRIX_CONNECT_CONTRACT",
    "MATRIX_SPACES_ROOMS_CONTRACT",
    "MATRIX_MESSAGE_CONTRACT",
    "MATRIX_E2EE_STATE_CONTRACT",
    "FLUTTER_MATRIX_BOUNDARY_CONTRACT",
    "RUST_MATRIX_CORE_BRIDGE_CONTRACT",
]


def fail(message: str) -> None:
    print(f"chat-matrix-facade-acceptance-check: {message}", file=sys.stderr)
    sys.exit(1)


def read(path: str) -> str:
    file_path = ROOT / path
    if not file_path.exists():
        fail(f"missing required file {path}")
    return file_path.read_text(encoding="utf-8")


def require(path: str, *fragments: str) -> str:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path} is missing required fragment: {fragment}")
    return text


def require_absent(path: str, *fragments: str) -> None:
    text = read(path)
    for fragment in fragments:
        if fragment in text:
            fail(f"{path} still contains forbidden fragment: {fragment}")


def require_matrix_repository_contracts() -> None:
    require(
        "client/test/features/chat/data/repositories/matrix_chat_repository_test.dart",
        "MATRIX_CONNECT_CONTRACT",
        "connect uses the configured Matrix homeserver",
        "loads conversations from the configured Matrix homeserver",
        "MATRIX_SPACES_ROOMS_CONTRACT",
        "maps Matrix timeline message safety states into chat entities",
        "MATRIX_MESSAGE_CONTRACT",
        "sends through Matrix and reads the delivered room timeline",
        "hello through Matrix",
        "ChatMessageDeliveryState.sent",
        "isNot(contains('access_token'))",
        "isNot(contains('homeserver'))",
    )
    require(
        "client/lib/features/chat/data/repositories/matrix_chat_repository.dart",
        "class MatrixChatRepository implements ChatRepository",
        "_conversationService.loadConversations(",
        "_roomService.loadRoomTimeline(",
        "_roomService.sendMessage(",
        "_sessionService.connect(",
        "configuration.serviceEndpoints.matrixHomeserverUrl",
    )
    require(
        "client/lib/features/chat/data/services/matrix_conversation_service.dart",
        "await client.oneShotSync()",
        "client.rooms.map(_mapRoom)",
        "MatrixRoomPreviewType.encrypted",
    )
    require(
        "client/lib/features/chat/data/services/matrix_room_service.dart",
        "await room.sendTextEvent(message.trim())",
        "await client.oneShotSync()",
        "room.getTimeline(limit: 50)",
        "MatrixMessageContentType.encrypted",
    )


def require_matrix_security_contract() -> None:
    require(
        "client/test/features/chat/data/repositories/matrix_chat_security_repository_test.dart",
        "MATRIX_E2EE_STATE_CONTRACT",
        "maps the Matrix security snapshot into chat-owned models",
        "MatrixDeviceVerificationState.unverified",
        "MatrixRoomEncryptionReadiness.encryptedRoomsNeedAttention",
        "ChatRoomEncryptionReadiness.encryptedRoomsNeedAttention",
        "ChatVerificationPhase.compareSas",
    )
    require(
        "client/lib/features/chat/data/repositories/matrix_chat_security_repository.dart",
        "MatrixChatSecurityRepository",
        "loadSecurityState",
        "ChatDeviceVerificationState",
        "ChatRoomEncryptionReadiness",
    )


def require_flutter_matrix_boundary() -> None:
    require(
        "client/test/architecture/backend_facade_contract_test.dart",
        "FLUTTER_MATRIX_BOUNDARY_CONTRACT",
        "primary chat provider is wired through the Matrix Client-Server projection",
        "MatrixChatRepository",
        "isNot(contains('BackendChatRepository('))",
        "matrixSessionServiceProvider",
    )
    require(
        "client/lib/features/chat/presentation/providers/chat_repository_provider.dart",
        "MatrixChatRepository",
        "Matrix Client-Server projection",
        "`/api/chat/**` remains a transitional/control facade",
        "normal message sync/send does not",
    )
    require(
        "client/test/architecture/member_client_provider_boundary_contract_test.dart",
        "package:slack_",
        "package:microsoft_graph",
        "allowedMatrixImportFiles",
    )
    require_absent(
        "client/lib/features/chat/data/repositories/matrix_chat_repository.dart",
        "Slack",
        "Teams",
        "BackendChatRepository",
    )
    require(
        "client/test/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge_test.dart",
        "RUST_MATRIX_CORE_BRIDGE_CONTRACT",
        "matrix-client-server-facade",
        "spring-boot-resource-server",
        "flutter-rust-bridge",
        "ruma-serde-serde_json-thiserror-tracing",
    )
    require(
        "client/lib/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart",
        "RustMatrixCoreBridge",
        "northboundHomeserverDependency",
        "flutterBridgeBoundary",
        "supportedMatrixVersions",
    )


def require_live_matrix_evidence_pointer() -> None:
    require(
        "client/integration_test/live_stack_app_e2e_test.dart",
        "MATRIX_LIVE_HOMESERVER_RESULT",
        "chatRepository.connect()",
        "chatRepository.loadConversations()",
        "chatRepository.sendMessage(",
        "chatRepository.loadRoomTimeline(roomId)",
        "matchedMessages=${deliveredMessage.length}",
    )


def main() -> int:
    require_matrix_repository_contracts()
    require_matrix_security_contract()
    require_flutter_matrix_boundary()
    require_live_matrix_evidence_pointer()
    for marker in MARKERS:
        print(marker)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
