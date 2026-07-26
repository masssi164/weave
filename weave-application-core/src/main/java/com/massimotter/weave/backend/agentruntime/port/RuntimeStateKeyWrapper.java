package com.massimotter.weave.backend.agentruntime.port;

/** Provider-neutral envelope-key boundary. Raw wrapping keys never cross this port. */
public interface RuntimeStateKeyWrapper {
  WrappedDataKey wrap(byte[] dataKey, byte[] authenticatedContext);

  byte[] unwrap(WrappedDataKey wrappedDataKey, byte[] authenticatedContext);

  KeyReadiness readiness();

  record WrappedDataKey(String keyRef, byte[] wrappedKey) {
    public WrappedDataKey {
      if (keyRef == null || keyRef.isBlank() || wrappedKey == null || wrappedKey.length == 0) {
        throw new IllegalArgumentException("wrapped data key and key reference are required");
      }
      wrappedKey = wrappedKey.clone();
    }

    @Override
    public byte[] wrappedKey() {
      return wrappedKey.clone();
    }
  }

  record KeyReadiness(boolean ready, String activeKeyRefHash) {
    public KeyReadiness {
      if (ready && (activeKeyRefHash == null || !activeKeyRefHash.matches("sha256:[a-f0-9]{64}"))) {
        throw new IllegalArgumentException(
            "ready key custody requires a support-safe key reference hash");
      }
      if (!ready) {
        activeKeyRefHash = null;
      }
    }
  }
}
