import 'package:weave/features/boards/domain/entities/board_activity_event.dart';

abstract interface class BoardActivityEventNormalizer<TRawEvent> {
  Iterable<BoardActivityEvent<BoardActivityPayload>> normalize(TRawEvent raw);
}
