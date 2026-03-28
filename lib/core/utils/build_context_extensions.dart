import 'package:flutter/material.dart';

/// Compact extensions on [BuildContext] to reduce boilerplate
/// when accessing theme properties throughout the widget tree.
extension BuildContextX on BuildContext {
  ThemeData get theme => Theme.of(this);
  ColorScheme get colors => theme.colorScheme;
  TextTheme get textTheme => theme.textTheme;
  MediaQueryData get mq => MediaQuery.of(this);
}
