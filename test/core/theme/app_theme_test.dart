import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/theme/app_theme.dart';

void main() {
  group('AppTheme', () {
    test('light theme uses Material 3 with light brightness', () {
      expect(AppTheme.light.useMaterial3, isTrue);
      expect(AppTheme.light.brightness, Brightness.light);
      expect(
        AppTheme.light.visualDensity,
        equals(VisualDensity.adaptivePlatformDensity),
      );
    });

    test('dark theme uses Material 3 with dark brightness', () {
      expect(AppTheme.dark.useMaterial3, isTrue);
      expect(AppTheme.dark.brightness, Brightness.dark);
      expect(
        AppTheme.dark.visualDensity,
        equals(VisualDensity.adaptivePlatformDensity),
      );
    });
  });
}
