import 'package:weave/generated/openapi_models.dart' as openapi;
import 'package:weave/integrations/weave_api/domain/entities/openapi_feature_adapter.dart';

const _chatFeatureKey = 'chat';

extension ChatReadinessOpenApiMapper on openapi.ChatReadiness {
  OpenApiFeatureReadiness toFeatureReadiness() {
    return OpenApiFeatureReadiness(
      featureKey: _chatFeatureKey,
      state: OpenApiFeatureCapabilityState.fromApi(memberState),
      memberImpact: _fallbackText(
        memberImpact,
        'Weave Chat readiness unknown.',
      ),
      supportSafe: supportSafe ?? true,
      diagnosticsRedacted: downstreamDiagnosticsExposedToMember != true,
    );
  }
}

String _fallbackText(String? value, String fallback) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? fallback : trimmed;
}
