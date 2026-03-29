import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/chat/models/chat_message.dart';

part 'chat_provider.g.dart';

/// Manages the list of chat messages.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Matrix SDK calls
@riverpod
class ChatNotifier extends _$ChatNotifier {
  @override
  Future<List<ChatMessage>> build() async => [];
}
