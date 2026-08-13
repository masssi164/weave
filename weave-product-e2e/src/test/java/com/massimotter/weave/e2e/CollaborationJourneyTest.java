package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CollaborationJourneyTest {

  @Test
  void classifiesOnlyAllowlistedServiceControlFailures() {
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR collaboration service control exceeded its bounded timeout"))
        .isEqualTo("control-timeout");
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR collaboration service control Docker operation exceeded its bounded timeout"))
        .isEqualTo("docker-timeout");
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR collaboration service restart identity did not advance exactly"))
        .isEqualTo("restart-identity");
  }

  @Test
  void doesNotExposeUntrustedControlOutput() {
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR provider payload token=secret https://private.example"))
        .isEqualTo("unspecified");
  }
}
