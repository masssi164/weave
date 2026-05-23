import 'package:flutter_test/flutter_test.dart';
import 'package:weave/core/permissions/workspace_role.dart';

void main() {
  group('WorkspaceRoleList', () {
    test('allows owner, admin, and operator to administer workspace setup', () {
      expect(['owner'].canAdministerWorkspace, isTrue);
      expect(['admin'].canAdministerWorkspace, isTrue);
      expect(['operator'].canAdministerWorkspace, isTrue);
      expect(['member'].canAdministerWorkspace, isFalse);
      expect(['guest'].canAdministerWorkspace, isFalse);
      expect(<String>[].canAdministerWorkspace, isFalse);
    });

    test(
      'normalizes role strings and preserves the strongest primary role',
      () {
        expect(['Member', 'OWNER'].canAdministerWorkspace, isTrue);
        expect(['member', 'admin'].primaryWorkspaceRole, 'admin');
        expect(['member', 'operator'].primaryWorkspaceRole, 'operator');
        expect(['unknown-role'].primaryWorkspaceRole, 'unknown');
      },
    );
  });
}
