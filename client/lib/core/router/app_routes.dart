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
  static const calendar = '/calendar';
  static const profile = '/profile';
  static const settings = '/settings';
  static const workspaceHealth = '/settings/workspace-health';
  static const help = '/help';

  static String chatRoom(String roomId) {
    return '$chat/rooms/${Uri.encodeComponent(roomId)}';
  }

  static String filesLocation(String path) {
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    if (normalizedPath == '/') {
      return files;
    }

    return Uri(
      path: files,
      queryParameters: {'path': normalizedPath},
    ).toString();
  }
}

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
