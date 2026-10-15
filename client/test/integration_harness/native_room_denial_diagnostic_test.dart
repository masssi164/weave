import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/chat/domain/entities/chat_failure.dart';
import 'package:weave/features/chat/domain/entities/chat_room_timeline.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/rust_matrix_core_bridge.dart';

import '../../integration_test/system_browser_auth_e2e_test.dart'
    show nativeRoomDenialProbe;

void main() {
  test(
    'room denial diagnostic preserves original failure and stack without private payload',
    () async {
      final records = <String>[];
      final previous = debugPrint;
      debugPrint = (message, {wrapWidth}) {
        if (message != null) records.add(message);
      };
      addTearDown(() => debugPrint = previous);
      for (final code in [
        'M_WEAVE_CHAT_JOIN_REQUIRED',
        'private-token https://private.invalid/room',
      ]) {
        final cause = RustMatrixCoreBridgeException(code);
        final failure = ChatFailure.protocol(
          'private room title and identity',
          cause: cause,
        );
        final stack = StackTrace.fromString('original private stack');
        try {
          await nativeRoomDenialProbe(
            Future<ChatRoomTimeline>.error(failure, stack),
          );
          fail('Denial was swallowed.');
        } catch (error, observedStack) {
          expect(error, same(failure));
          expect(observedStack.toString(), stack.toString());
        }
      }
      expect(records, [
        'NATIVE_PRODUCT_STAGE phase=room-denial-observed type=protocol causeCode=M_WEAVE_CHAT_JOIN_REQUIRED supportSafe=true',
        'NATIVE_PRODUCT_STAGE phase=room-denial-observed type=protocol causeCode=unavailable supportSafe=true',
      ]);
      expect(records.join(), isNot(contains('private')));
    },
  );

  test('secondary diagnostic failure never replaces room denial', () async {
    final previous = debugPrint;
    debugPrint = (message, {wrapWidth}) =>
        throw StateError('private logger error');
    addTearDown(() => debugPrint = previous);
    const failure = ChatFailure.sessionRequired('private explanation');
    await expectLater(
      nativeRoomDenialProbe(Future<ChatRoomTimeline>.error(failure)),
      throwsA(same(failure)),
    );
  });

  test('successful timeline probe remains successful and silent', () async {
    final records = <String>[];
    final previous = debugPrint;
    debugPrint = (message, {wrapWidth}) {
      if (message != null) records.add(message);
    };
    addTearDown(() => debugPrint = previous);
    const timeline = ChatRoomTimeline(
      roomId: 'owned',
      roomTitle: 'private',
      isInvite: false,
      canSendMessages: true,
      messages: [],
    );
    expect(await nativeRoomDenialProbe(Future.value(timeline)), same(timeline));
    expect(records, isEmpty);
  });
}
