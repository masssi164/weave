import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/features/chat/domain/entities/chat_conversation.dart';
import 'package:weave/features/chat/domain/entities/context_graph.dart';

final contextPackPreviewFacadeProvider = Provider<ContextPackPreviewFacade>(
  (ref) => const ContextPackPreviewFacade(),
);

class ContextPackPreviewFacade {
  const ContextPackPreviewFacade();

  ContextPackPreview previewForRoom(ChatConversation conversation) {
    final roomEvidence = ContextGraphEvidence(
      id: 'evidence:room:${conversation.id}',
      label: 'Current room',
      scope: ContextGraphScope.currentRoom,
      sourceDescription:
          'The user opened ${conversation.title}; no background room scan is running.',
    );
    final selectedFilesEvidence = ContextGraphEvidence(
      id: 'evidence:selected-files:${conversation.id}',
      label: 'User-selected files',
      scope: ContextGraphScope.selectedFiles,
      sourceDescription:
          'Files must be selected explicitly before they can be included.',
    );
    final linkedTasksEvidence = ContextGraphEvidence(
      id: 'evidence:linked-tasks:${conversation.id}',
      label: 'Linked tasks',
      scope: ContextGraphScope.linkedTasks,
      sourceDescription:
          'Tasks must be linked to this room before they can be included.',
    );
    final recentDecisionsEvidence = ContextGraphEvidence(
      id: 'evidence:recent-decisions:${conversation.id}',
      label: 'Recent decisions',
      scope: ContextGraphScope.recentDecisions,
      sourceDescription:
          'Decisions must be captured as product records before they can be included.',
    );

    final roomItemId = 'room:${conversation.id}';
    final items = <ContextGraphItem>[
      ContextGraphItem(
        id: roomItemId,
        kind: ContextGraphNodeKind.room,
        scope: ContextGraphScope.currentRoom,
        title: conversation.title,
        description: 'The room the user is currently viewing.',
        includedInPreview: true,
        evidence: [roomEvidence],
      ),
      ContextGraphItem(
        id: 'selected-files:${conversation.id}',
        kind: ContextGraphNodeKind.file,
        scope: ContextGraphScope.selectedFiles,
        title: 'Selected files',
        description: 'No files selected yet.',
        includedInPreview: false,
        evidence: [selectedFilesEvidence],
      ),
      ContextGraphItem(
        id: 'linked-tasks:${conversation.id}',
        kind: ContextGraphNodeKind.task,
        scope: ContextGraphScope.linkedTasks,
        title: 'Linked tasks',
        description: 'No linked tasks yet.',
        includedInPreview: false,
        evidence: [linkedTasksEvidence],
      ),
      ContextGraphItem(
        id: 'recent-decisions:${conversation.id}',
        kind: ContextGraphNodeKind.decision,
        scope: ContextGraphScope.recentDecisions,
        title: 'Recent decisions',
        description: 'No recent decisions captured yet.',
        includedInPreview: false,
        evidence: [recentDecisionsEvidence],
      ),
    ];

    return ContextPackPreview(
      id: 'context-pack:${conversation.id}',
      items: items,
      edges: [
        ContextGraphEdge(
          sourceItemId: roomItemId,
          targetItemId: 'selected-files:${conversation.id}',
          kind: ContextGraphEdgeKind.attachedTo,
          evidenceIds: [selectedFilesEvidence.id],
        ),
        ContextGraphEdge(
          sourceItemId: 'linked-tasks:${conversation.id}',
          targetItemId: roomItemId,
          kind: ContextGraphEdgeKind.discussedIn,
          evidenceIds: [linkedTasksEvidence.id],
        ),
        ContextGraphEdge(
          sourceItemId: 'recent-decisions:${conversation.id}',
          targetItemId: roomItemId,
          kind: ContextGraphEdgeKind.discussedIn,
          evidenceIds: [recentDecisionsEvidence.id],
        ),
      ],
      agentUseEnabled: false,
      backgroundRoomReadingEnabled: false,
    );
  }
}
