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
    "MATRIX_OPENCLAW_STOCK_CHANNEL_CONTRACT",
    "MATRIX_READ_RECEIPT_CONTRACT",
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
        "client/test/features/chat/data/repositories/weave_matrix_facade_chat_repository_test.dart",
        "MATRIX_CONNECT_CONTRACT",
        "connect opens the OIDC-gated encrypted Rust session",
        "expect(cryptoSession.synchronizeValues, <bool>[true])",
        "MATRIX_SPACES_ROOMS_CONTRACT",
        "maps only Rust-projected encrypted rooms into chat entities",
        "MATRIX_MESSAGE_CONTRACT",
        "send, decrypt, and receipt stay inside the Rust Matrix core",
        "encrypted through Rust",
        "decrypted only in Rust",
        "MATRIX_E2EE_CLIENT_FAILS_CLOSED",
        "isNot(contains('M_WEAVE_E2EE_SYNC'))",
    )
    require(
        "client/lib/features/chat/data/repositories/weave_matrix_facade_chat_repository.dart",
        "class WeaveMatrixFacadeChatRepository implements ChatRepository",
        "RustMatrixCoreBridge",
        "loadEncryptedRooms",
        "loadEncryptedRoomMessages",
        "sendEncryptedText",
        "markRead",
        "descriptor",
        "configuration.serviceEndpoints.matrixHomeserverUrl.host",
        "disposePreservingCryptoState",
        "ChatMessageDeliveryState.sent",
    )
    require_absent(
        "client/lib/features/chat/data/repositories/weave_matrix_facade_chat_repository.dart",
        "package:matrix",
        "flutter_vodozemac",
        "BackendChatRepository",
        "jsonDecode(response.body)",
        "/_matrix/client/",
    )


def require_matrix_security_contract() -> None:
    require(
        "client/test/features/chat/data/repositories/rust_matrix_core_chat_security_repository_test.dart",
        "MATRIX_E2EE_STATE_CONTRACT",
        "maps SDK recovery, cross-signing, and encrypted-room state",
        "ChatDeviceVerificationState.verified",
        "ChatRoomEncryptionReadiness.ready",
        "reports recovery-required state without claiming readiness",
        "ChatSecurityBootstrapState.recoveryRequired",
        "bootstrap and SAS actions delegate to the same Rust profile",
    )
    require(
        "client/lib/features/chat/data/repositories/rust_matrix_core_chat_security_repository.dart",
        "RustMatrixCoreChatSecurityRepository",
        "loadSecurityState",
        "loadSecurityState(",
        "bootstrapRecovery",
        "recover(",
        "startVerification",
        "startSas",
        "confirmSas",
        "ChatFailure.unsupportedConfiguration",
        "M_WEAVE_E2EE_NO_OTHER_DEVICE",
        "RustMatrixCoreBridge",
    )


def require_flutter_matrix_boundary() -> None:
    require(
        "client/test/architecture/backend_facade_contract_test.dart",
        "FLUTTER_MATRIX_BOUNDARY_CONTRACT",
        "primary chat provider is wired through the Matrix Client-Server projection",
        "WeaveMatrixFacadeChatRepository",
        "isNot(contains('BackendChatRepository('))",
        "isNot(contains('matrixSessionServiceProvider'))",
    )
    require(
        "client/lib/features/chat/presentation/providers/chat_repository_provider.dart",
        "WeaveMatrixFacadeChatRepository",
        "Matrix Client-Server projection",
        "`/api/chat/**` remains a control/product facade",
        "direct Matrix SDK",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/ChatControllerTest.java",
        "chatLegacyRestDataPlaneRoutesAreRemovedInFavorOfMatrixFacade",
        "/api/chat/conversations/channel-general/messages",
        "status().isNotFound()",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/controller/MatrixClientServerProjectionControllerTest.java",
        "whoamiUsesRumaValidatedIdentityDerivedFromOidcSubject",
        "stockOpenClawMemberReceiptAndTypingCallsStayOnCanonicalChat",
        "roomLifecycleStateAndProfileProjectCanonicalChat",
        "/_matrix/client/v3/account/whoami",
        "spring-boot-resource-server",
    )
    require(
        "client/test/architecture/member_client_provider_boundary_contract_test.dart",
        "package:slack_",
        "package:microsoft_graph",
        "package:matrix",
        "Weave Matrix facade and Rust bridge",
    )
    require_absent(
        "client/lib/features/chat/data/repositories/weave_matrix_facade_chat_repository.dart",
        "Slack",
        "Teams",
        "BackendChatRepository",
    )
    require_absent("client/pubspec.yaml", "matrix:", "flutter_vodozemac")
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
        "projectMatrixJson",
    )
    require(
        "server/src/test/java/com/massimotter/weave/backend/chat/adapter/WeaveCanonicalChatAdapterTest.java",
        "repeatedTransactionReturnsSameCanonicalMessageAndSingleChange",
        "conformanceAccountsForPortableLossyAndUnsupportedChatSemantics",
        "northboundIdentifiersRemainCanonicalAndProviderNeutral",
        "readReceiptsAndTypingRemainCanonicalUserState",
    )


def require_live_matrix_evidence_pointer() -> None:
    require(
        "client/integration_test/live_stack_app_e2e_test.dart",
        "MATRIX_FACADE_RESULT",
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
