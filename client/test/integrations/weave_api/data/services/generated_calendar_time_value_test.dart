import 'package:flutter_test/flutter_test.dart';
import 'package:weave/generated/user_api/api.dart' as user_api;

void main() {
  group('generated Calendar temporal wire values', () {
    // Execute with TZ=UTC, TZ=Europe/Berlin and TZ=Pacific/Apia.
    for (final date in ['2026-03-29', '2026-10-25', '2011-12-30']) {
      test('DATE preserves $date without host-zone normalization', () {
        final value = user_api.CalendarTimeValue.fromJson({
          'kind': 'DATE',
          'date': date,
        })!;

        expect(value.date!.isUtc, isTrue);
        expect(value.toJson()['date'], date);
        expect(value.toJson()['instant'], isNull);
      });
    }

    test('DATE created with local fields does not convert to an instant', () {
      final value = user_api.CalendarTimeValue(
        kind: user_api.CalendarTimeValueKindEnum.DATE,
        date: DateTime(2026, 10, 25),
      );

      expect(value.toJson()['date'], '2026-10-25');
    });

    test('impossible DATE does not normalize to a different civil day', () {
      final value = user_api.CalendarTimeValue.fromJson({
        'kind': 'DATE',
        'date': '2026-02-30',
      })!;

      expect(value.date, isNull);
    });

    test('UTC still preserves the instant across a host DST gap', () {
      final value = user_api.CalendarTimeValue.fromJson({
        'kind': 'UTC',
        'instant': '2026-03-29T02:30:00Z',
      })!;

      expect(value.instant, DateTime.utc(2026, 3, 29, 2, 30));
      expect(value.toJson()['instant'], '2026-03-29T02:30:00.000Z');
    });

    test('FLOATING and ZONED remain exact wall-clock strings', () {
      for (final kind in ['FLOATING', 'ZONED']) {
        final value = user_api.CalendarTimeValue.fromJson({
          'kind': kind,
          'localDateTime': '2026-03-29T02:30:00',
          'timeZone': kind == 'ZONED' ? 'America/New_York' : null,
        })!;

        expect(value.toJson()['localDateTime'], '2026-03-29T02:30:00');
        expect(
          value.toJson()['timeZone'],
          kind == 'ZONED' ? 'America/New_York' : null,
        );
      }
    });
  });
}
