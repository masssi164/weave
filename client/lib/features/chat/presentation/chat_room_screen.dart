import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/core/widgets/empty_state.dart';
import 'package:weave/core/widgets/error_state.dart';
import 'package:weave/core/widgets/loading_state.dart';
import 'package:weave/features/app/domain/entities/integration_invalidation.dart';
import 'package:weave/features/app/presentation/providers/workspace_invalidation_provider.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_message.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/features/chat/domain/entities/channel_workspace.dart';
import 'package:weave/features/chat/domain/entities/decision_evidence.dart';
import 'package:weave/features/chat/domain/repositories/chat_repository.dart';
import 'package:weave/features/chat/presentation/providers/archived_message_store_provider.dart';
import 'package:weave/features/chat/presentation/providers/channel_workspace_preview_provider.dart';
import 'package:weave/features/chat/presentation/providers/chat_repository_provider.dart';
import 'package:weave/features/chat/presentation/providers/decision_evidence_provider.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

class ChatRoomScreen extends ConsumerStatefulWidget {
  const ChatRoomScreen({super.key, required this.conversation});

  final ChatConversation conversation;

  @override
  ConsumerState<ChatRoomScreen> createState() => _ChatRoomScreenState();
}

class _ChatRoomScreenState extends ConsumerState<ChatRoomScreen>
    with WidgetsBindingObserver {
  final _composerController = TextEditingController();
  ChatRoomTimeline? _timeline;
  ChatFailure? _failure;
  Set<String> _archivedMessageIds = <String>{};
  bool _loading = true;
  bool _invitationAccepted = false;
  bool _sending = false;
  bool _archiving = false;
  bool _showingArchivedMessages = false;
  _PendingOutgoingMessage? _pendingMessage;
  Timer? _draftSaveDebounce;
  Timer? _receiveTimer;
  Future<void>? _timelineRequest;
  bool _reloadRequested = false;
  bool _started = false;
  bool _visible = false;
  bool _foreground = true;
  bool _accessDenied = false;
  // Visibility changes discard reads, while valid sends may finish in the background.
  int _receiveGeneration = 0;
  int _authorityGeneration = 0;
  int _composerRevision = 0;
  String? _memberStorageScope;
  String? _draftRestorationKey;

  bool get _canReceive =>
      mounted &&
      _visible &&
      _foreground &&
      !_accessDenied &&
      (!widget.conversation.isInvite || _invitationAccepted);

  void _scheduleReceive() {
    _receiveTimer?.cancel();
    if (!_canReceive || _sending || _timelineRequest != null) return;
    _receiveTimer = Timer(const Duration(seconds: 2), () {
      unawaited(_loadTimeline(routine: true));
    });
  }

  void _suspendReceive() {
    _receiveTimer?.cancel();
    _receiveGeneration++;
    _reloadRequested = false;
  }

  void _authorityChanged() {
    _authorityGeneration++;
    _suspendReceive();
    if (!mounted) return;
    _draftSaveDebounce?.cancel();
    _isRestoringDraft = true;
    _composerController.clear();
    _isRestoringDraft = false;
    setState(() {
      _draftRestored = false;
      _memberStorageScope = null;
      _draftRestorationKey = null;
      _composerRevision = 0;
      _accessDenied = true;
      _timeline = null;
      _archivedMessageIds = {};
      _pendingMessage = null;
      _sending = false;
      _loading = false;
      _failure = const ChatFailure.sessionRequired(
        'Current Chat access must be checked again.',
      );
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final visible =
        TickerMode.valuesOf(context).enabled &&
        (ModalRoute.isCurrentOf(context) ?? true);
    if (_visible == visible) return;
    _visible = visible;
    _suspendReceive();
    if (_started && _canReceive) {
      Future<void>.microtask(() => _loadTimeline(routine: true));
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final foreground = state == AppLifecycleState.resumed;
    if (_foreground == foreground) return;
    _foreground = foreground;
    _suspendReceive();
    if (_started && _canReceive) unawaited(_loadTimeline(routine: true));
  }

  @override
  void didUpdateWidget(ChatRoomScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.conversation.id == widget.conversation.id &&
        oldWidget.conversation.isInvite == widget.conversation.isInvite) {
      return;
    }
    _authorityGeneration++;
    _suspendReceive();
    _draftSaveDebounce?.cancel();
    _isRestoringDraft = true;
    _composerController.clear();
    _isRestoringDraft = false;
    _timeline = null;
    _failure = null;
    _pendingMessage = null;
    _archivedMessageIds = {};
    _draftRestored = false;
    _memberStorageScope = null;
    _draftRestorationKey = null;
    _composerRevision = 0;
    _invitationAccepted = false;
    _accessDenied = false;
    _sending = false;
    _loading = !widget.conversation.isInvite;
    final generation = _authorityGeneration;
    Future<void>.microtask(() async {
      if (!mounted || generation != _authorityGeneration) return;
      _started = true;
      await _loadTimeline();
    });
  }

  bool _isRestoringDraft = false;
  bool _draftRestored = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    for (final integration in [
      WorkspaceIntegration.appAuth,
      WorkspaceIntegration.chat,
    ]) {
      ref.listenManual(
        integrationInvalidationProvider(integration),
        (_, _) => _authorityChanged(),
      );
    }
    ref.listenManual(chatRepositoryProvider, (previous, next) {
      if (identical(previous, next)) return;
      _authorityChanged();
    });
    _composerController.addListener(_onComposerChanged);
    if (widget.conversation.isInvite) {
      _loading = false;
      return;
    }
    final generation = _authorityGeneration;
    Future<void>.microtask(() async {
      if (!mounted || generation != _authorityGeneration) return;
      _started = true;
      await _loadTimeline();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _receiveTimer?.cancel();
    _receiveGeneration++;
    _authorityGeneration++;
    _draftSaveDebounce?.cancel();
    _composerController
      ..removeListener(_onComposerChanged)
      ..dispose();
    super.dispose();
  }

  bool _bindDraftOwner(String? scope) {
    final owner = scope != null && RegExp(r'^[0-9a-f]{64}$').hasMatch(scope)
        ? scope
        : null;
    if (_memberStorageScope == owner) return false;
    _authorityGeneration++;
    _draftSaveDebounce?.cancel();
    _isRestoringDraft = true;
    _composerController.clear();
    _isRestoringDraft = false;
    _composerRevision = 0;
    _memberStorageScope = owner;
    _draftRestorationKey = null;
    _draftRestored = false;
    _pendingMessage = null;
    _sending = false;
    return true;
  }

  Future<void> _restoreDraft() async {
    final key = _draftKey;
    if (key == null ||
        _draftRestorationKey == key ||
        _composerController.text.isNotEmpty) {
      return;
    }
    _draftRestorationKey = key;
    final generation = _authorityGeneration;
    final revision = _composerRevision;
    final store = ref.read(preferencesStoreProvider);
    try {
      final draft = await store.getString(key);
      if (!mounted ||
          generation != _authorityGeneration ||
          revision != _composerRevision ||
          key != _draftKey ||
          _composerController.text.isNotEmpty ||
          draft == null ||
          draft.trim().isEmpty) {
        return;
      }
      _isRestoringDraft = true;
      _composerController.text = draft;
      _isRestoringDraft = false;
      setState(() {
        _draftRestored = true;
      });
    } catch (_) {
      // Optional local drafts cannot bypass or interrupt an authorized room receive.
    }
  }

  void _onComposerChanged() {
    if (_isRestoringDraft) return;
    _composerRevision++;
    _draftSaveDebounce?.cancel();
    final key = _draftKey;
    if (key == null) return;
    final generation = _authorityGeneration;
    final text = _composerController.text;
    final store = ref.read(preferencesStoreProvider);
    _draftSaveDebounce = Timer(const Duration(milliseconds: 300), () async {
      if (!mounted || generation != _authorityGeneration || key != _draftKey) {
        return;
      }
      if (text.trim().isEmpty) {
        await store.remove(key);
      } else {
        await store.setString(key, text);
      }
    });
  }

  String? get _draftKey => _memberStorageScope == null
      ? null
      : 'chat.roomDraft.v2.$_memberStorageScope.${Uri.encodeComponent(widget.conversation.id)}';

  Future<void> _acceptInvitation() async {
    if (_loading) return;
    final repository = ref.read(chatRepositoryProvider);
    if (repository is! ChatInvitationRepository) {
      setState(
        () => _failure = const ChatFailure.unsupportedPlatform(
          'Invitations are unavailable.',
        ),
      );
      return;
    }
    final generation = _authorityGeneration;
    final roomId = widget.conversation.id;
    bool current() =>
        mounted &&
        generation == _authorityGeneration &&
        roomId == widget.conversation.id &&
        identical(repository, ref.read(chatRepositoryProvider));
    setState(() {
      _loading = true;
      _failure = null;
    });
    try {
      await (repository as ChatInvitationRepository).acceptInvitation(roomId);
      if (!current()) return;
      setState(() {
        _invitationAccepted = true;
        _accessDenied = false;
        _started = true;
        _loading = false;
      });
      await _loadTimeline();
    } on ChatFailure catch (failure) {
      if (!current()) return;
      setState(() {
        _failure = failure;
        _loading = false;
      });
    } catch (error) {
      if (!current()) return;
      setState(() {
        _failure = ChatFailure.unknown(
          'Invitation acceptance failed.',
          cause: error,
        );
        _loading = false;
      });
    }
  }

  Future<void> _loadTimeline({bool routine = false}) {
    if (!mounted ||
        !_visible ||
        !_foreground ||
        (widget.conversation.isInvite && !_invitationAccepted)) {
      return Future<void>.value();
    }
    if (routine && (_accessDenied || _sending)) return Future<void>.value();
    _started = true;
    if (!routine) _accessDenied = false;
    _receiveTimer?.cancel();
    final inFlight = _timelineRequest;
    if (inFlight != null) {
      _reloadRequested = true;
      return inFlight;
    }
    _timelineRequest = _drainTimeline();
    return _timelineRequest!;
  }

  Future<void> _drainTimeline() async {
    try {
      do {
        _reloadRequested = false;
        await _readTimeline();
      } while (_reloadRequested && _canReceive);
    } finally {
      _timelineRequest = null;
      _scheduleReceive();
    }
  }

  Future<void> _readTimeline() async {
    final generation = _receiveGeneration;
    final roomId = widget.conversation.id;
    final repository = ref.read(chatRepositoryProvider);
    final archives = ref.read(archivedMessageStoreProvider);
    bool current() =>
        _canReceive &&
        generation == _receiveGeneration &&
        roomId == widget.conversation.id &&
        identical(repository, ref.read(chatRepositoryProvider));
    if (_timeline == null) {
      setState(() {
        _loading = true;
        _failure = null;
      });
    }
    try {
      final timeline = await repository.loadRoomTimeline(roomId);
      if (!current()) return;
      if (_bindDraftOwner(timeline.memberStorageScope)) {
        setState(() {
          _timeline = null;
          _archivedMessageIds = {};
          _loading = true;
        });
      }
      final archivedMessageIds = await archives.loadArchivedMessageIds(roomId);
      if (!current()) return;
      setState(() {
        _timeline = timeline;
        _archivedMessageIds = archivedMessageIds;
        _failure = null;
        _loading = false;
      });
      unawaited(_restoreDraft());
      unawaited(
        repository.markRoomRead(roomId).catchError((Object _) {
          // A read marker cannot invalidate a successfully received timeline.
        }),
      );
    } on ChatFailure catch (failure) {
      if (!current()) return;
      setState(() {
        if (failure.type == ChatFailureType.sessionRequired) {
          _accessDenied = true;
          _receiveGeneration++;
          _authorityGeneration++;
          _sending = false;
          _timeline = null;
          _archivedMessageIds = {};
          _pendingMessage = null;
          _reloadRequested = false;
        }
        _failure = failure;
        _loading = false;
      });
    } catch (error) {
      if (!current()) return;
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

    final generation = _authorityGeneration;
    final roomId = widget.conversation.id;
    final repository = ref.read(chatRepositoryProvider);
    final draftKey = _draftKey;
    final draftStore = draftKey == null
        ? null
        : ref.read(preferencesStoreProvider);
    bool current() =>
        mounted &&
        generation == _authorityGeneration &&
        roomId == widget.conversation.id &&
        identical(repository, ref.read(chatRepositoryProvider));
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
      await repository.sendMessage(roomId: roomId, message: message);
      if (!current()) return;
      _composerController.clear();
      if (draftKey != null) await draftStore!.remove(draftKey);
      if (!current()) return;
      await _loadTimeline();
      if (!current()) return;
      setState(() {
        _pendingMessage = null;
      });
    } on ChatFailure catch (failure) {
      if (!current()) return;
      setState(() {
        if (failure.type == ChatFailureType.sessionRequired) {
          _authorityGeneration++;
          _suspendReceive();
          _accessDenied = true;
          _timeline = null;
          _archivedMessageIds = <String>{};
          _pendingMessage = null;
          _failure = failure;
        } else {
          _pendingMessage = pendingMessage.copyWith(
            deliveryState: ChatMessageDeliveryState.failed,
            failure: failure,
          );
          _failure = null;
        }
        _sending = false;
      });
    } catch (error) {
      if (!current()) return;
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
      if (current()) {
        setState(() {
          _sending = false;
        });
        _scheduleReceive();
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

  void _captureDecisionEvidence(
    ChatMessage message,
    DecisionEvidenceKind kind,
  ) {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(decisionEvidenceProvider.notifier);
    final now = DateTime.now();
    if (kind == DecisionEvidenceKind.decision) {
      controller.createDecisionFromMessage(
        roomId: widget.conversation.id,
        message: message,
        capturedAt: now,
        ownerLabel: l10n.chatDecisionEvidenceOwnerYou,
      );
    } else {
      controller.captureMessage(
        roomId: widget.conversation.id,
        message: message,
        kind: kind,
        capturedAt: now,
        ownerLabel: l10n.chatDecisionEvidenceOwnerYou,
      );
    }

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          l10n.chatDecisionEvidenceCapturedMessage(
            kind == DecisionEvidenceKind.decision
                ? l10n.chatDecisionLedgerRecordLabel.toLowerCase()
                : _decisionEvidenceKindLabel(l10n, kind).toLowerCase(),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (widget.conversation.isInvite && !_invitationAccepted) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.conversation.title)),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(l10n.chatInvitationDescription),
                if (_failure != null) ...[
                  const SizedBox(height: 16),
                  Semantics(
                    liveRegion: true,
                    child: Text(
                      _failure!.type == ChatFailureType.sessionRequired
                          ? l10n.chatErrorSessionRequiredGuidance
                          : l10n.chatInvitationFailed,
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                FilledButton(
                  key: const Key('chat-accept-invitation-button'),
                  onPressed: _loading ? null : _acceptInvitation,
                  child: Text(
                    _loading
                        ? l10n.chatInvitationJoining
                        : l10n.chatInvitationAccept,
                  ),
                ),
                if (_loading)
                  const Padding(
                    padding: EdgeInsets.all(16),
                    child: CircularProgressIndicator(),
                  ),
              ],
            ),
          ),
        ),
      );
    }
    if (_failure?.type == ChatFailureType.sessionRequired) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.chatScreenTitle)),
        body: ErrorState(
          message: l10n.chatErrorSessionRequiredGuidance,
          retryLabel: l10n.retryButton,
          onRetry: _loadTimeline,
        ),
      );
    }
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
    final timelineTitle = timeline?.roomTitle.trim();
    final roomTitle = timelineTitle == null || timelineTitle.isEmpty
        ? widget.conversation.title
        : timelineTitle;
    final canSend =
        !_showingArchivedMessages && timeline?.canSendMessages == true;
    final decisionEvidenceRecords = ref.watch(decisionEvidenceProvider);
    final decisionEvidenceSnapshot = RoomDecisionEvidenceSnapshot(
      roomId: widget.conversation.id,
      records: List<DecisionEvidenceRecord>.unmodifiable(
        decisionEvidenceRecords[widget.conversation.id] ??
            const <DecisionEvidenceRecord>[],
      ),
      backgroundRoomReadingEnabled: false,
    );

    Widget buildChatTimelineBody() {
      return LayoutBuilder(
        builder: (context, constraints) {
          final compactContextPreview = constraints.maxHeight < 320;
          final showRoomGovernancePreview =
              constraints.maxHeight >= 220 &&
              displayPendingMessage?.failure == null;

          return Column(
            children: [
              if (_failure != null && !_loading)
                MaterialBanner(
                  content: Text(_chatRoomLoadFailureMessage(l10n, _failure!)),
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
                  content: Semantics(
                    container: true,
                    liveRegion: true,
                    child: Text(l10n.chatRoomDraftRestoredMessage),
                  ),
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
                  content: Semantics(
                    container: true,
                    liveRegion: true,
                    child: Text(_chatRoomSendFailureMessage(l10n, failure)),
                  ),
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
              if (!_loading &&
                  !_showingArchivedMessages &&
                  showRoomGovernancePreview)
                ConstrainedBox(
                  constraints: BoxConstraints(
                    maxHeight: constraints.maxHeight >= 480
                        ? constraints.maxHeight * 0.42
                        : constraints.maxHeight * 0.32,
                  ),
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                    child: Column(
                      children: [
                        _RoomDecisionEvidenceCard(
                          snapshot: decisionEvidenceSnapshot,
                          compact: compactContextPreview,
                        ),
                      ],
                    ),
                  ),
                ),
              Expanded(
                child: switch ((_loading, timeline, _failure)) {
                  (true, _, _) => LoadingState(
                    message: l10n.chatRoomLoadingLabel,
                  ),
                  (false, null, final failure?) => ErrorState(
                    message: _chatRoomLoadFailureMessage(l10n, failure),
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
                            height: MediaQuery.sizeOf(context).height < 720
                                ? 420
                                : MediaQuery.sizeOf(context).height * 0.5,
                            child: Center(
                              child: EmptyState(
                                message: _emptyTimelineMessage(
                                  l10n,
                                  timeline,
                                  showingArchivedMessages:
                                      _showingArchivedMessages,
                                ),
                                guidance: _emptyTimelineGuidance(
                                  l10n,
                                  timeline,
                                  showingArchivedMessages:
                                      _showingArchivedMessages,
                                ),
                                icon: _showingArchivedMessages
                                    ? Icons.archive_outlined
                                    : Icons.chat_bubble_outline,
                                actionLabel: _emptyTimelineActionLabel(
                                  l10n,
                                  timeline,
                                  showingArchivedMessages:
                                      _showingArchivedMessages,
                                ),
                                onAction: _emptyTimelineAction(
                                  timeline,
                                  showingArchivedMessages:
                                      _showingArchivedMessages,
                                ),
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
                            message: displayPendingMessage.toChatMessage(
                              context,
                            ),
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
                          onCapture: _archiving || _showingArchivedMessages
                              ? null
                              : (kind) =>
                                    _captureDecisionEvidence(message, kind),
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
          );
        },
      );
    }

    final workspaceFacade = ref.watch(channelWorkspacePreviewFacadeProvider);
    final workspace = workspaceFacade.supportsWorkspaceTabs(widget.conversation)
        ? workspaceFacade.previewForChannel(widget.conversation)
        : null;
    final scaffold = Scaffold(
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
      body: workspace == null
          ? buildChatTimelineBody()
          : _ChannelWorkspaceTabs(
              workspace: workspace,
              showSummary: displayPendingMessage?.failure == null,
              chatChild: buildChatTimelineBody(),
              decisionsChild: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: _RoomDecisionEvidenceCard(
                  snapshot: decisionEvidenceSnapshot,
                  compact: false,
                ),
              ),
            ),
    );

    if (workspace == null) {
      return scaffold;
    }

    return DefaultTabController(
      length: workspace.surfaces.length,
      child: scaffold,
    );
  }

  String _emptyTimelineMessage(
    AppLocalizations l10n,
    ChatRoomTimeline timeline, {
    required bool showingArchivedMessages,
  }) {
    if (showingArchivedMessages) {
      return l10n.chatRoomArchivedReviewEmptyMessage;
    }
    if (timeline.messages.isEmpty) {
      return l10n.chatRoomEmptyMessage;
    }
    return l10n.chatRoomArchivedEmptyMessage;
  }

  String _emptyTimelineGuidance(
    AppLocalizations l10n,
    ChatRoomTimeline timeline, {
    required bool showingArchivedMessages,
  }) {
    if (showingArchivedMessages) {
      return l10n.chatRoomArchivedReviewEmptyGuidance;
    }
    if (timeline.messages.isEmpty) {
      return l10n.chatRoomEmptyGuidance;
    }
    return l10n.chatRoomArchivedEmptyGuidance;
  }

  String _emptyTimelineActionLabel(
    AppLocalizations l10n,
    ChatRoomTimeline timeline, {
    required bool showingArchivedMessages,
  }) {
    if (showingArchivedMessages) {
      return l10n.chatRoomRefreshAction;
    }
    if (timeline.messages.isEmpty) {
      return l10n.chatRoomRefreshAction;
    }
    return l10n.chatRoomArchivedMessagesAction;
  }

  VoidCallback _emptyTimelineAction(
    ChatRoomTimeline timeline, {
    required bool showingArchivedMessages,
  }) {
    if (showingArchivedMessages) {
      return _loadTimeline;
    }
    if (timeline.messages.isEmpty) {
      return _loadTimeline;
    }
    return () => setState(() {
      _showingArchivedMessages = true;
    });
  }
}

String _chatRoomLoadFailureMessage(AppLocalizations l10n, ChatFailure failure) {
  return switch (failure.type) {
    ChatFailureType.configuration ||
    ChatFailureType.unsupportedConfiguration => l10n.chatErrorAdminGuidance,
    ChatFailureType.sessionRequired => l10n.chatErrorSessionRequiredGuidance,
    ChatFailureType.unsupportedPlatform =>
      l10n.chatErrorUnsupportedPlatformGuidance,
    ChatFailureType.peerDevicePending => failure.message,
    ChatFailureType.cancelled ||
    ChatFailureType.protocol ||
    ChatFailureType.storage ||
    ChatFailureType.unknown => l10n.chatRoomLoadFailureMessage,
  };
}

String _chatRoomSendFailureMessage(AppLocalizations l10n, ChatFailure failure) {
  return switch (failure.type) {
    ChatFailureType.configuration ||
    ChatFailureType.unsupportedConfiguration => l10n.chatErrorAdminGuidance,
    ChatFailureType.sessionRequired => l10n.chatErrorSessionRequiredGuidance,
    ChatFailureType.unsupportedPlatform =>
      l10n.chatErrorUnsupportedPlatformGuidance,
    ChatFailureType.peerDevicePending => failure.message,
    ChatFailureType.cancelled ||
    ChatFailureType.protocol ||
    ChatFailureType.storage ||
    ChatFailureType.unknown => l10n.chatRoomSendFailureMessage,
  };
}

class _ChannelWorkspaceTabs extends StatelessWidget {
  const _ChannelWorkspaceTabs({
    required this.workspace,
    required this.showSummary,
    required this.chatChild,
    required this.decisionsChild,
  });

  final ChannelWorkspacePreview workspace;
  final bool showSummary;
  final Widget chatChild;
  final Widget decisionsChild;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Column(
      children: [
        if (showSummary)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: _ChannelWorkspaceSummary(workspace: workspace),
          ),
        Material(
          color: theme.colorScheme.surface,
          child: Semantics(
            container: true,
            label: l10n.channelWorkspaceTabsSemanticLabel(
              workspace.channelTitle,
            ),
            child: TabBar(
              isScrollable: true,
              tabAlignment: TabAlignment.start,
              tabs: workspace.surfaces
                  .map((surface) => _ChannelWorkspaceTab(surface: surface))
                  .toList(growable: false),
            ),
          ),
        ),
        Expanded(
          child: TabBarView(
            children: workspace.surfaces
                .map((surface) {
                  if (surface.kind == ChannelWorkspaceSurfaceKind.chat) {
                    return chatChild;
                  }

                  if (surface.kind == ChannelWorkspaceSurfaceKind.decisions) {
                    return decisionsChild;
                  }

                  if (surface.kind == ChannelWorkspaceSurfaceKind.meetings) {
                    return _ChannelMeetingsPreviewPanel(workspace: workspace);
                  }

                  return _ChannelWorkspaceSurfacePanel(
                    workspace: workspace,
                    surface: surface,
                  );
                })
                .toList(growable: false),
          ),
        ),
      ],
    );
  }
}

class _ChannelWorkspaceSummary extends StatelessWidget {
  const _ChannelWorkspaceSummary({required this.workspace});

  final ChannelWorkspacePreview workspace;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return Semantics(
      container: true,
      label:
          '${l10n.channelWorkspaceSummaryTitle(workspace.channelTitle)}. '
          '${l10n.channelWorkspaceSummaryDescription}. '
          '${l10n.channelWorkspaceRouteLabel(workspace.routePath)}. '
          '${l10n.channelWorkspaceSpaceIdLabel(workspace.contextId)}. '
          '${l10n.channelWorkspaceEvidenceLabel(workspace.spaceEvidenceRef)}. '
          '${l10n.channelWorkspaceGovernanceNote}',
      child: ExcludeSemantics(
        child: Card(
          elevation: 0,
          color: theme.colorScheme.primaryContainer.withValues(alpha: 0.24),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: BorderSide(
              color: theme.colorScheme.primary.withValues(alpha: 0.36),
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.workspaces_outline,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.channelWorkspaceSummaryTitle(
                              workspace.channelTitle,
                            ),
                            style: theme.textTheme.titleMedium,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ChannelWorkspaceTab extends StatelessWidget {
  const _ChannelWorkspaceTab({required this.surface});

  final ChannelWorkspaceSurface surface;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Tab(
      child: SizedBox(
        height: 48,
        child: Center(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(_channelSurfaceIcon(surface.kind), size: 20),
              const SizedBox(width: 8),
              Text(_channelSurfaceTabLabel(l10n, surface.kind)),
              if (surface.isGated) ...[
                const SizedBox(width: 6),
                const Icon(Icons.lock_outline, size: 16),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _ChannelWorkspaceSurfacePanel extends StatelessWidget {
  const _ChannelWorkspaceSurfacePanel({
    required this.workspace,
    required this.surface,
  });

  final ChannelWorkspacePreview workspace;
  final ChannelWorkspaceSurface surface;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final title = _channelSurfacePanelTitle(l10n, surface.kind);
    final body = _channelSurfacePanelDescription(l10n, surface.kind);
    final status = _channelSurfaceStatusLabel(l10n, surface.availability);
    final semanticsLabel = [
      title,
      status,
      body,
      l10n.channelWorkspaceSurfaceObjectLabel(surface.canonicalObjectRef),
      l10n.channelWorkspaceEvidenceLabel(surface.supportSafeEvidenceRef),
      l10n.channelWorkspaceExplicitContextNote(workspace.channelTitle),
    ].join('. ');

    return Semantics(
      container: true,
      label: semanticsLabel,
      child: ExcludeSemantics(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Card(
            elevation: 0,
            color: theme.colorScheme.surfaceContainerHighest,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(24),
              side: BorderSide(color: theme.colorScheme.outlineVariant),
            ),
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        _channelSurfaceIcon(surface.kind),
                        color: theme.colorScheme.primary,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(title, style: theme.textTheme.titleLarge),
                            const SizedBox(height: 6),
                            Text(body, style: theme.textTheme.bodyMedium),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      Chip(
                        avatar: Icon(
                          surface.isUnavailable
                              ? Icons.lock_outline
                              : Icons.visibility_outlined,
                          size: 18,
                        ),
                        label: Text(status),
                      ),
                      Chip(
                        avatar: const Icon(Icons.tag_outlined, size: 18),
                        label: Text(
                          l10n.channelWorkspaceSurfaceObjectLabel(
                            surface.canonicalObjectRef,
                          ),
                        ),
                      ),
                      Chip(
                        avatar: const Icon(Icons.verified_outlined, size: 18),
                        label: Text(
                          l10n.channelWorkspaceEvidenceLabel(
                            surface.supportSafeEvidenceRef,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  MergeSemantics(
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.privacy_tip_outlined,
                          size: 20,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            l10n.channelWorkspaceExplicitContextNote(
                              workspace.channelTitle,
                            ),
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
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

IconData _channelSurfaceIcon(ChannelWorkspaceSurfaceKind kind) {
  return switch (kind) {
    ChannelWorkspaceSurfaceKind.chat => Icons.chat_bubble_outline,
    ChannelWorkspaceSurfaceKind.files => Icons.folder_outlined,
    ChannelWorkspaceSurfaceKind.documents => Icons.description_outlined,
    ChannelWorkspaceSurfaceKind.calendar => Icons.event_outlined,
    ChannelWorkspaceSurfaceKind.meetings => Icons.video_call_outlined,
    ChannelWorkspaceSurfaceKind.boards => Icons.view_kanban_outlined,
    ChannelWorkspaceSurfaceKind.decisions =>
      Icons.assignment_turned_in_outlined,
    ChannelWorkspaceSurfaceKind.evidence => Icons.fact_check_outlined,
  };
}

String _channelSurfaceTabLabel(
  AppLocalizations l10n,
  ChannelWorkspaceSurfaceKind kind,
) {
  return switch (kind) {
    ChannelWorkspaceSurfaceKind.chat => l10n.channelWorkspaceChatTab,
    ChannelWorkspaceSurfaceKind.files => l10n.channelWorkspaceFilesTab,
    ChannelWorkspaceSurfaceKind.documents =>
      l10n.providerCategoryDocumentsTitle,
    ChannelWorkspaceSurfaceKind.calendar => l10n.channelWorkspaceCalendarTab,
    ChannelWorkspaceSurfaceKind.meetings => l10n.channelWorkspaceMeetingsTab,
    ChannelWorkspaceSurfaceKind.boards => l10n.channelWorkspaceBoardsTab,
    ChannelWorkspaceSurfaceKind.decisions =>
      l10n.chatDecisionEvidenceDecisionsLabel,
    ChannelWorkspaceSurfaceKind.evidence =>
      l10n.chatDecisionEvidenceEvidencePluralLabel,
  };
}

String _channelSurfacePanelTitle(
  AppLocalizations l10n,
  ChannelWorkspaceSurfaceKind kind,
) {
  return switch (kind) {
    ChannelWorkspaceSurfaceKind.chat => l10n.channelWorkspaceChatTitle,
    ChannelWorkspaceSurfaceKind.files => l10n.channelWorkspaceFilesTitle,
    ChannelWorkspaceSurfaceKind.documents =>
      l10n.providerCategoryDocumentsTitle,
    ChannelWorkspaceSurfaceKind.calendar => l10n.channelWorkspaceCalendarTitle,
    ChannelWorkspaceSurfaceKind.meetings => l10n.channelWorkspaceMeetingsTitle,
    ChannelWorkspaceSurfaceKind.boards => l10n.channelWorkspaceBoardsTitle,
    ChannelWorkspaceSurfaceKind.decisions =>
      l10n.chatDecisionEvidencePanelTitle,
    ChannelWorkspaceSurfaceKind.evidence => l10n.chatContextEvidenceHintTitle,
  };
}

String _channelSurfacePanelDescription(
  AppLocalizations l10n,
  ChannelWorkspaceSurfaceKind kind,
) {
  return switch (kind) {
    ChannelWorkspaceSurfaceKind.chat => l10n.channelWorkspaceChatDescription,
    ChannelWorkspaceSurfaceKind.files => l10n.channelWorkspaceFilesDescription,
    ChannelWorkspaceSurfaceKind.documents =>
      l10n.providerCategoryDocumentsDetail,
    ChannelWorkspaceSurfaceKind.calendar =>
      l10n.channelWorkspaceCalendarDescription,
    ChannelWorkspaceSurfaceKind.meetings =>
      l10n.channelWorkspaceMeetingsDescription,
    ChannelWorkspaceSurfaceKind.boards =>
      l10n.channelWorkspaceBoardsDescription,
    ChannelWorkspaceSurfaceKind.decisions =>
      l10n.chatDecisionEvidencePanelDescription,
    ChannelWorkspaceSurfaceKind.evidence =>
      l10n.chatContextEvidenceHintDescription,
  };
}

String _channelSurfaceStatusLabel(
  AppLocalizations l10n,
  ChannelWorkspaceSurfaceAvailability availability,
) {
  return switch (availability) {
    ChannelWorkspaceSurfaceAvailability.available =>
      l10n.channelWorkspaceStatusAvailable,
    ChannelWorkspaceSurfaceAvailability.disabledByPolicy =>
      l10n.channelWorkspaceStatusDisabledByPolicy,
    ChannelWorkspaceSurfaceAvailability.notConfigured =>
      l10n.channelWorkspaceStatusNotConfigured,
    ChannelWorkspaceSurfaceAvailability.degraded =>
      l10n.channelWorkspaceStatusDegraded,
    ChannelWorkspaceSurfaceAvailability.unavailable =>
      l10n.channelWorkspaceStatusUnavailable,
    ChannelWorkspaceSurfaceAvailability.comingLater =>
      l10n.channelWorkspaceStatusComingLater,
  };
}

class _ChannelMeetingsPreviewPanel extends StatelessWidget {
  const _ChannelMeetingsPreviewPanel({required this.workspace});

  final ChannelWorkspacePreview workspace;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final meeting = workspace.meetingPreview;
    final semanticsLabel = [
      l10n.channelWorkspaceMeetingsTitle,
      l10n.channelWorkspaceStatusComingLater,
      l10n.channelWorkspaceMeetingsDescription,
      l10n.channelWorkspaceMeetingsCapabilityBody,
      l10n.channelWorkspaceMeetingsPrivacyBody(workspace.channelTitle),
      l10n.channelWorkspaceMeetingsRecordingOff,
    ].join('. ');

    return Semantics(
      container: true,
      label: semanticsLabel,
      child: ExcludeSemantics(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Card(
                elevation: 0,
                color: theme.colorScheme.surfaceContainerHighest,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(24),
                  side: BorderSide(color: theme.colorScheme.outlineVariant),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(
                            Icons.video_call_outlined,
                            color: theme.colorScheme.primary,
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  l10n.channelWorkspaceMeetingsTitle,
                                  style: theme.textTheme.titleLarge,
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  l10n.channelWorkspaceMeetingsDescription,
                                  style: theme.textTheme.bodyMedium,
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          Chip(
                            avatar: const Icon(Icons.lock_outline, size: 18),
                            label: Text(l10n.channelWorkspaceStatusComingLater),
                          ),
                          Chip(
                            avatar: const Icon(
                              Icons.no_accounts_outlined,
                              size: 18,
                            ),
                            label: Text(
                              l10n.channelWorkspaceMeetingsRecordingOff,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 18),
                      _ChannelMeetingInfoBlock(
                        icon: Icons.verified_user_outlined,
                        title: l10n.channelWorkspaceMeetingsCapabilityTitle,
                        body: l10n.channelWorkspaceMeetingsCapabilityBody,
                      ),
                      const SizedBox(height: 14),
                      _ChannelMeetingInfoBlock(
                        icon: Icons.privacy_tip_outlined,
                        title: l10n.channelWorkspaceMeetingsPrivacyTitle,
                        body: l10n.channelWorkspaceMeetingsPrivacyBody(
                          workspace.channelTitle,
                        ),
                      ),
                      const SizedBox(height: 20),
                      Text(
                        l10n.channelWorkspaceMeetingsContextTitle,
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        l10n.channelWorkspaceMeetingsContextBody,
                        style: theme.textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: meeting.contextItems
                            .map(
                              (item) => Chip(
                                avatar: Icon(
                                  _meetingContextItemIcon(item.kind),
                                  size: 18,
                                ),
                                label: Text(
                                  _meetingContextItemLabel(l10n, item.kind),
                                ),
                              ),
                            )
                            .toList(growable: false),
                      ),
                      const SizedBox(height: 20),
                      Wrap(
                        spacing: 12,
                        runSpacing: 12,
                        children: meeting.controls
                            .map(
                              (control) => _ChannelMeetingControlButton(
                                control: control,
                              ),
                            )
                            .toList(growable: false),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChannelMeetingInfoBlock extends StatelessWidget {
  const _ChannelMeetingInfoBlock({
    required this.icon,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return MergeSemantics(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: theme.colorScheme.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: theme.textTheme.titleSmall),
                const SizedBox(height: 2),
                Text(
                  body,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ChannelMeetingControlButton extends StatelessWidget {
  const _ChannelMeetingControlButton({required this.control});

  final ChannelMeetingControl control;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final label = switch (control.kind) {
      ChannelMeetingControlKind.join => l10n.channelWorkspaceMeetingsJoinButton,
      ChannelMeetingControlKind.start =>
        l10n.channelWorkspaceMeetingsStartButton,
    };
    final reason = _meetingControlDisabledReason(l10n, control.disabledReason);

    return Tooltip(
      message: reason,
      child: Semantics(
        button: true,
        enabled: control.enabled,
        label: '$label. $reason',
        child: FilledButton.tonalIcon(
          onPressed: control.enabled ? () {} : null,
          icon: Icon(
            control.kind == ChannelMeetingControlKind.join
                ? Icons.login_outlined
                : Icons.add_call,
          ),
          label: Text(label),
        ),
      ),
    );
  }
}

IconData _meetingContextItemIcon(ChannelMeetingContextItemKind kind) {
  return switch (kind) {
    ChannelMeetingContextItemKind.agenda => Icons.format_list_bulleted,
    ChannelMeetingContextItemKind.files => Icons.attach_file,
    ChannelMeetingContextItemKind.decisions => Icons.fact_check_outlined,
    ChannelMeetingContextItemKind.tasks => Icons.task_alt,
    ChannelMeetingContextItemKind.followUpEvidence => Icons.plagiarism_outlined,
  };
}

String _meetingContextItemLabel(
  AppLocalizations l10n,
  ChannelMeetingContextItemKind kind,
) {
  return switch (kind) {
    ChannelMeetingContextItemKind.agenda =>
      l10n.channelWorkspaceMeetingsContextAgenda,
    ChannelMeetingContextItemKind.files =>
      l10n.channelWorkspaceMeetingsContextFiles,
    ChannelMeetingContextItemKind.decisions =>
      l10n.channelWorkspaceMeetingsContextDecisions,
    ChannelMeetingContextItemKind.tasks =>
      l10n.channelWorkspaceMeetingsContextTasks,
    ChannelMeetingContextItemKind.followUpEvidence =>
      l10n.channelWorkspaceMeetingsContextEvidence,
  };
}

String _meetingControlDisabledReason(AppLocalizations l10n, String reason) {
  return switch (reason) {
    'meeting-backend-capability-unavailable' =>
      l10n.channelWorkspaceMeetingsBackendUnavailableReason,
    _ => reason,
  };
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

class _RoomDecisionEvidenceCard extends StatelessWidget {
  const _RoomDecisionEvidenceCard({
    required this.snapshot,
    required this.compact,
  });

  final RoomDecisionEvidenceSnapshot snapshot;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final ledgerRecords = snapshot.decisionLedgerRecords;
    final counts = DecisionEvidenceKind.values
        .map(
          (kind) => l10n.chatDecisionEvidenceCountLabel(
            _decisionEvidenceKindPluralLabel(l10n, kind),
            snapshot.countFor(kind),
          ),
        )
        .join(', ');
    final recordSummary = snapshot.records.isEmpty
        ? l10n.chatDecisionEvidenceEmptyState
        : snapshot.records
              .map(
                (record) =>
                    '${_decisionEvidenceKindLabel(l10n, record.kind)}: '
                    '${record.title}. '
                    '${l10n.chatDecisionEvidenceSourceLabel(record.source.senderDisplayName)}.',
              )
              .join(' ');
    final ledgerSummary = ledgerRecords.isEmpty
        ? l10n.chatDecisionLedgerEmptyState
        : ledgerRecords
              .map(
                (decision) =>
                    '${l10n.chatDecisionLedgerRecordLabel}: '
                    '${decision.title}. '
                    '${_decisionLedgerStatusLabel(l10n, decision.status)}. '
                    '${l10n.chatDecisionLedgerSourceCount(decision.references.length)}.',
              )
              .join(' ');
    final auditMetadata = snapshot.auditMetadata;

    return Semantics(
      container: true,
      label: [
        l10n.chatDecisionEvidencePanelTitle,
        counts,
        ledgerSummary,
        recordSummary,
        auditMetadata.provenanceSummary,
        auditMetadata.exportPosture,
        auditMetadata.auditRefs.join(', '),
        l10n.chatDecisionEvidenceNoBackgroundReading,
      ].join('. '),
      child: ExcludeSemantics(
        child: Card(
          elevation: 0,
          color: theme.colorScheme.surface,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
            side: BorderSide(color: theme.colorScheme.outlineVariant),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      Icons.assignment_turned_in_outlined,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            l10n.chatDecisionEvidencePanelTitle,
                            style: theme.textTheme.titleMedium,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            l10n.chatDecisionEvidencePanelDescription,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: DecisionEvidenceKind.values
                      .map(
                        (kind) => _DecisionEvidenceCountChip(
                          kind: kind,
                          count: snapshot.countFor(kind),
                        ),
                      )
                      .toList(growable: false),
                ),
                const SizedBox(height: 12),
                if (ledgerRecords.isNotEmpty) ...[
                  Text(
                    l10n.chatDecisionLedgerTitle,
                    style: theme.textTheme.titleSmall,
                  ),
                  const SizedBox(height: 8),
                  for (final decision in ledgerRecords.take(compact ? 2 : 4))
                    _DecisionLedgerRecordTile(decision: decision),
                  const SizedBox(height: 8),
                  Text(
                    auditMetadata.provenanceSummary,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    auditMetadata.exportPosture,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    auditMetadata.auditRefs.join(' · '),
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                if (snapshot.records.isEmpty)
                  Text(
                    l10n.chatDecisionEvidenceEmptyState,
                    style: theme.textTheme.bodyMedium,
                  )
                else ...[
                  for (final record in snapshot.records.take(compact ? 2 : 4))
                    _DecisionEvidenceRecordTile(record: record),
                  if (snapshot.records.length > (compact ? 2 : 4))
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        l10n.chatDecisionEvidenceMoreRecords(
                          snapshot.records.length - (compact ? 2 : 4),
                        ),
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                ],
                const SizedBox(height: 8),
                Text(
                  l10n.chatDecisionEvidenceNoBackgroundReading,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DecisionEvidenceCountChip extends StatelessWidget {
  const _DecisionEvidenceCountChip({required this.kind, required this.count});

  final DecisionEvidenceKind kind;
  final int count;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Chip(
      avatar: Icon(_decisionEvidenceKindIcon(kind), size: 18),
      label: Text(
        l10n.chatDecisionEvidenceCountLabel(
          _decisionEvidenceKindPluralLabel(l10n, kind),
          count,
        ),
      ),
      visualDensity: VisualDensity.compact,
    );
  }
}

class _DecisionEvidenceRecordTile extends StatelessWidget {
  const _DecisionEvidenceRecordTile({required this.record});

  final DecisionEvidenceRecord record;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            _decisionEvidenceKindIcon(record.kind),
            size: 20,
            color: theme.colorScheme.primary,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _decisionEvidenceKindLabel(l10n, record.kind),
                  style: theme.textTheme.labelLarge,
                ),
                Text(record.title, style: theme.textTheme.bodyMedium),
                Text(
                  l10n.chatDecisionEvidenceRecordMeta(
                    _decisionEvidenceStatusLabel(l10n, record.status),
                    record.ownerLabel,
                    record.source.senderDisplayName,
                  ),
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _DecisionLedgerRecordTile extends StatelessWidget {
  const _DecisionLedgerRecordTile({required this.decision});

  final ChannelDecisionRecord decision;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final sourceLabel = decision.references
        .map((reference) => reference.label)
        .where((label) => label.isNotEmpty)
        .firstOrNull;
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: MergeSemantics(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.assignment_turned_in_outlined,
              size: 20,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.chatDecisionLedgerRecordLabel,
                    style: theme.textTheme.labelLarge,
                  ),
                  Text(decision.title, style: theme.textTheme.bodyMedium),
                  Text(
                    l10n.chatDecisionLedgerRecordMeta(
                      _decisionLedgerStatusLabel(l10n, decision.status),
                      decision.authorLabel,
                      sourceLabel ??
                          l10n.chatDecisionLedgerSourceCount(
                            decision.references.length,
                          ),
                    ),
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

IconData _decisionEvidenceKindIcon(DecisionEvidenceKind kind) {
  return switch (kind) {
    DecisionEvidenceKind.decision => Icons.check_circle_outline,
    DecisionEvidenceKind.risk => Icons.warning_amber_outlined,
    DecisionEvidenceKind.openQuestion => Icons.help_outline,
    DecisionEvidenceKind.evidence => Icons.link_outlined,
  };
}

String _decisionEvidenceKindLabel(
  AppLocalizations l10n,
  DecisionEvidenceKind kind,
) {
  return switch (kind) {
    DecisionEvidenceKind.decision => l10n.chatDecisionEvidenceDecisionLabel,
    DecisionEvidenceKind.risk => l10n.chatDecisionEvidenceRiskLabel,
    DecisionEvidenceKind.openQuestion =>
      l10n.chatDecisionEvidenceOpenQuestionLabel,
    DecisionEvidenceKind.evidence => l10n.chatDecisionEvidenceEvidenceLabel,
  };
}

String _decisionEvidenceKindPluralLabel(
  AppLocalizations l10n,
  DecisionEvidenceKind kind,
) {
  return switch (kind) {
    DecisionEvidenceKind.decision => l10n.chatDecisionEvidenceDecisionsLabel,
    DecisionEvidenceKind.risk => l10n.chatDecisionEvidenceRisksLabel,
    DecisionEvidenceKind.openQuestion =>
      l10n.chatDecisionEvidenceOpenQuestionsLabel,
    DecisionEvidenceKind.evidence =>
      l10n.chatDecisionEvidenceEvidencePluralLabel,
  };
}

String _decisionEvidenceStatusLabel(
  AppLocalizations l10n,
  DecisionEvidenceStatus status,
) {
  return switch (status) {
    DecisionEvidenceStatus.active => l10n.chatDecisionEvidenceStatusActive,
    DecisionEvidenceStatus.resolved => l10n.chatDecisionEvidenceStatusResolved,
    DecisionEvidenceStatus.archived => l10n.chatDecisionEvidenceStatusArchived,
  };
}

String _decisionLedgerStatusLabel(
  AppLocalizations l10n,
  DecisionLedgerStatus status,
) {
  return switch (status) {
    DecisionLedgerStatus.proposed => l10n.chatDecisionLedgerStatusProposed,
    DecisionLedgerStatus.accepted => l10n.chatDecisionLedgerStatusAccepted,
    DecisionLedgerStatus.superseded => l10n.chatDecisionLedgerStatusSuperseded,
    DecisionLedgerStatus.rejected => l10n.chatDecisionLedgerStatusRejected,
  };
}

class _MessageBubble extends StatelessWidget {
  const _MessageBubble({
    required this.message,
    this.archived = false,
    this.onArchive,
    this.onCapture,
    this.onRestore,
    this.onRetry,
  });

  final ChatMessage message;
  final bool archived;
  final VoidCallback? onArchive;
  final ValueChanged<DecisionEvidenceKind>? onCapture;
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
    final senderLabel = message.isMine
        ? l10n.chatRoomYouLabel
        : message.senderDisplayName;
    final localTime = MaterialLocalizations.of(
      context,
    ).formatTimeOfDay(TimeOfDay.fromDateTime(message.sentAt.toLocal()));
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
            senderLabel,
            if (archived) l10n.chatRoomArchivedMessageLabel,
            body,
            localTime,
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
                            senderLabel,
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
                      else if (onArchive != null ||
                          onCapture != null ||
                          onRestore != null)
                        PopupMenuButton<_MessageAction>(
                          tooltip: AppLocalizations.of(
                            context,
                          ).chatRoomMessageActionsLabel,
                          onSelected: (value) {
                            if (value == _MessageAction.archive) {
                              onArchive?.call();
                            } else if (value ==
                                _MessageAction.captureDecision) {
                              onCapture?.call(DecisionEvidenceKind.decision);
                            } else if (value == _MessageAction.captureRisk) {
                              onCapture?.call(DecisionEvidenceKind.risk);
                            } else if (value ==
                                _MessageAction.captureOpenQuestion) {
                              onCapture?.call(
                                DecisionEvidenceKind.openQuestion,
                              );
                            } else if (value ==
                                _MessageAction.captureEvidence) {
                              onCapture?.call(DecisionEvidenceKind.evidence);
                            } else if (value == _MessageAction.restore) {
                              onRestore?.call();
                            }
                          },
                          itemBuilder: (context) => [
                            if (onCapture != null) ...[
                              PopupMenuItem<_MessageAction>(
                                value: _MessageAction.captureDecision,
                                child: Text(
                                  AppLocalizations.of(
                                    context,
                                  ).chatDecisionEvidenceCaptureDecisionAction,
                                ),
                              ),
                              PopupMenuItem<_MessageAction>(
                                value: _MessageAction.captureRisk,
                                child: Text(
                                  AppLocalizations.of(
                                    context,
                                  ).chatDecisionEvidenceCaptureRiskAction,
                                ),
                              ),
                              PopupMenuItem<_MessageAction>(
                                value: _MessageAction.captureOpenQuestion,
                                child: Text(
                                  AppLocalizations.of(
                                    context,
                                  ).chatDecisionEvidenceCaptureQuestionAction,
                                ),
                              ),
                              PopupMenuItem<_MessageAction>(
                                value: _MessageAction.captureEvidence,
                                child: Text(
                                  AppLocalizations.of(
                                    context,
                                  ).chatDecisionEvidenceCaptureEvidenceAction,
                                ),
                              ),
                            ],
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
                          localTime,
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

enum _MessageAction {
  archive,
  captureDecision,
  captureRisk,
  captureOpenQuestion,
  captureEvidence,
  restore,
}
