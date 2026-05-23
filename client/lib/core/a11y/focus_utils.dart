import 'package:flutter/widgets.dart';

/// Helpers for managing focus within multi-step flows and screen transitions.
abstract final class FocusUtils {
  /// Requests focus on the given [focusNode] after the current frame completes.
  ///
  /// This is useful when navigating to a new step or screen to ensure the
  /// first meaningful element receives keyboard focus.
  static void requestFocusAfterFrame(FocusNode focusNode) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (focusNode.canRequestFocus) {
        focusNode.requestFocus();
      }
    });
  }

  /// Unfocuses whatever currently has focus in the given [context].
  static void unfocus(BuildContext context) {
    FocusScope.of(context).unfocus();
  }
}
