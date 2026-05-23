import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/bootstrap/presentation/bootstrap_gate.dart';
import 'package:weave/core/failures/app_failure.dart';

import '../../helpers/test_app.dart';

void main() {
  group('BootstrapGate', () {
    testWidgets('shows friendly shell error guidance with retry', (
      tester,
    ) async {
      var retried = false;

      await tester.pumpWidget(
        createTestApp(
          BootstrapGate.error(
            failure: const AppFailure.bootstrap('Backend health timed out.'),
            onRetry: () => retried = true,
          ),
        ),
      );

      expect(find.text('We could not get Weave ready'), findsOneWidget);
      expect(
        find.textContaining(
          'Try again. If this keeps happening, check that your workspace services are reachable.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Backend health timed out.'), findsOneWidget);

      await tester.tap(find.text('Retry'));
      expect(retried, isTrue);
    });
  });
}
