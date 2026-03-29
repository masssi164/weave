import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/files/presentation/files_screen.dart';

import '../../helpers/test_app.dart';

void main() {
  group('FilesScreen', () {
    testWidgets('renders without errors', (tester) async {
      await tester.pumpWidget(createTestApp(const FilesScreen()));
      await tester.pumpAndSettle();
    });

    testWidgets('meets androidTapTargetGuideline', (tester) async {
      await tester.pumpWidget(createTestApp(const FilesScreen()));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    });

    testWidgets('meets labeledTapTargetGuideline', (tester) async {
      await tester.pumpWidget(createTestApp(const FilesScreen()));
      await tester.pumpAndSettle();

      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    });
  });
}
