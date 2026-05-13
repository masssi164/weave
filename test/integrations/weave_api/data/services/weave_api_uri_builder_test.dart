import 'package:flutter_test/flutter_test.dart';
import 'package:weave/integrations/weave_api/data/services/weave_api_uri_builder.dart';

void main() {
  group('weaveApiUri', () {
    test(
      'adds the backend facade api prefix when the base is the API host',
      () {
        expect(
          weaveApiUri(Uri.parse('https://api.weave.local'), const [
            'api',
            'calendar',
            'events',
          ]).toString(),
          'https://api.weave.local/api/calendar/events',
        );
      },
    );

    test(
      'does not duplicate the api prefix when the base already includes it',
      () {
        expect(
          weaveApiUri(Uri.parse('https://api.weave.local/api'), const [
            'api',
            'calendar',
            'events',
          ]).toString(),
          'https://api.weave.local/api/calendar/events',
        );
      },
    );
  });
}
