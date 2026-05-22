import 'package:weave/features/chat/domain/entities/chat_conversation.dart';

enum ChannelWorkspaceSurfaceKind { chat, files, boards, calendar }

enum ChannelWorkspaceSurfaceAvailability { available, preview, gated }

class ChannelWorkspaceSurface {
  const ChannelWorkspaceSurface({
    required this.kind,
    required this.availability,
    required this.providerContractId,
    required this.contextId,
  });

  final ChannelWorkspaceSurfaceKind kind;
  final ChannelWorkspaceSurfaceAvailability availability;
  final String providerContractId;
  final String contextId;

  bool get isAvailable =>
      availability == ChannelWorkspaceSurfaceAvailability.available;

  bool get isGated => availability == ChannelWorkspaceSurfaceAvailability.gated;
}

class ChannelWorkspacePreview {
  const ChannelWorkspacePreview({
    required this.channelId,
    required this.channelTitle,
    required this.contextId,
    required this.surfaces,
    required this.explicitContextOnly,
    required this.backgroundRoomReadingEnabled,
    required this.adminSetupExposedToMembers,
  });

  factory ChannelWorkspacePreview.forConversation(
    ChatConversation conversation,
  ) {
    final contextId = 'channel:${conversation.id}';
    return ChannelWorkspacePreview(
      channelId: conversation.id,
      channelTitle: conversation.title,
      contextId: contextId,
      surfaces: [
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.chat,
          availability: ChannelWorkspaceSurfaceAvailability.available,
          providerContractId: 'matrix-room',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.files,
          availability: ChannelWorkspaceSurfaceAvailability.preview,
          providerContractId: 'weave-files-channel-link',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.boards,
          availability: ChannelWorkspaceSurfaceAvailability.preview,
          providerContractId: 'weave-boards-channel-link',
          contextId: contextId,
        ),
        ChannelWorkspaceSurface(
          kind: ChannelWorkspaceSurfaceKind.calendar,
          availability: ChannelWorkspaceSurfaceAvailability.gated,
          providerContractId: 'weave-calendar-channel-scope',
          contextId: contextId,
        ),
      ],
      explicitContextOnly: true,
      backgroundRoomReadingEnabled: false,
      adminSetupExposedToMembers: false,
    );
  }

  final String channelId;
  final String channelTitle;
  final String contextId;
  final List<ChannelWorkspaceSurface> surfaces;
  final bool explicitContextOnly;
  final bool backgroundRoomReadingEnabled;
  final bool adminSetupExposedToMembers;

  bool get isChannelWorkspaceGoverned =>
      explicitContextOnly &&
      !backgroundRoomReadingEnabled &&
      !adminSetupExposedToMembers;

  ChannelWorkspaceSurface surface(ChannelWorkspaceSurfaceKind kind) {
    return surfaces.singleWhere((surface) => surface.kind == kind);
  }
}
