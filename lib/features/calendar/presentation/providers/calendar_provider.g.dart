// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'calendar_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(calDavClient)
final calDavClientProvider = CalDavClientProvider._();

final class CalDavClientProvider
    extends $FunctionalProvider<CalDavClient, CalDavClient, CalDavClient>
    with $Provider<CalDavClient> {
  CalDavClientProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'calDavClientProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$calDavClientHash();

  @$internal
  @override
  $ProviderElement<CalDavClient> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  CalDavClient create(Ref ref) {
    return calDavClient(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(CalDavClient value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<CalDavClient>(value),
    );
  }
}

String _$calDavClientHash() => r'962ad8cc88b0a849e1d18cd1917c814dec6dfe6f';

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
    r'59521acab9f18ab5b82d10a1c4ab1768dac972cb';

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
