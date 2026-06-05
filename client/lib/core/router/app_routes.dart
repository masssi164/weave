/// Route path constants used throughout the application.
///
/// Centralising paths avoids typos and makes refactoring easier.
abstract final class AppRoutes {
  static const welcome = '/welcome';
  static const setup = '/setup';
  static const signIn = '/sign-in';
  static const join = '/join';
  static const firstRun = '/first-run';
  static const chat = '/chat';
  static const chatRoomRelative = 'rooms/:roomId';
  static const files = '/files';
  static const calendar = '/calendar';
  static const deck = '/deck';
  static const settings = '/settings';
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
