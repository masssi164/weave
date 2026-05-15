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
    r'afaed17dbe52673ec80727500a033f2346e2f6db';

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

@ProviderFor(calendarClientSetup)
final calendarClientSetupProvider = CalendarClientSetupProvider._();

final class CalendarClientSetupProvider
    extends
        $FunctionalProvider<
          AsyncValue<CalendarClientSetup>,
          CalendarClientSetup,
          FutureOr<CalendarClientSetup>
        >
    with
        $FutureModifier<CalendarClientSetup>,
        $FutureProvider<CalendarClientSetup> {
  CalendarClientSetupProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calendarClientSetupProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calendarClientSetupHash();

  @$internal
  @override
  $FutureProviderElement<CalendarClientSetup> $createElement(
    $ProviderPointer pointer,
  ) => $FutureProviderElement(pointer);

  @override
  FutureOr<CalendarClientSetup> create(Ref ref) {
    return calendarClientSetup(ref);
  }
}

String _$calendarClientSetupHash() =>
    r'cfa2310a5679c86ccef7cfbaf2936cb61c814578';

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

String _$calendarEventHash() => r'637cafd78690cae4821a18608f2292bd25521592';

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

String _$calendarNotifierHash() => r'd019ac3247543c39dc76dd552259e82227c7fe02';

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
