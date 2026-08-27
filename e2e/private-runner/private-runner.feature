Feature: Provider-neutral collaboration with private Runner execution
  Weave keeps collaboration identity and task state in the Engine while
  organization code, internal endpoints, and local credentials remain in a
  company-derived Runner image behind an outbound-only connection.

  @private-runner-outbound-execution
  Scenario: OpenClaw receives a validated result from an internal company capability
    Given the company Runner enrolled with an access ID and one-time secret
    And the Runner published the public contract for "internal.asset.lookup@1.0.0"
    And the internal asset API is reachable only from the company-private network
    When OpenClaw invokes the capability through the Weave MCP endpoint
    Then the Engine creates one durable task and grants one fenced Runner lease
    And the Runner executes the local handler without an inbound connection
    And OpenClaw receives the schema-valid result and stable artifact links
    And no handler path, private endpoint, credential, or raw internal response leaves the Runner boundary

  @private-runner-evidence-context
  Scenario: Deterministic private observations become bounded agent context
    Given a company detector observes the Home Core service topology
    When the Runner submits an evidence-backed observation batch
    Then the Engine reconciles entities and relations idempotently by digest
    And the Context Compiler applies authorization before bounded graph traversal
    And OpenClaw receives only relevant facts, provenance, freshness, and weave resource links

  @reduced-provider-reference-stack
  Scenario: The reference stack uses only the selected collaboration providers
    Given the provider-reference profile is active
    Then Keycloak provides external OIDC
    And Nextcloud provides Files and Calendar
    And Tuwunel provides the Matrix Chat data plane
    And Weave Server, MCP, and the private Runner add no duplicate collaboration provider
    And Native Files is absent until the provider-cutover profile is selected

  @native-files-cutover-stack
  Scenario: The cutover profile adds Native Files without changing product identity
    Given stable Weave Resources are bound to Nextcloud Files
    When the provider-cutover profile activates Native Files
    Then the same Flutter routes, MCP schemas, resource IDs, and relations are used
    And a verified binding switch and rollback can be executed without starting another IAM or Chat stack
