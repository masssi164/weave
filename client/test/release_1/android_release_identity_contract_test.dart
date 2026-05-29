import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Android release identity contract', () {
    final buildGradle = File('../client/android/app/build.gradle.kts');
    final manifest = File('../client/android/app/src/main/AndroidManifest.xml');
    final mainActivity = File(
      '../client/android/app/src/main/kotlin/com/massimotter/weave/MainActivity.kt',
    );
    final oldMainActivity = File(
      '../client/android/app/src/main/kotlin/com/example/weave/MainActivity.kt',
    );
    final oidcConstants = File(
      '../client/lib/features/auth/domain/entities/oidc_constants.dart',
    );
    final readme = File('../client/README.md');
    final gitignore = File('../.gitignore');

    test('uses the Weave Android package id instead of template identity', () {
      final gradleText = buildGradle.readAsStringSync();
      final activityText = mainActivity.readAsStringSync();

      expect(gradleText, contains('namespace = "com.massimotter.weave"'));
      expect(gradleText, contains('applicationId = "com.massimotter.weave"'));
      expect(activityText, contains('package com.massimotter.weave'));
      expect(oldMainActivity.existsSync(), isFalse);
      expect(gradleText, isNot(contains('com.example.weave')));
    });

    test('keeps OIDC redirect assumptions aligned after package id change', () {
      final gradleText = buildGradle.readAsStringSync();
      final manifestText = manifest.readAsStringSync();
      final oidcText = oidcConstants.readAsStringSync();
      final readmeText = readme.readAsStringSync();

      expect(
        gradleText,
        contains('"appAuthRedirectScheme" to "com.massimotter.weave"'),
      );
      expect(
        manifestText,
        contains('android:scheme="\${appAuthRedirectScheme}"'),
      );
      expect(
        oidcText,
        contains("oidcRedirectScheme = 'com.massimotter.weave'"),
      );
      expect(oidcText, contains("'\$oidcRedirectScheme:/oauthredirect'"));
      expect(readmeText, contains('com.massimotter.weave:/oauthredirect'));
      expect(readmeText, contains('com.massimotter.weave:/logout'));
    });

    test('release signing fails closed without local credentials', () {
      final gradleText = buildGradle.readAsStringSync();
      final readmeText = readme.readAsStringSync();
      final gitignoreText = gitignore.readAsStringSync();

      expect(gradleText, contains('client/android/key.properties'));
      expect(gradleText, contains('storeFile'));
      expect(gradleText, contains('storePassword'));
      expect(gradleText, contains('keyAlias'));
      expect(gradleText, contains('keyPassword'));
      expect(gradleText, contains('Debug signing must not be used'));
      expect(gradleText, isNot(contains('signingConfigs.getByName("debug")')));
      expect(readmeText, contains('does not fall back to debug keys'));
      expect(readmeText, contains('client/android/key.properties'));
      expect(gitignoreText, contains('**/android/key.properties'));
      expect(gitignoreText, contains('**/android/*.jks'));
      expect(gitignoreText, contains('**/android/*.keystore'));
    });
  });
}
