import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/server_config/presentation/widgets/server_configuration_form.dart';
import 'package:weave/l10n/generated/app_localizations.dart';

Finder _textFieldWithLabel(String label) {
  return find.byWidgetPredicate(
    (widget) => widget is TextField && widget.decoration?.labelText == label,
  );
}

Widget _buildApp(ServerConfigurationFormLayout layout) {
  return ProviderScope(
    child: MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: ServerConfigurationForm(layout: layout)),
    ),
  );
}

void main() {
  group('ServerConfigurationForm', () {
    testWidgets(
      'uses next keyboard action for the issuer field in full layout',
      (tester) async {
        await tester.pumpWidget(_buildApp(ServerConfigurationFormLayout.full));
        await tester.pump();

        final issuerField = tester.widget<TextField>(
          _textFieldWithLabel('OIDC Issuer URL'),
        );

        expect(issuerField.textInputAction, TextInputAction.next);
      },
    );

    testWidgets(
      'uses done keyboard action when the issuer field is the last input',
      (tester) async {
        await tester.pumpWidget(
          _buildApp(ServerConfigurationFormLayout.providerAndIssuerOnly),
        );
        await tester.pump();

        final issuerField = tester.widget<TextField>(
          _textFieldWithLabel('OIDC Issuer URL'),
        );

        expect(issuerField.textInputAction, TextInputAction.done);
      },
    );
  });
}
