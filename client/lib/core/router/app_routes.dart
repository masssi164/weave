/// Route path constants used throughout the application.
///
/// Centralising paths avoids typos and makes refactoring easier.
abstract final class AppRoutes {
  static const organizationAccess = '/organization-access';
  static const signIn = '/sign-in';
  static const join = '/join';
  static const home = '/home';
  static const chat = '/chat';
  static const chatRoomRelative = 'rooms/:roomId';
  static const files = '/files';
  static const filesPrivateRelative = 'private';
  static const filesSpaceRelative = 'spaces/:spaceRef';
  static const filesFolderRelative = 'folders/:folderId';
  static const calendar = '/calendar';
  static const profile = '/profile';
  static const settings = '/settings';
  static const settingsPreferences = '/settings/preferences';
  static const settingsSupport = '/settings/support';
  static const workspaceHealth = '/settings/workspace-health';
  static const help = '/help';

  static String chatRoom(String roomId) {
    return '$chat/rooms/${Uri.encodeComponent(roomId)}';
  }

  static List<String> _filesLocationSegments(String? spaceRef) => [
    '',
    'files',
    if (spaceRef == null) 'private' else ...['spaces', spaceRef],
  ];

  /// The location is part of the route, never a mutable Files-wide scope mode.
  static String filesFolder(String folderId, String path, {String? spaceRef}) =>
      Uri(
        pathSegments: [
          ..._filesLocationSegments(spaceRef),
          'folders',
          folderId,
        ],
        queryParameters: {'folderPath': path},
      ).toString();

  /// A location root is a child of the Files overview, including private files.
  static String filesLocation(String path, {String? spaceRef}) {
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    return Uri(
      pathSegments: _filesLocationSegments(spaceRef),
      queryParameters: normalizedPath == '/' ? null : {'path': normalizedPath},
    ).toString();
  }
}

/// Native history metadata; the overview is not the root of any file location.
typedef FilesRouteLocation = ({String path, String? spaceRef});

/// Converts a validated app-link shape to its internal route without changing
/// query data. Domain parsing remains responsible for the handoff contract.
String? normalizedJoinRouteLocation(Uri uri) {
  // Do not normalize away data that the canonical handoff parser rejects.
  if (uri.userInfo.isNotEmpty || uri.hasFragment) return null;
  final isCustomSchemeJoin =
      uri.scheme == 'weave' && uri.host == 'join' && _isEmptyOrRootPath(uri);
  final isCustomSchemePathJoin =
      uri.scheme == 'weave' && uri.host.isEmpty && uri.path == AppRoutes.join;
  final isInAppJoin = uri.scheme.isEmpty && uri.path == AppRoutes.join;
  if (!isCustomSchemeJoin && !isCustomSchemePathJoin && !isInAppJoin) {
    return null;
  }

  final query = uri.hasQuery ? '?${uri.query}' : '';
  return '${AppRoutes.join}$query';
}

bool _isEmptyOrRootPath(Uri uri) => uri.path.isEmpty || uri.path == '/';
