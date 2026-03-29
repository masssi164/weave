import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:weave/features/calendar/models/calendar_event.dart';

part 'calendar_provider.g.dart';

/// Manages the list of calendar events.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud CalDAV calls
@riverpod
class CalendarNotifier extends _$CalendarNotifier {
  @override
  Future<List<CalendarEvent>> build() async => [];
}
