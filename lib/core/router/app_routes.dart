/// Route path constants used throughout the application.
///
/// Centralising paths avoids typos and makes refactoring easier.
abstract final class AppRoutes {
  static const welcome = '/welcome';
  static const setup = '/setup';
  static const signIn = '/sign-in';
  static const chat = '/chat';
  static const chatRoomRelative = 'rooms/:roomId';
  static const files = '/files';
  static const calendar = '/calendar';
  static const deck = '/deck';
  static const settings = '/settings';

  static String chatRoom(String roomId) {
    return '$chat/rooms/${Uri.encodeComponent(roomId)}';
  }
}
