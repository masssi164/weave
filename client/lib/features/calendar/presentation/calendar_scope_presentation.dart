import 'package:weave/features/app/domain/entities/member_space_access_snapshot.dart';
import 'package:weave/features/calendar/domain/entities/calendar_event.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

String calendarScopeLabel(
  AppLocalizations l10n,
  CalendarScope scope,
  MemberSpaceAccessSnapshot? access,
) {
  final reference = [
    scope.contextId,
    scope.workspaceId,
  ].where((candidate) => access?.displayNameFor(candidate) != null).firstOrNull;
  if (reference == null) return l10n.calendarUnavailableScopeName;
  final name = access!.displayNameFor(reference)!;
  final ordinal = access.displayOrdinalFor(reference);
  final label = ordinal == null ? name : l10n.filesSpaceOrdinal(name, ordinal);
  return switch (scope.type) {
    'workspace' => label,
    'team' => l10n.calendarTeamScopeName(label),
    'channel' => l10n.calendarChannelScopeName(label),
    _ => l10n.calendarUnavailableScopeName,
  };
}
