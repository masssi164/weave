package com.massimotter.weave.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.bootstrap.BootstrapOwnerCredential;
import com.massimotter.weave.backend.model.identity.BootstrapOwnerInvitationRequest;
import com.massimotter.weave.backend.model.identity.MemberInvitationResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BootstrapOwnerInvitationControllerTest {
  private static final String TOKEN =
      "8ebf346fb3617f1e3356c59f337c6230601730c39e9f5adbfbb2a93b13cd99ec";
  private static final String IDEMPOTENCY_KEY = "bootstrap-owner-run-0001";

  @Mock BootstrapOwnerCredential credential;
  @Mock MemberInvitationService invitations;
  @InjectMocks BootstrapOwnerInvitationController controller;

  @Test
  void delegatesOnlyAfterTheSecretRefCredentialMatches() {
    BootstrapOwnerInvitationRequest request =
        new BootstrapOwnerInvitationRequest("owner@example.org", "Weave Owner");
    MemberInvitationResponse expected =
        new MemberInvitationResponse(
            "invitation-1",
            "organization-1",
            "owner@example.org",
            "Weave Owner",
            "pending",
            "pending",
            "owner",
            Instant.parse("2026-07-27T10:00:00Z"),
            Instant.parse("2026-07-26T10:00:00Z"),
            Instant.parse("2026-07-26T10:00:00Z"));
    when(credential.matches(TOKEN)).thenReturn(true);
    when(invitations.bootstrapOwner(request, IDEMPOTENCY_KEY)).thenReturn(expected);

    assertThat(controller.create(TOKEN, IDEMPOTENCY_KEY, request)).isEqualTo(expected);
    verify(invitations).bootstrapOwner(request, IDEMPOTENCY_KEY);
    verify(credential).deleteAfterSuccess();
  }

  @Test
  void rejectsAnIncorrectCredentialWithASupportSafeError() {
    when(credential.matches(TOKEN)).thenReturn(false);

    assertThatThrownBy(
            () ->
                controller.create(
                    TOKEN,
                    IDEMPOTENCY_KEY,
                    new BootstrapOwnerInvitationRequest(
                        "owner@example.org", "Weave Owner")))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(error.code()).isEqualTo("owner-bootstrap-unauthorized");
              assertThat(error.getMessage()).doesNotContain(TOKEN);
            });
    verify(credential, never()).deleteAfterSuccess();
  }

  @Test
  void failsClosedWhenTheSecretRefCannotBeRead() {
    when(credential.matches(TOKEN)).thenThrow(new IllegalStateException("private detail"));

    assertThatThrownBy(
            () ->
                controller.create(
                    TOKEN,
                    IDEMPOTENCY_KEY,
                    new BootstrapOwnerInvitationRequest(
                        "owner@example.org", "Weave Owner")))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(error.code()).isEqualTo("owner-bootstrap-unavailable");
              assertThat(error.getMessage()).doesNotContain("private detail");
            });
    verify(credential, never()).deleteAfterSuccess();
  }
}
