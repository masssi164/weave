import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  group('admin-provisioned first-use boundary', () {
    final boundaryDoc = File('../docs/admin-provisioned-first-use.md');
    final releasePlan = File('../docs/release-v0.1-dogfood-plan.md');
    final roadmap = File('../docs/roadmap-and-guarded-surfaces.md');
    final quality = File('../docs/quality-and-evidence.md');
    final weaveControlContract = File(
      '../docs/weave-control-bootstrap-to-client-contract.md',
    );

    test('documents member vs admin/operator setup ownership', () {
      expect(boundaryDoc.existsSync(), isTrue);
      final markdown = boundaryDoc.readAsStringSync();

      for (final required in <String>[
        'Normal organization members land in an admin-provisioned workspace',
        'Normal members must not configure OIDC, realms, provider URLs, service endpoints, backup/restore, policy, or infrastructure readiness',
        'must not see provider setup diagnostics',
        'Workspace Health is the admin/operator control plane',
        'deploy-new, attach-existing, and hybrid bootstrap modes are Weave Control concepts only',
        '`owner`',
        '`admin`',
        '`operator`',
        '`member`',
        '`guest`',
        'infra/KEYCLOAK_CONTRACT.md',
        'infra/docs/admin-user-activation.md',
      ]) {
        expect(markdown, contains(required));
      }
    });

    test('defines release capability states without preview as a state', () {
      final taxonomy = _section(
        boundaryDoc.readAsStringSync(),
        '## Capability state taxonomy',
      );

      for (final state in <String>[
        '**Ready for users**',
        '**Admin setup required**',
        '**Disabled by policy**',
        '**Broken/degraded**',
        '**Not in this release**',
      ]) {
        expect(taxonomy, contains(state));
      }

      expect(taxonomy, contains('No release-scope product surface'));
      expect(taxonomy, isNot(contains('**Preview**')));
      expect(taxonomy, isNot(contains('**Scaffold**')));
      expect(taxonomy, isNot(contains('**Coming soon**')));
    });

    test('critical docs point setup/readiness to admins, not members', () {
      final release = releasePlan.readAsStringSync();
      final roadmapText = roadmap.readAsStringSync();
      final qualityText = quality.readAsStringSync();
      final controlText = weaveControlContract.readAsStringSync();

      expect(release, contains('without OIDC/provider/infra setup prompts'));
      expect(
        release,
        contains('members must not see provider setup diagnostics'),
      );
      expect(
        release,
        contains(
          'admins/operators use Workspace Health as the setup/readiness control plane',
        ),
      );

      expect(
        roadmapText,
        contains(
          'Provider stack readiness belongs in Workspace Health as the admin/operator control plane',
        ),
      );
      expect(
        roadmapText,
        contains('Normal members must not see provider setup diagnostics'),
      );
      expect(
        roadmapText,
        contains(
          'available product workflows or simple impact/fallback states only',
        ),
      );

      expect(qualityText, contains('Admin-provisioned first-use guard'));
      expect(
        qualityText,
        contains('Normal members must not see provider setup diagnostics'),
      );

      expect(controlText, contains('`deploy_new`'));
      expect(controlText, contains('`attach_existing`'));
      expect(controlText, contains('`hybrid`'));
      expect(
        controlText,
        contains('Weave Server stays separately deployable or attachable'),
      );
      expect(controlText, contains('Members never configure CI/CD targets'));
      expect(
        controlText,
        contains('dispatch_preflight_only'),
      );
      expect(
        controlText,
        contains('Flutter/App E2E is a separate client lane'),
      );
    });
  });
}

String _section(String markdown, String heading) {
  final start = markdown.indexOf(heading);
  if (start == -1) {
    fail('Missing heading: $heading');
  }

  int? nextHeading;
  for (final match in RegExp(
    r'^## ',
    multiLine: true,
  ).allMatches(markdown, start + heading.length)) {
    nextHeading = match.start;
    break;
  }

  return markdown.substring(start, nextHeading ?? markdown.length);
}
