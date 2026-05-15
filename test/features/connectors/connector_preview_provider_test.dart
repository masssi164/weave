import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/connectors/domain/entities/connector_preview.dart';
import 'package:weave/features/connectors/presentation/providers/connector_preview_provider.dart';

void main() {
  test('connector preview provider covers governed connector states', () {
    final container = ProviderContainer.test();
    addTearDown(container.dispose);

    final connectors = container.read(connectorPreviewProvider);
    expect(
      connectors.map((connector) => connector.status).toSet(),
      containsAll(<ConnectorPreviewStatus>{
        ConnectorPreviewStatus.disabled,
        ConnectorPreviewStatus.unavailable,
        ConnectorPreviewStatus.degraded,
        ConnectorPreviewStatus.actionRequired,
        ConnectorPreviewStatus.configured,
      }),
    );
    expect(
      connectors.where((connector) => !connector.providerActionsEnabled),
      isNotEmpty,
    );
    expect(
      connectors.any(
        (connector) =>
            connector.summary.toLowerCase().contains('token') &&
            connector.providerActionsEnabled,
      ),
      isFalse,
    );
  });
}
