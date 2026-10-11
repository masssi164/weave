// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'calendar_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(calendarFacadeClient)
final calendarFacadeClientProvider = CalendarFacadeClientProvider._();

final class CalendarFacadeClientProvider
    extends
        $FunctionalProvider<
          CalendarFacadeClient,
          CalendarFacadeClient,
          CalendarFacadeClient
        >
    with $Provider<CalendarFacadeClient> {
  CalendarFacadeClientProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calendarFacadeClientProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calendarFacadeClientHash();

  @$internal
  @override
  $ProviderElement<CalendarFacadeClient> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  CalendarFacadeClient create(Ref ref) {
    return calendarFacadeClient(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(CalendarFacadeClient value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<CalendarFacadeClient>(value),
    );
  }
}

String _$calendarFacadeClientHash() =>
    r'459098a9b859969169a1c71dcbbae146ac2852b9';

@ProviderFor(calendarRepository)
final calendarRepositoryProvider = CalendarRepositoryProvider._();

final class CalendarRepositoryProvider
    extends
        $FunctionalProvider<
          CalendarRepository,
          CalendarRepository,
          CalendarRepository
        >
    with $Provider<CalendarRepository> {
  CalendarRepositoryProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calendarRepositoryProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calendarRepositoryHash();

  @$internal
  @override
  $ProviderElement<CalendarRepository> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  CalendarRepository create(Ref ref) {
    return calendarRepository(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(CalendarRepository value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<CalendarRepository>(value),
    );
  }
}

String _$calendarRepositoryHash() =>
    r'7114198967e26af320220ef18cd4b95dab8ceb7f';

@ProviderFor(SelectedCalendarScope)
final selectedCalendarScopeProvider = SelectedCalendarScopeProvider._();

final class SelectedCalendarScopeProvider
    extends $NotifierProvider<SelectedCalendarScope, CalendarScope> {
  SelectedCalendarScopeProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'selectedCalendarScopeProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$selectedCalendarScopeHash();

  @$internal
  @override
  SelectedCalendarScope create() => SelectedCalendarScope();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(CalendarScope value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<CalendarScope>(value),
    );
  }
}

String _$selectedCalendarScopeHash() =>
    r'0c5e723169e1116b25402c7b9d6831a09d1ee6c9';

abstract class _$SelectedCalendarScope extends $Notifier<CalendarScope> {
  CalendarScope build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<CalendarScope, CalendarScope>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<CalendarScope, CalendarScope>,
              CalendarScope,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

@ProviderFor(calendarScopes)
final calendarScopesProvider = CalendarScopesProvider._();

final class CalendarScopesProvider
    extends
        $FunctionalProvider<
          AsyncValue<CalendarScopeList>,
          CalendarScopeList,
          FutureOr<CalendarScopeList>
        >
    with
        $FutureModifier<CalendarScopeList>,
        $FutureProvider<CalendarScopeList> {
  CalendarScopesProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calendarScopesProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calendarScopesHash();

  @$internal
  @override
  $FutureProviderElement<CalendarScopeList> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<CalendarScopeList> create(Ref ref) {
    return calendarScopes(ref);
  }
}

String _$calendarScopesHash() => r'e9a5ea7efbf9aba9494c2e94cd67b5d479dab920';

@ProviderFor(calendarEvaluationTimeZone)
final calendarEvaluationTimeZoneProvider =
    CalendarEvaluationTimeZoneProvider._();

final class CalendarEvaluationTimeZoneProvider
    extends $FunctionalProvider<AsyncValue<String>, String, FutureOr<String>>
    with $FutureModifier<String>, $FutureProvider<String> {
  CalendarEvaluationTimeZoneProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calendarEvaluationTimeZoneProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calendarEvaluationTimeZoneHash();

  @$internal
  @override
  $FutureProviderElement<String> $createElement($ProviderPointer pointer) =>
      $FutureProviderElement(pointer);

  @override
  FutureOr<String> create(Ref ref) {
    return calendarEvaluationTimeZone(ref);
  }
}

String _$calendarEvaluationTimeZoneHash() =>
    r'fe8cdfe86de8e7f72f9d398f9e640a1299098188';

@ProviderFor(calendarEvent)
final calendarEventProvider = CalendarEventFamily._();

final class CalendarEventProvider
    extends
        $FunctionalProvider<
          AsyncValue<CalendarEvent>,
          CalendarEvent,
          FutureOr<CalendarEvent>
        >
    with $FutureModifier<CalendarEvent>, $FutureProvider<CalendarEvent> {
  CalendarEventProvider._({
    required CalendarEventFamily super.from,
    required String super.argument,
  }) : super(
         retry: null,
         name: r'calendarEventProvider',
         isAutoDispose: true,
         dependencies: null,
         $allTransitiveDependencies: null,
       );

  @override
  String debugGetCreateSourceHash() => _$calendarEventHash();

  @override
  String toString() {
    return r'calendarEventProvider'
        ''
        '($argument)';
  }

  @$internal
  @override
  $FutureProviderElement<CalendarEvent> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<CalendarEvent> create(Ref ref) {
    final argument = this.argument as String;
    return calendarEvent(ref, argument);
  }

  @override
  bool operator ==(Object other) {
    return other is CalendarEventProvider && other.argument == argument;
  }

  @override
  int get hashCode {
    return argument.hashCode;
  }
}

String _$calendarEventHash() => r'9dbdbece19669a8f944a8881a81b0a3f79fc3d02';

final class CalendarEventFamily extends $Family
    with $FunctionalFamilyOverride<FutureOr<CalendarEvent>, String> {
  CalendarEventFamily._()
    : super(
        retry: null,
        name: r'calendarEventProvider',
        dependencies: null,
        $allTransitiveDependencies: null,
        isAutoDispose: true,
      );

  CalendarEventProvider call(String id) =>
      CalendarEventProvider._(argument: id, from: this);

  @override
  String toString() => r'calendarEventProvider';
}

@ProviderFor(CalendarNotifier)
final calendarProvider = CalendarNotifierProvider._();

final class CalendarNotifierProvider
    extends $AsyncNotifierProvider<CalendarNotifier, CalendarEventList> {
  CalendarNotifierProvider._()
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

String _$calendarNotifierHash() => r'f48c9f655cc36ddebb8f95b76dfbb211e6c404a3';

abstract class _$CalendarNotifier extends $AsyncNotifier<CalendarEventList> {
  FutureOr<CalendarEventList> build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<AsyncValue<CalendarEventList>, CalendarEventList>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AsyncValue<CalendarEventList>, CalendarEventList>,
              AsyncValue<CalendarEventList>,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
