import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/presentation/providers/archived_message_store_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ChatRoomScreen extends ConsumerStatefulWidget {
  const ChatRoomScreen({super.key, required this.conversation});

  final ChatConversation conversation;

  @override
  ConsumerState<ChatRoomScreen> createState() => _ChatRoomScreenState();
}

class _ChatRoomScreenState extends ConsumerState<ChatRoomScreen> {
  final _composerController = TextEditingController();
  ChatRoomTimeline? _timeline;
  ChatFailure? _failure;
  Set<String> _archivedMessageIds = <String>{};
  bool _loading = true;
  bool _sending = false;
  bool _archiving = false;
  bool _showingArchivedMessages = false;
  _PendingOutgoingMessage? _pendingMessage;
  Timer? _draftSaveDebounce;
  bool _isRestoringDraft = false;
  bool _draftRestored = false;

  @override
  void initState() {
    super.initState();
    _composerController.addListener(_onComposerChanged);
    Future<void>.microtask(() async {
      await _restoreDraft();
      await _loadTimeline();
    });
  }

  @override
  void dispose() {
    _draftSaveDebounce?.cancel();
    _composerController
      ..removeListener(_onComposerChanged)
      ..dispose();
    super.dispose();
  }

  Future<void> _restoreDraft() async {
    _isRestoringDraft = true;
    try {
      final draft = await ref
          .read(preferencesStoreProvider)
          .getString(_draftKey);
      if (!mounted || draft == null || draft.trim().isEmpty) {
        return;
      }
      _composerController.text = draft;
      setState(() {
        _draftRestored = true;
      });
    } finally {
      _isRestoringDraft = false;
    }
  }

  void _onComposerChanged() {
    if (_isRestoringDraft) {
      return;
    }
    _draftSaveDebounce?.cancel();
    _draftSaveDebounce = Timer(const Duration(milliseconds: 300), () async {
      final text = _composerController.text;
      final store = ref.read(preferencesStoreProvider);
      if (text.trim().isEmpty) {
        await store.remove(_draftKey);
      } else {
        await store.setString(_draftKey, text);
      }
    });
  }

  String get _draftKey =>
      'chat.roomDraft.v1.${Uri.encodeComponent(widget.conversation.id)}';

  Future<void> _loadTimeline() async {
    setState(() {
      _loading = true;
      _failure = null;
    });

    try {
      final timeline = await ref
          .read(chatRepositoryProvider)
          .loadRoomTimeline(widget.conversation.id);
      final archivedMessageIds = await ref
          .read(archivedMessageStoreProvider)
          .loadArchivedMessageIds(widget.conversation.id);
      if (!mounted) return;

      setState(() {
        _timeline = timeline;
        _archivedMessageIds = archivedMessageIds;
        _loading = false;
      });

      unawaited(
        ref.read(chatRepositoryProvider).markRoomRead(widget.conversation.id),
      );
    } on ChatFailure catch (failure) {
      if (!mounted) return;
      setState(() {
        _failure = failure;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _failure = ChatFailure.unknown(
          'Unable to load this conversation right now.',
          cause: error,
        );
        _loading = false;
      });
    }
  }

  Future<void> _sendMessage({_PendingOutgoingMessage? retryingMessage}) async {
    final message = (retryingMessage?.text ?? _composerController.text).trim();
    if (message.isEmpty || _sending) {
      return;
    }

    final pendingMessage =
        retryingMessage ?? _PendingOutgoingMessage.create(text: message);

    setState(() {
      _sending = true;
      _failure = null;
      _pendingMessage = pendingMessage.copyWith(
        deliveryState: ChatMessageDeliveryState.sending,
        failure: null,
      );
    });

    try {
      await ref
          .read(chatRepositoryProvider)
          .sendMessage(roomId: widget.conversation.id, message: message);
      _composerController.clear();
      await ref.read(preferencesStoreProvider).remove(_draftKey);
      await _loadTimeline();
      if (!mounted) return;
      setState(() {
        _pendingMessage = null;
      });
    } on ChatFailure catch (failure) {
      if (!mounted) return;
      setState(() {
        _pendingMessage = pendingMessage.copyWith(
          deliveryState: ChatMessageDeliveryState.failed,
          failure: failure,
        );
        _failure = null;
        _sending = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _pendingMessage = pendingMessage.copyWith(
          deliveryState: ChatMessageDeliveryState.failed,
          failure: ChatFailure.unknown(
            'Unable to send that message right now.',
            cause: error,
          ),
        );
        _failure = null;
        _sending = false;
      });
    } finally {
      if (mounted) {
        setState(() {
          _sending = false;
        });
      }
    }
  }

  Future<void> _archiveMessage(ChatMessage message) async {
    if (_archiving) {
      return;
    }

    final l10n = AppLocalizations.of(context);
    final shouldArchive = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.chatRoomArchiveDialogTitle),
        content: Text(l10n.chatRoomArchiveDialogMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.chatRoomArchiveAction),
          ),
        ],
      ),
    );
    if (shouldArchive != true || !mounted) {
      return;
    }

    setState(() {
      _archiving = true;
      _failure = null;
    });

    try {
      await ref
          .read(archivedMessageStoreProvider)
          .archiveMessage(
            roomId: widget.conversation.id,
            messageId: message.id,
          );
      if (!mounted) return;
      setState(() {
        _archivedMessageIds = {..._archivedMessageIds, message.id};
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.chatRoomArchiveSuccessMessage)),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.chatRoomArchiveFailureMessage)),
      );
    } finally {
      if (mounted) {
        setState(() {
          _archiving = false;
        });
      }
    }
  }

  Future<void> _restoreArchivedMessage(ChatMessage message) async {
    if (_archiving) {
      return;
    }

    final l10n = AppLocalizations.of(context);
    setState(() {
      _archiving = true;
      _failure = null;
    });

    try {
      await ref
          .read(archivedMessageStoreProvider)
          .restoreMessage(
            roomId: widget.conversation.id,
            messageId: message.id,
          );
      if (!mounted) return;
      setState(() {
        _archivedMessageIds = {..._archivedMessageIds}..remove(message.id);
        if (_archivedMessageIds.isEmpty) {
          _showingArchivedMessages = false;
        }
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.chatRoomRestoreSuccessMessage)),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.chatRoomRestoreFailureMessage)),
      );
    } finally {
      if (mounted) {
        setState(() {
          _archiving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final timeline = _timeline;
    final activeMessages = timeline == null
        ? const <ChatMessage>[]
        : timeline.messages
              .where((message) => !_archivedMessageIds.contains(message.id))
              .toList(growable: false);
    final archivedMessages = timeline == null
        ? const <ChatMessage>[]
        : timeline.messages
              .where((message) => _archivedMessageIds.contains(message.id))
              .toList(growable: false);
    final visibleMessages = _showingArchivedMessages
        ? archivedMessages
        : activeMessages;
    final displayPendingMessage = _showingArchivedMessages
        ? null
        : _pendingMessage;
    final roomTitle = timeline?.roomTitle ?? widget.conversation.title;
    final canSend =
        !_showingArchivedMessages &&
        (timeline?.canSendMessages ?? !widget.conversation.isInvite);

    return Scaffold(
      appBar: AppBar(
        title: Text(roomTitle),
        actions: [
          IconButton(
            onPressed: _loading
                ? null
                : () => setState(() {
                    _showingArchivedMessages = !_showingArchivedMessages;
                  }),
            icon: Icon(
              _showingArchivedMessages
                  ? Icons.forum_outlined
                  : Icons.archive_outlined,
            ),
            tooltip: _showingArchivedMessages
                ? l10n.chatRoomActiveTimelineAction
                : l10n.chatRoomArchivedMessagesAction,
          ),
          IconButton(
            onPressed: _loading ? null : _loadTimeline,
            icon: const Icon(Icons.refresh),
            tooltip: l10n.retryButton,
          ),
        ],
      ),
      body: Column(
        children: [
          if (_failure != null && !_loading)
            MaterialBanner(
              content: Text(_failure!.message),
              actions: [
                TextButton(
                  onPressed: _loadTimeline,
                  child: Text(l10n.retryButton),
                ),
              ],
            ),
          if (_draftRestored && !_loading)
            MaterialBanner(
              leading: const Icon(Icons.edit_note_outlined),
              content: Text(l10n.chatRoomDraftRestoredMessage),
              actions: [
                TextButton(
                  onPressed: () => setState(() {
                    _draftRestored = false;
                  }),
                  child: Text(l10n.semanticCloseButton),
                ),
              ],
            ),
          if (_showingArchivedMessages && !_loading)
            _ArchivedMessagesNotice(
              archivedCount: archivedMessages.length,
              onShowActiveTimeline: () => setState(() {
                _showingArchivedMessages = false;
              }),
            ),
          if (displayPendingMessage?.failure case final failure?)
            MaterialBanner(
              content: Text(failure.message),
              actions: [
                TextButton(
                  onPressed: _sending
                      ? null
                      : () => _sendMessage(
                          retryingMessage: displayPendingMessage,
                        ),
                  child: Text(l10n.chatRoomRetrySendAction),
                ),
              ],
            ),
          Expanded(
            child: switch ((_loading, timeline, _failure)) {
              (true, _, _) => LoadingState(message: l10n.chatRoomLoadingLabel),
              (false, null, final failure?) => ErrorState(
                message: failure.message,
                onRetry: _loadTimeline,
              ),
              (false, final timeline?, _)
                  when visibleMessages.isEmpty &&
                      displayPendingMessage == null =>
                RefreshIndicator(
                  onRefresh: _loadTimeline,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      SizedBox(
                        height: MediaQuery.sizeOf(context).height * 0.5,
                        child: Center(
                          child: EmptyState(
                            message: _showingArchivedMessages
                                ? l10n.chatRoomArchivedReviewEmptyMessage
                                : timeline.messages.isEmpty
                                ? l10n.chatRoomEmptyMessage
                                : l10n.chatRoomArchivedEmptyMessage,
                            icon: _showingArchivedMessages
                                ? Icons.archive_outlined
                                : Icons.chat_bubble_outline,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              (false, final ChatRoomTimeline _, _) => RefreshIndicator(
                onRefresh: _loadTimeline,
                child: ListView.separated(
                  reverse: true,
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
                  itemCount:
                      visibleMessages.length +
                      (displayPendingMessage == null ? 0 : 1),
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    if (displayPendingMessage != null && index == 0) {
                      return _MessageBubble(
                        message: displayPendingMessage.toChatMessage(context),
                        onRetry:
                            displayPendingMessage.deliveryState ==
                                    ChatMessageDeliveryState.failed &&
                                !_sending
                            ? () => _sendMessage(
                                retryingMessage: displayPendingMessage,
                              )
                            : null,
                      );
                    }

                    final messageIndex =
                        visibleMessages.length -
                        1 -
                        (index - (displayPendingMessage == null ? 0 : 1));
                    final message = visibleMessages[messageIndex];
                    return _MessageBubble(
                      message: message,
                      archived: _showingArchivedMessages,
                      onArchive: _archiving || _showingArchivedMessages
                          ? null
                          : () => _archiveMessage(message),
                      onRestore: _archiving || !_showingArchivedMessages
                          ? null
                          : () => _restoreArchivedMessage(message),
                    );
                  },
                ),
              ),
              _ => const SizedBox.shrink(),
            },
          ),
          if (_showingArchivedMessages)
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: OutlinedButton.icon(
                  onPressed: () => setState(() {
                    _showingArchivedMessages = false;
                  }),
                  icon: const Icon(Icons.forum_outlined),
                  label: Text(l10n.chatRoomActiveTimelineAction),
                ),
              ),
            )
          else
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _composerController,
                        minLines: 1,
                        maxLines: 4,
                        enabled: canSend && !_sending,
                        decoration: InputDecoration(
                          hintText: canSend
                              ? l10n.chatRoomComposerHint
                              : l10n.chatRoomComposerDisabledHint,
                        ),
                        onSubmitted: (_) => _sendMessage(),
                      ),
                    ),
                    const SizedBox(width: 12),
                    FilledButton(
                      onPressed: canSend && !_sending ? _sendMessage : null,
                      child: Text(
                        _sending
                            ? l10n.chatRoomSendingButton
                            : l10n.chatRoomSendButton,
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _ArchivedMessagesNotice extends StatelessWidget {
  const _ArchivedMessagesNotice({
    required this.archivedCount,
    required this.onShowActiveTimeline,
  });

  final int archivedCount;
  final VoidCallback onShowActiveTimeline;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return MaterialBanner(
      leading: const Icon(Icons.archive_outlined),
      content: Semantics(
        container: true,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.chatRoomArchivedReviewTitle,
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text(l10n.chatRoomArchivedReviewDescription(archivedCount)),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: onShowActiveTimeline,
          child: Text(l10n.chatRoomActiveTimelineAction),
        ),
      ],
    );
  }
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({
    required this.message,
    this.archived = false,
    this.onArchive,
    this.onRestore,
    this.onRetry,
  });

  final ChatMessage message;
  final bool archived;
  final VoidCallback? onArchive;
  final VoidCallback? onRestore;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final bubbleColor = archived
        ? theme.colorScheme.tertiaryContainer
        : message.isMine
        ? theme.colorScheme.primaryContainer
        : theme.colorScheme.surfaceContainerHighest;
    final foregroundColor = archived
        ? theme.colorScheme.onTertiaryContainer
        : message.isMine
        ? theme.colorScheme.onPrimaryContainer
        : theme.colorScheme.onSurface;
    final body = switch (message.contentType) {
      ChatMessageContentType.text => message.text ?? '',
      ChatMessageContentType.encrypted => l10n.chatRoomEncryptedMessageLabel,
      ChatMessageContentType.unsupported =>
        l10n.chatRoomUnsupportedMessageLabel,
    };
    final status = switch (message.deliveryState) {
      ChatMessageDeliveryState.sending => l10n.chatRoomMessageSendingStatus,
      ChatMessageDeliveryState.sent => null,
      ChatMessageDeliveryState.failed => l10n.chatRoomMessageFailedStatus,
    };

    return Align(
      alignment: message.isMine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          label: [
            message.senderDisplayName,
            if (archived) l10n.chatRoomArchivedMessageLabel,
            body,
            MaterialLocalizations.of(
              context,
            ).formatTimeOfDay(TimeOfDay.fromDateTime(message.sentAt)),
            if (status != null) status,
          ].join('. '),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: bubbleColor,
              borderRadius: BorderRadius.circular(18),
              border: archived
                  ? Border.all(color: theme.colorScheme.tertiary)
                  : message.deliveryState == ChatMessageDeliveryState.failed
                  ? Border.all(color: theme.colorScheme.error)
                  : null,
            ),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: ExcludeSemantics(
                          child: Text(
                            message.senderDisplayName,
                            style: theme.textTheme.labelMedium?.copyWith(
                              color: foregroundColor.withValues(alpha: 0.85),
                            ),
                          ),
                        ),
                      ),
                      if (onRetry != null)
                        IconButton(
                          onPressed: onRetry,
                          tooltip: l10n.chatRoomRetrySendAction,
                          icon: const Icon(Icons.refresh),
                          color: foregroundColor.withValues(alpha: 0.85),
                        )
                      else if (onArchive != null || onRestore != null)
                        PopupMenuButton<_MessageAction>(
                          tooltip: AppLocalizations.of(
                            context,
                          ).chatRoomMessageActionsLabel,
                          onSelected: (value) {
                            if (value == _MessageAction.archive) {
                              onArchive?.call();
                            } else if (value == _MessageAction.restore) {
                              onRestore?.call();
                            }
                          },
                          itemBuilder: (context) => [
                            if (onArchive != null)
                              PopupMenuItem<_MessageAction>(
                                value: _MessageAction.archive,
                                child: Text(
                                  AppLocalizations.of(
                                    context,
                                  ).chatRoomArchiveAction,
                                ),
                              ),
                            if (onRestore != null)
                              PopupMenuItem<_MessageAction>(
                                value: _MessageAction.restore,
                                child: Text(
                                  AppLocalizations.of(
                                    context,
                                  ).chatRoomRestoreAction,
                                ),
                              ),
                          ],
                          icon: Icon(
                            Icons.more_vert,
                            color: foregroundColor.withValues(alpha: 0.85),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  ExcludeSemantics(
                    child: Text(
                      body,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: foregroundColor,
                      ),
                    ),
                  ),
                  if (archived) ...[
                    const SizedBox(height: 6),
                    ExcludeSemantics(
                      child: Chip(
                        avatar: const Icon(Icons.archive_outlined),
                        label: Text(l10n.chatRoomArchivedMessageLabel),
                        visualDensity: VisualDensity.compact,
                      ),
                    ),
                  ],
                  const SizedBox(height: 6),
                  ExcludeSemantics(
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          MaterialLocalizations.of(context).formatTimeOfDay(
                            TimeOfDay.fromDateTime(message.sentAt),
                          ),
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: foregroundColor.withValues(alpha: 0.75),
                          ),
                        ),
                        if (status != null) ...[
                          const SizedBox(width: 8),
                          Text(
                            status,
                            style: theme.textTheme.labelSmall?.copyWith(
                              color:
                                  message.deliveryState ==
                                      ChatMessageDeliveryState.failed
                                  ? theme.colorScheme.error
                                  : foregroundColor.withValues(alpha: 0.75),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PendingOutgoingMessage {
  const _PendingOutgoingMessage({
    required this.localId,
    required this.text,
    required this.sentAt,
    required this.deliveryState,
    this.failure,
  });

  factory _PendingOutgoingMessage.create({required String text}) {
    return _PendingOutgoingMessage(
      localId: 'local-${DateTime.now().microsecondsSinceEpoch}',
      text: text,
      sentAt: DateTime.now(),
      deliveryState: ChatMessageDeliveryState.sending,
    );
  }

  final String localId;
  final String text;
  final DateTime sentAt;
  final ChatMessageDeliveryState deliveryState;
  final ChatFailure? failure;

  _PendingOutgoingMessage copyWith({
    ChatMessageDeliveryState? deliveryState,
    ChatFailure? failure,
  }) {
    return _PendingOutgoingMessage(
      localId: localId,
      text: text,
      sentAt: sentAt,
      deliveryState: deliveryState ?? this.deliveryState,
      failure: failure,
    );
  }

  ChatMessage toChatMessage(BuildContext context) {
    return ChatMessage(
      id: localId,
      senderId: '@me:local',
      senderDisplayName: AppLocalizations.of(context).chatRoomYouLabel,
      sentAt: sentAt,
      isMine: true,
      deliveryState: deliveryState,
      contentType: ChatMessageContentType.text,
      text: text,
    );
  }
}

enum _MessageAction { archive, restore }
