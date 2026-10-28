import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../integration_test/dual_ios_collaboration_test.dart'
    show navigateNativeDualDestination;

void main() {
  for (final ignoreDuplicate in [true, false]) {
    testWidgets(
      ignoreDuplicate
          ? 'navigation taps the unique hittable target after scrolling adds an inactive duplicate'
          : 'navigation refuses two hittable targets introduced during scrolling',
      (tester) async {
        final scroll = ScrollController();
        final duplicate = ValueNotifier(false);
        addTearDown(scroll.dispose);
        addTearDown(duplicate.dispose);
        var currentTaps = 0;
        var duplicateTaps = 0;
        scroll.addListener(() => duplicate.value = true);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: ValueListenableBuilder<bool>(
                valueListenable: duplicate,
                builder: (context, showDuplicate, _) => Stack(
                  children: [
                    SingleChildScrollView(
                      controller: scroll,
                      child: Column(
                        children: [
                          const SizedBox(height: 1200),
                          Semantics(
                            identifier: 'nav-chat',
                            child: TextButton(
                              onPressed: () => currentTaps++,
                              child: const Text('Current Chat'),
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (showDuplicate)
                      Positioned(
                        top: 0,
                        left: 0,
                        child: IgnorePointer(
                          ignoring: ignoreDuplicate,
                          child: Semantics(
                            identifier: 'nav-chat',
                            child: TextButton(
                              onPressed: () => duplicateTaps++,
                              child: const Text('Outgoing Chat'),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final navigation = find.byWidgetPredicate(
          (widget) =>
              widget is Semantics && widget.properties.identifier == 'nav-chat',
        );
        expect(navigation, findsOneWidget);
        expect(navigation.hitTestable(), findsNothing);
        if (ignoreDuplicate) {
          await navigateNativeDualDestination(tester, Icons.chat_bubble_outline);
          expect(currentTaps, 1);
        } else {
          await expectLater(
            navigateNativeDualDestination(tester, Icons.chat_bubble_outline),
            throwsA(isA<TestFailure>()),
          );
          expect(currentTaps, 0);
        }
        expect(navigation, findsNWidgets(2));
        expect(duplicateTaps, 0);
        expect(tester.takeException(), isNull);
      },
    );
  }
}
