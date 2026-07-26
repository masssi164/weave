package com.massimotter.weave.backend.model;

public final class IdentityKeyFormat {

  public static final int MAX_ISSUER_LENGTH = 384;
  public static final int MAX_SUBJECT_LENGTH = 128;
  public static final int MAX_PRIMARY_IDENTITY_KEY_LENGTH = 528;
  public static final String PRIMARY_IDENTITY_KEY_PATTERN =
      "issuer\\+subject:[^#\\s]{1,384}#[^#\\s]{1,128}";

  private IdentityKeyFormat() {}
}
