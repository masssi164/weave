import 'package:flutter_test/flutter_test.dart';
import 'package:weave/integrations/rust_matrix_core/data/services/matrix_identity_projection.dart';

void main() {
  test('uses the full issuer and subject in the stable Matrix account ID', () {
    final issuer = Uri.parse('https://auth.example.invalid/realms/weave');
    final homeserver = Uri.parse('https://api.weave.test');
    expect(
      matrixUserIdForMember(issuer, 'user@example.com', homeserver),
      '@acct_c0d500fee3a1efb1aa74f6432cc73bbb:api.weave.test',
    );
    final upper = matrixUserIdForMember(issuer, 'User', homeserver);
    final lower = matrixUserIdForMember(issuer, 'user', homeserver);
    expect(upper, isNot(lower));
    expect(
      matrixUserIdForMember(issuer, 'a:x', homeserver),
      isNot(matrixUserIdForMember(issuer, 'b:x', homeserver)),
    );
    expect(
      lower,
      isNot(
        matrixUserIdForMember(
          Uri.parse('https://other.example/realms/weave'),
          'user',
          homeserver,
        ),
      ),
    );
  });
}
