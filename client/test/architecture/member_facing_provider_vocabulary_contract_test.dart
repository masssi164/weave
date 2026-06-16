import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// Sprint 32 / issue #787: normal member evidence must speak in Weave
// product capabilities, while provider-specific vocabulary stays in explicit
// admin/operator/debug surfaces.
void main() {
  test('marketing screenshots avoid raw provider vocabulary', () {
    final auditedFiles = <File>[
      ...Directory('../docs/assets/marketing')
          .listSync()
          .whereType<File>()
          .where((file) => file.path.endsWith('.svg')),
    ];

    final forbiddenTerms = <String>[
      'Matrix',
      'Nextcloud',
      'Keycloak',
      'OIDC',
      'SAML',
      'SCIM',
      'LDAP',
      'CalDAV',
      'homeserver',
      'issuer',
      'client id',
      'service endpoint',
      'service endpoints',
    ];

    for (final file in auditedFiles) {
      final source = file.readAsStringSync();
      for (final term in forbiddenTerms) {
        expect(
          source.toLowerCase(),
          isNot(contains(term.toLowerCase())),
          reason:
              '${file.path} is member-facing marketing evidence and must use Weave domain vocabulary instead of `$term`.',
        );
      }
    }
  });
}
