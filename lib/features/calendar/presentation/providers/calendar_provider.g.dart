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

@ProviderFor(CalendarNotifier)
final calendarProvider = CalendarNotifierProvider._();

final class CalendarNotifierProvider
    extends $AsyncNotifierProvider<CalendarNotifier, List<CalendarEvent>> {
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

String _$calendarNotifierHash() => r'0be3b0fdd80f51576d1dffa1f46f79b0a3c7fcbb';

abstract class _$CalendarNotifier extends $AsyncNotifier<List<CalendarEvent>> {
  FutureOr<List<CalendarEvent>> build();
  @$mustCallSuper
  @override
  void runBuild() {
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
    element.handleCreate(ref, build);
  }
}
