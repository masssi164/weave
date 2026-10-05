import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';

enum ChatViewPhase { loading, content, empty, error, unsupported }

class ChatUiState {
  const ChatUiState._({
    required this.phase,
    this.conversations = const <ChatConversation>[],
    this.failure,
    this.staleFailure,
    this.isRefreshing = false,
  });

  const ChatUiState.loading() : this._(phase: ChatViewPhase.loading);

  const ChatUiState.content(
    List<ChatConversation> conversations, {
    ChatFailure? staleFailure,
    bool isRefreshing = false,
  }) : this._(
         phase: ChatViewPhase.content,
         conversations: conversations,
         staleFailure: staleFailure,
         isRefreshing: isRefreshing,
       );

  const ChatUiState.empty() : this._(phase: ChatViewPhase.empty);

  const ChatUiState.error(ChatFailure failure)
    : this._(phase: ChatViewPhase.error, failure: failure);

  const ChatUiState.unsupported(ChatFailure failure)
    : this._(phase: ChatViewPhase.unsupported, failure: failure);

  final ChatViewPhase phase;
  final List<ChatConversation> conversations;
  final ChatFailure? failure;
  final ChatFailure? staleFailure;
  final bool isRefreshing;
}

class ChatController extends Notifier<ChatUiState> {
  int? _sessionGeneration;

  @override
  ChatUiState build() {
    final invalidation = ref.watch(
      integrationInvalidationProvider(WorkspaceIntegration.chat),
    );
    final sessionGeneration = invalidation?.sequence ?? 0;
    if (_sessionGeneration != sessionGeneration) {
      _sessionGeneration = sessionGeneration;
      Future<void>.microtask(() => _loadConversations());
    }

    return const ChatUiState.loading();
  }

  Future<void> retry() async {
    final cachedConversations = state.conversations;
    if (cachedConversations.isEmpty) {
      state = const ChatUiState.loading();
      await _loadConversations();
      return;
    }

    state = ChatUiState.content(cachedConversations, isRefreshing: true);
    await _loadConversations(staleConversations: cachedConversations);
  }

  Future<ChatConversation> createConversation({required String title}) async {
    final conversation = await ref
        .read(chatRepositoryProvider)
        .createConversation(title: title);
    final conversations = <ChatConversation>[
      conversation,
      ...state.conversations.where((item) => item.id != conversation.id),
    ];
    state = ChatUiState.content(conversations);
    return conversation;
  }

  Future<void> _loadConversations({
    List<ChatConversation>? staleConversations,
  }) async {
    final repository = ref.read(chatRepositoryProvider);

    try {
      final conversations = await repository.loadConversations();
      state = conversations.isEmpty
          ? const ChatUiState.empty()
          : ChatUiState.content(conversations);
    } on ChatFailure catch (failure) {
      state = _stateForLoadFailure(failure, staleConversations);
    } catch (error) {
      state = _stateForLoadFailure(
        ChatFailure.unknown(
          'Unable to load conversations right now.',
          cause: error,
        ),
        staleConversations,
      );
    }
  }

  ChatUiState _stateForLoadFailure(
    ChatFailure failure,
    List<ChatConversation>? staleConversations,
  ) {
    if (staleConversations != null && staleConversations.isNotEmpty) {
      return ChatUiState.content(staleConversations, staleFailure: failure);
    }

    return _stateForFailure(failure);
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
