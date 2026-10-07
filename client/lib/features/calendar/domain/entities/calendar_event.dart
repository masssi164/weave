class CalendarScope {
  const CalendarScope({
    this.id = 'workspace',
    required this.type,
    required this.label,
    this.workspaceId = 'workspace',
    this.contextId = 'workspace-default',
    this.teamId,
    this.channelId,
    this.accessModel = 'shared-workspace-calendar',
    this.capabilities = const [],
  });

  static const workspace = CalendarScope(
    id: 'workspace',
    type: 'workspace',
    label: 'Weave workspace calendar',
    contextId: 'workspace-default',
    accessModel: 'shared-workspace-calendar',
  );

  final String id;
  final String type;
  final String label;
  final String workspaceId;
  final String contextId;
  final String? teamId;
  final String? channelId;
  final String accessModel;
  final List<String> capabilities;

  bool get isWorkspace => type == workspace.type;
  bool get isTeam => type == 'team';
  bool get isChannel => type == 'channel';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CalendarScope &&
          other.id == id &&
          other.type == type &&
          other.label == label &&
          other.workspaceId == workspaceId &&
          other.contextId == contextId &&
          other.teamId == teamId &&
          other.channelId == channelId;

  @override
  int get hashCode =>
      Object.hash(id, type, label, workspaceId, contextId, teamId, channelId);
}

class CalendarThreadRef {
  const CalendarThreadRef({
    this.kind = 'context',
    required this.contextId,
    this.meetingThreadId,
    this.channelId,
    this.matrixRoomId,
    this.matrixThreadId,
    this.boardTaskIds = const [],
  });

  factory CalendarThreadRef.forScope(CalendarScope scope) => CalendarThreadRef(
    contextId: scope.contextId,
    channelId: scope.isChannel ? scope.channelId : null,
  );

  final String kind;
  final String contextId;
  final String? meetingThreadId;
  final String? channelId;
  final String? matrixRoomId;
  final String? matrixThreadId;
  final List<String> boardTaskIds;
}

class CalendarAttendee {
  const CalendarAttendee({
    this.name,
    this.email,
    this.role,
    this.responseStatus,
  });

  final String? name;
  final String? email;
  final String? role;
  final String? responseStatus;

  String get displayLabel {
    final displayName = name ?? email ?? 'Unknown attendee';
    final address = email != null && email != name ? ' <$email>' : '';
    final status = responseStatus == null ? '' : ' · $responseStatus';
    return '$displayName$address$status';
  }
}

class CalendarScopeList {
  const CalendarScopeList({this.scopes = const [CalendarScope.workspace]});

  final List<CalendarScope> scopes;
}

class CalendarEventList {
  const CalendarEventList({
    this.scope = CalendarScope.workspace,
    this.events = const [],
  });

  final CalendarScope scope;
  final List<CalendarEvent> events;
}

enum CalendarTimeKind { date, floating, utc, zoned }

class CalendarEvent {
  CalendarEvent({
    required this.id,
    this.previewHandle,
    required this.title,
    required this.startTime,
    required this.endTime,
    this.description,
    this.timezone,
    this.location,
    this.allDay = false,
    this.etag,
    this.scope = CalendarScope.workspace,
    CalendarThreadRef? threadRef,
    this.attendees = const [],
    this.timeKind = CalendarTimeKind.utc,
    this.recurring = false,
    this.allowedActions = const [],
    this.updatedAt,
  }) : threadRef = threadRef ?? CalendarThreadRef.forScope(scope);

  final String id;

  /// Present only for a short-lived provider event view without a stable Event ID.
  final String? previewHandle;
  bool get isTransientPreview => previewHandle != null;
  final String title;
  final String? description;

  /// Wall-clock fields held in UTC containers to avoid host DST normalization.
  /// The separate timeKind/timezone fields carry the actual temporal intent.
  final DateTime startTime;
  final DateTime endTime;
  final String? timezone;
  final String? location;
  final bool allDay;
  final String? etag;
  final CalendarScope scope;
  final CalendarThreadRef threadRef;
  final List<CalendarAttendee> attendees;
  final CalendarTimeKind timeKind;
  final bool recurring;
  final List<String> allowedActions;

  bool get canEdit => allowedActions.contains('update');
  bool get canDelete => allowedActions.contains('delete');
  final DateTime? updatedAt;
}

class CalendarEventDraft {
  const CalendarEventDraft({
    required this.title,
    required this.startTime,
    required this.endTime,
    required this.timezone,
    this.description,
    this.location,
    this.allDay = false,
    this.timeKind = CalendarTimeKind.zoned,
    this.scope = CalendarScope.workspace,
  });

  final String title;
  final String? description;
  final DateTime startTime;
  final DateTime endTime;
  final String timezone;
  final String? location;
  final bool allDay;
  final CalendarTimeKind timeKind;
  final CalendarScope scope;
}
