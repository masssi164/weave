package com.massimotter.weave.backend.agentruntime.adapter;

/**
 * Declares whether a file-backed SecretRef adapter is used by an offline
 * lifecycle owner or by the read-only server runtime.
 */
public enum FileSecretStoreAccess {
    READ_ONLY,
    READ_WRITE
}
