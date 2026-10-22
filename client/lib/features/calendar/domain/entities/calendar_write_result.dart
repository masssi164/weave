import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/features/calendar/domain/entities/calendar_failure.dart';

enum CalendarWriteOutcome {
  notCommitted,
  uncertain,
  committed,
  committedRefreshPending,
}

class CalendarWriteResult {
  const CalendarWriteResult(
    this.outcome, {
    this.event,
    this.ownerKey,
    this.scopeId,
    this.contextCurrent = true,
  });
  final String? ownerKey;
  final String? scopeId;
  final bool contextCurrent;
  final CalendarWriteOutcome outcome;
  final CalendarEvent? event;
}

class CalendarWriteFailure extends CalendarFailure {
  CalendarWriteFailure(CalendarFailure source, {required this.outcome})
    : super(
        source.kind,
        writeMayHaveCommitted: source.writeMayHaveCommitted,
        stage: source.stage,
        httpStatus: source.httpStatus,
        apiCode: source.apiCode,
      );
  final CalendarWriteOutcome outcome;
}
