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
        return OpenApiFeatureCapabilityState.available;
      case 'disabled_by_policy':
        return OpenApiFeatureCapabilityState.disabledByPolicy;
      case 'not_configured':
        return OpenApiFeatureCapabilityState.notConfigured;
      case 'degraded':
        return OpenApiFeatureCapabilityState.degraded;
      case 'unavailable':
        return OpenApiFeatureCapabilityState.unavailable;
      case 'coming_later':
        return OpenApiFeatureCapabilityState.comingLater;
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
