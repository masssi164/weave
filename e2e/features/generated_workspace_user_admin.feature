Feature: Member Home and private workspace diagnostics through generated Weave APIs
  One organization has a member Home and separately authorized operator diagnostics.

  @generated-workspace-user-admin-runtime
  Scenario: The member sees honest availability while the operator sees private setup checks
    Given a member and an operator have separate Weave sessions for the same organization
    When the member opens Home and the operator reviews workspace diagnostics
    Then Home keeps stable sections and shows no invented item counts or setup instructions
    And the operator sees the authorized capability policy and configuration checks
    And neither session can access the other API audience
    And the member Home remains valid after the isolated service restart
