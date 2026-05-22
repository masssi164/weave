import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('provider stack app code uses Weave backend facades only', () {
    final clientSource = File(
      'lib/integrations/weave_api/data/services/weave_api_client.dart',
    ).readAsStringSync();

    expect(clientSource, contains("'api', 'providers', 'status'"));
    expect(clientSource, contains("'api', 'office', 'capabilities'"));
    expect(clientSource, contains("'api', 'office', 'launch'"));
    expect(clientSource, contains("'devops'"));

    final providerStackSources = Directory('lib')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'))
        .where((file) {
          final path = file.path.replaceAll('\\', '/');
          return path.contains('/provider_stack_') ||
              path.endsWith('/weave_api_client.dart') ||
              path.endsWith('/weave_api_provider.dart') ||
              path.endsWith('/channel_workspace.dart') ||
              path.endsWith('/channel_workspace_preview_provider.dart');
        })
        .map((file) => file.readAsStringSync())
        .join('\n');

    expect(providerStackSources, isNot(contains('gitlab.com/api')));
    expect(providerStackSources, isNot(contains('forgejo')));
    expect(providerStackSources, isNot(contains('/ocs/v2.php')));
    expect(providerStackSources, isNot(contains('/remote.php/dav')));
    expect(providerStackSources, isNot(contains('/onlyoffice/')));
  });
}
