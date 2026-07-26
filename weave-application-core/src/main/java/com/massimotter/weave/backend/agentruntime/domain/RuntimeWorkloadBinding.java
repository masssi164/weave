package com.massimotter.weave.backend.agentruntime.domain;

import java.util.regex.Pattern;

public record RuntimeWorkloadBinding(
    String issuer,
    String subject,
    String clientId,
    AuthenticationMethod authenticationMethod,
    String credentialRef) {

  private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
  private static final Pattern CREDENTIAL_REF =
      Pattern.compile("credentialref://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+");

  public RuntimeWorkloadBinding {
    RuntimeMemberBinding.requireHttpsUri(issuer, "workload issuer");
    RuntimeMemberBinding.requireText(subject, "workload subject");
    if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
      throw new IllegalArgumentException(
          "workload clientId must use the weaver-cell-{id} namespace");
    }
    if (authenticationMethod == null) {
      throw new IllegalArgumentException("workload authenticationMethod is required");
    }
    if (credentialRef == null || !CREDENTIAL_REF.matcher(credentialRef).matches()) {
      throw new IllegalArgumentException("workload credentialRef must be a credentialref URI");
    }
  }

  public enum AuthenticationMethod {
    PRIVATE_KEY_JWT,
    CLIENT_SECRET_BASIC
  }
}
