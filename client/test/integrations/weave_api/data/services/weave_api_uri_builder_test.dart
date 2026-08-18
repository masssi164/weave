import 'package:flutter_test/flutter_test.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_uri_builder.dart';

void main() {
  group('weaveApiUri', () {
    test('appends an endpoint relative to the canonical api base', () {
      expect(
        weaveApiUri(Uri.parse('https://api.weave.test/api'), const [
          'calendar',
          'scopes',
        ]).toString(),
        'https://api.weave.test/api/calendar/scopes',
      );
    });

    test('rejects an obsolete API-host-only base', () {
      expect(
        () => weaveApiUri(Uri.parse('https://api.weave.test'), const [
          'calendar',
          'scopes',
        ]),
        throwsArgumentError,
      );
    });

    test('rejects endpoint paths that repeat the api base segment', () {
      expect(
        () => weaveApiUri(Uri.parse('https://api.weave.test/api'), const [
          'api',
          'calendar',
          'scopes',
        ]),
        throwsArgumentError,
      );
    });
  });
}
