// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'calendar_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// Manages the list of calendar events.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud CalDAV calls

@ProviderFor(CalendarNotifier)
const calendarProvider = CalendarNotifierProvider._();

/// Manages the list of calendar events.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud CalDAV calls
final class CalendarNotifierProvider
    extends $AsyncNotifierProvider<CalendarNotifier, List<CalendarEvent>> {
  /// Manages the list of calendar events.
  ///
  /// Returns an empty list by default — no network calls.
  /// TODO(integration): replace with Nextcloud CalDAV calls
  const CalendarNotifierProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calendarProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calendarNotifierHash();

  @$internal
  @override
  CalendarNotifier create() => CalendarNotifier();
}

String _$calendarNotifierHash() => r'af7ece649a00bade02c3828ec613c8f705c33c86';

/// Manages the list of calendar events.
///
/// Returns an empty list by default — no network calls.
/// TODO(integration): replace with Nextcloud CalDAV calls

abstract class _$CalendarNotifier extends $AsyncNotifier<List<CalendarEvent>> {
  FutureOr<List<CalendarEvent>> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final created = build();
    final ref =
        this.ref as $Ref<AsyncValue<List<CalendarEvent>>, List<CalendarEvent>>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<List<CalendarEvent>>, List<CalendarEvent>>,
              AsyncValue<List<CalendarEvent>>,
              Object?,
              Object?
            >;
    element.handleValue(ref, created);
  }
}
