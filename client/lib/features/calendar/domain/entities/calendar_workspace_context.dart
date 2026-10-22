import 'package:weave/features/calendar/domain/entities/calendar_event.dart';

/// One identity-bound discovery result; never contains credentials.
class CalendarWorkspaceContext {
  const CalendarWorkspaceContext({
    required this.ownerKey,
    required this.scopes,
    required this.selectedScope,
    required this.evaluationTimeZone,
  });
  final String ownerKey;
  final CalendarScopeList scopes;
  final CalendarScope? selectedScope;
  final String evaluationTimeZone;
}
