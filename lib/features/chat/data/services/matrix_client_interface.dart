const matrixOidcClientName = 'Weave';
const matrixOidcClientUri = 'https://github.com/masssi164/weave';
const matrixOidcRedirectScheme = 'com.massimotter.weave.matrix';
const matrixOidcRedirectUri = '$matrixOidcRedirectScheme:/oauthredirect';

enum MatrixRoomPreviewType { none, text, encrypted, unsupported }

class MatrixRoomSnapshot {
  const MatrixRoomSnapshot({
    required this.id,
    required this.title,
    required this.previewType,
    required this.unreadCount,
    required this.isInvite,
    required this.isDirectMessage,
    this.previewText,
    this.lastActivityAt,
  });

  final String id;
  final String title;
  final MatrixRoomPreviewType previewType;
  final String? previewText;
  final DateTime? lastActivityAt;
  final int unreadCount;
  final bool isInvite;
  final bool isDirectMessage;
}

abstract interface class MatrixClient {
  Future<List<MatrixRoomSnapshot>> loadConversations({required Uri homeserver});

  Future<void> connect({required Uri homeserver});

  Future<void> signOut();

  Future<void> clearSession();
}
