@weave-spec-0006
Feature: WEAVE-SPEC-0006 acceptance

  No-unaccounted-loss portability manifest, lossy field accounting, and blocked broad lossless claims.

  @weave-spec-0006-portability-manifest-accounts-loss
  Scenario: Portability manifest accounts for preserved lossy and blocked records
    Given WEAVE-SPEC-0006 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_PORTABILITY_MANIFEST_ACCOUNTS_LOSS
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers preserved/lossy/blocked records
    And the catalog records the bounded domains provider-portability, decisions-evidence

  @weave-spec-0006-lossless-claim-blocked
  Scenario: Broad lossless provider-switch claims stay blocked without reconciliation evidence
    Given WEAVE-SPEC-0006 is the source of truth
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_LOSSLESS_CLAIM_BLOCKED
    When the product claim is evaluated for merge
    Then the claim is blocked unless the mapped evidence covers lossless claim blocked
    And the catalog records the bounded domains provider-portability, operator-release

  @weave-spec-0006-intent
  Scenario: Portability intent prevents unaccounted data loss before provider-switch claims
    Given WEAVE-SPEC-0006 defines Intent
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_INTENT_NO_UNACCOUNTED_LOSS
    When a product or release claim depends on Intent
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0006-scope
  Scenario: Portability separates preserved lossy blocked and out-of-scope transfer data
    Given WEAVE-SPEC-0006 defines In scope
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_SCOPE_BOUNDARY
    When a product or release claim depends on In scope
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0006-functional-requirements
  Scenario: Portability functional requirements are scenario-backed
    Given WEAVE-SPEC-0006 defines Functional requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_FUNCTIONAL_REQUIREMENTS
    When a product or release claim depends on Functional requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0006-support-redaction
  Scenario: Portability support evidence remains redacted and safe to review
    Given WEAVE-SPEC-0006 defines Support-safe redaction requirements
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_SUPPORT_REDACTION
    When a product or release claim depends on Support-safe redaction requirements
    Then the claim remains blocked until this scenario is mapped cataloged and test-guarded
    And the scenario evidence stays support-safe provider-neutral and offline-spec unless runtime proof is collected

  @weave-spec-0006-product-boundaries
  Scenario: Portability product boundaries reject unverified lossless migration promises
    Given WEAVE-SPEC-0006 defines Product boundaries
    And the acceptance scenario is mapped to offline spec evidence marker WEAVE_SPEC_0006_PRODUCT_BOUNDARIES
    When implementation or release evidence is reviewed against Product boundaries
    Then the claim remains blocked unless this scenario maps the requirement category explicitly
    And the catalog and Flutter mapping guard include the same marker and scenario name
