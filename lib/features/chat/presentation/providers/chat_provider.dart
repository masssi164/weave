import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';

enum ChatViewPhase { loading, connecting, content, empty, error, unsupported }

class ChatUiState {
  const ChatUiState._({
    required this.phase,
    this.conversations = const <ChatConversation>[],
    this.failure,
  });

  const ChatUiState.loading() : this._(phase: ChatViewPhase.loading);

  const ChatUiState.connecting() : this._(phase: ChatViewPhase.connecting);

  const ChatUiState.content(List<ChatConversation> conversations)
    : this._(phase: ChatViewPhase.content, conversations: conversations);

  const ChatUiState.empty() : this._(phase: ChatViewPhase.empty);

  const ChatUiState.error(ChatFailure failure)
    : this._(phase: ChatViewPhase.error, failure: failure);

  const ChatUiState.unsupported(ChatFailure failure)
    : this._(phase: ChatViewPhase.unsupported, failure: failure);

  final ChatViewPhase phase;
  final List<ChatConversation> conversations;
  final ChatFailure? failure;
}

class ChatController extends Notifier<ChatUiState> {
  int? _sessionGeneration;
  bool _autoConnectAttempted = false;

  @override
  ChatUiState build() {
    final sessionGeneration = ref.watch(matrixSessionInvalidationProvider);
    if (_sessionGeneration != sessionGeneration) {
      _sessionGeneration = sessionGeneration;
      _autoConnectAttempted = false;
      Future<void>.microtask(_loadInitial);
    }

    return const ChatUiState.loading();
  }

  Future<void> retry() async {
    state = const ChatUiState.loading();
    await _loadConversations(allowAutoConnect: false);
  }

  Future<void> connect() async {
    if (state.phase == ChatViewPhase.connecting) {
      return;
    }

    state = const ChatUiState.connecting();

    try {
      await ref.read(chatRepositoryProvider).connect();
      await _loadConversations(allowAutoConnect: false);
    } on ChatFailure catch (failure) {
      state = _stateForFailure(failure);
    } catch (error) {
      state = ChatUiState.error(
        ChatFailure.unknown(
          'Unable to connect to Matrix right now.',
          cause: error,
        ),
      );
    }
  }

  Future<void> _loadInitial() async {
    await _loadConversations(allowAutoConnect: true);
  }

  Future<void> _loadConversations({required bool allowAutoConnect}) async {
    final repository = ref.read(chatRepositoryProvider);

    try {
      final conversations = await repository.loadConversations();
      state = conversations.isEmpty
          ? const ChatUiState.empty()
          : ChatUiState.content(conversations);
    } on ChatFailure catch (failure) {
      if (failure.type == ChatFailureType.sessionRequired &&
          allowAutoConnect &&
          !_autoConnectAttempted) {
        _autoConnectAttempted = true;
        await connect();
        return;
      }

      state = _stateForFailure(failure);
    } catch (error) {
      state = ChatUiState.error(
        ChatFailure.unknown(
          'Unable to load conversations right now.',
          cause: error,
        ),
      );
    }
  }

  ChatUiState _stateForFailure(ChatFailure failure) {
    return switch (failure.type) {
      ChatFailureType.unsupportedConfiguration ||
      ChatFailureType.unsupportedPlatform => ChatUiState.unsupported(failure),
      _ => ChatUiState.error(failure),
    };
  }
}

final chatProvider = NotifierProvider<ChatController, ChatUiState>(
  ChatController.new,
);
