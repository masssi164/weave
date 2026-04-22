import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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

  @override
  void initState() {
    super.initState();
    Future<void>.microtask(_loadTimeline);
  }

  @override
  void dispose() {
    _composerController.dispose();
    super.dispose();
  }

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

  Future<void> _sendMessage() async {
    final message = _composerController.text.trim();
    if (message.isEmpty || _sending) {
      return;
    }

    setState(() {
      _sending = true;
      _failure = null;
    });

    try {
      await ref
          .read(chatRepositoryProvider)
          .sendMessage(roomId: widget.conversation.id, message: message);
      _composerController.clear();
      await _loadTimeline();
    } on ChatFailure catch (failure) {
      if (!mounted) return;
      setState(() {
        _failure = failure;
        _sending = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _failure = ChatFailure.unknown(
          'Unable to send that message right now.',
          cause: error,
        );
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

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final timeline = _timeline;
    final visibleMessages = timeline == null
        ? const <ChatMessage>[]
        : timeline.messages
              .where((message) => !_archivedMessageIds.contains(message.id))
              .toList(growable: false);
    final roomTitle = timeline?.roomTitle ?? widget.conversation.title;
    final canSend = timeline?.canSendMessages ?? !widget.conversation.isInvite;

    return Scaffold(
      appBar: AppBar(
        title: Text(roomTitle),
        actions: [
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
          Expanded(
            child: switch ((_loading, timeline, _failure)) {
              (true, _, _) => LoadingState(message: l10n.chatRoomLoadingLabel),
              (false, null, final failure?) => ErrorState(
                message: failure.message,
                onRetry: _loadTimeline,
              ),
              (false, final timeline?, _) when visibleMessages.isEmpty =>
                RefreshIndicator(
                  onRefresh: _loadTimeline,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      SizedBox(
                        height: MediaQuery.sizeOf(context).height * 0.5,
                        child: Center(
                          child: EmptyState(
                            message: timeline.messages.isEmpty
                                ? l10n.chatRoomEmptyMessage
                                : l10n.chatRoomArchivedEmptyMessage,
                            icon: Icons.chat_bubble_outline,
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
                  itemCount: visibleMessages.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final message =
                        visibleMessages[visibleMessages.length - 1 - index];
                    return _MessageBubble(
                      message: message,
                      onArchive: _archiving
                          ? null
                          : () => _archiveMessage(message),
                    );
                  },
                ),
              ),
              _ => const SizedBox.shrink(),
            },
          ),
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

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({required this.message, this.onArchive});

  final ChatMessage message;
  final VoidCallback? onArchive;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bubbleColor = message.isMine
        ? theme.colorScheme.primaryContainer
        : theme.colorScheme.surfaceContainerHighest;
    final foregroundColor = message.isMine
        ? theme.colorScheme.onPrimaryContainer
        : theme.colorScheme.onSurface;
    final body = switch (message.contentType) {
      ChatMessageContentType.text => message.text ?? '',
      ChatMessageContentType.encrypted => 'Encrypted message',
      ChatMessageContentType.unsupported => 'Unsupported message',
    };
    final status = switch (message.deliveryState) {
      ChatMessageDeliveryState.sending => 'Sending…',
      ChatMessageDeliveryState.sent => null,
      ChatMessageDeliveryState.failed => 'Failed to send',
    };

    return Align(
      alignment: message.isMine ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 420),
        child: Semantics(
          container: true,
          label: [
            message.senderDisplayName,
            body,
            MaterialLocalizations.of(
              context,
            ).formatTimeOfDay(TimeOfDay.fromDateTime(message.sentAt)),
            if (status != null) status,
          ].join('. '),
          child: ExcludeSemantics(
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: bubbleColor,
                borderRadius: BorderRadius.circular(18),
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
                          child: Text(
                            message.senderDisplayName,
                            style: theme.textTheme.labelMedium?.copyWith(
                              color: foregroundColor.withValues(alpha: 0.85),
                            ),
                          ),
                        ),
                        PopupMenuButton<_MessageAction>(
                          tooltip: AppLocalizations.of(
                            context,
                          ).chatRoomMessageActionsLabel,
                          onSelected: (value) {
                            if (value == _MessageAction.archive) {
                              onArchive?.call();
                            }
                          },
                          itemBuilder: (context) => [
                            PopupMenuItem<_MessageAction>(
                              value: _MessageAction.archive,
                              enabled: onArchive != null,
                              child: Text(
                                AppLocalizations.of(
                                  context,
                                ).chatRoomArchiveAction,
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
                    Text(
                      body,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: foregroundColor,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Row(
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
                              color: foregroundColor.withValues(alpha: 0.75),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

enum _MessageAction { archive }
