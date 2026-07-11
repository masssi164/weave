/// Route path constants used throughout the application.
///
/// Centralising paths avoids typos and makes refactoring easier.
abstract final class AppRoutes {
  static const welcome = '/welcome';
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
