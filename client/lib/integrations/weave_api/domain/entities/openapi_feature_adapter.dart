enum OpenApiFeatureCapabilityState {
  available,
  disabled,
  disabledByPolicy,
  notConfigured,
  degraded,
  unavailable,
  comingLater,
  unsupported,
  unknown;

  static OpenApiFeatureCapabilityState fromApi(String? value) {
    switch (value) {
      case 'available':
      case 'usable':
      case 'ready':
        return OpenApiFeatureCapabilityState.available;
      case 'disabled':
        return OpenApiFeatureCapabilityState.disabled;
      case 'disabled_by_policy':
      case 'policy_blocked':
      case 'policy-blocked':
        return OpenApiFeatureCapabilityState.disabledByPolicy;
      case 'not_configured':
      case 'misconfigured':
        return OpenApiFeatureCapabilityState.notConfigured;
      case 'degraded':
        return OpenApiFeatureCapabilityState.degraded;
      case 'unavailable':
        return OpenApiFeatureCapabilityState.unavailable;
      case 'coming_later':
        return OpenApiFeatureCapabilityState.comingLater;
      case 'unsupported':
        return OpenApiFeatureCapabilityState.unsupported;
      default:
        return OpenApiFeatureCapabilityState.unknown;
    }
  }
}

class OpenApiFeatureCapability {
  const OpenApiFeatureCapability({required this.key, required this.state});

  final String key;
  final OpenApiFeatureCapabilityState state;
}

class OpenApiFeatureReadiness {
  const OpenApiFeatureReadiness({
    required this.featureKey,
    required this.state,
    required this.memberImpact,
    this.capabilities = const <OpenApiFeatureCapability>[],
    this.diagnosticsRedacted = true,
    this.supportSafe = true,
  });

  factory OpenApiFeatureReadiness.unknown(String featureKey) {
    return OpenApiFeatureReadiness(
      featureKey: featureKey,
      state: OpenApiFeatureCapabilityState.unknown,
      memberImpact: '$featureKey readiness is not present in the response.',
    );
  }

  final String featureKey;
  final OpenApiFeatureCapabilityState state;
  final String memberImpact;
  final List<OpenApiFeatureCapability> capabilities;
  final bool diagnosticsRedacted;
  final bool supportSafe;

  bool get isUsable => state == OpenApiFeatureCapabilityState.available;
}

class OpenApiResourcePage<TResource> {
  const OpenApiResourcePage({
    required this.featureKey,
    required this.resources,
    required this.readiness,
  });

  final String featureKey;
  final List<TResource> resources;
  final OpenApiFeatureReadiness readiness;
}
