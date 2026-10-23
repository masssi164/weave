Feature: Authorized administration preserves canonical member identity
  The accepted identity lifecycle connects current authorized administration
  to the same account used by independently authenticated member applications.

  @admin-member-canonical-account-reference
  Scenario: Authorized administration links a current member to canonical Space identity
    Given a current organization administrator lists or reads a live member
    When Server projects the authorized member
    Then its account reference matches that member's independently authenticated User identity
    And email or display-name changes do not change the account reference
    And normal members and wrong-organization callers cannot obtain this projection
    And preparation verifies independent identity proof before assigning Space access
    And provider identifiers credentials and sessions remain private
