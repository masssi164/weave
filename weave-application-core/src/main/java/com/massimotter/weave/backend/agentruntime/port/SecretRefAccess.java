package com.massimotter.weave.backend.agentruntime.port;

/**
 * Narrow callback access for a mounted secret; the resolver clears the supplied bytes afterward.
 */
public interface SecretRefAccess {
  <T> T withSecret(String credentialRef, SecretOperation<T> operation);

  @FunctionalInterface
  interface SecretOperation<T> {
    T apply(byte[] secret);
  }
}
