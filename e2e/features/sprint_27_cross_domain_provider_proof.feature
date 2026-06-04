Feature: Sprint 27 cross-domain provider proof

  @sprint27-calendar-provider-boundary
  Scenario: Calendar switch proof reports preserved and lossy fields
    Given Nextcloud CalDAV and Radicale provider manifests exist
    When Sprint 27 maps the redacted Calendar fixture through Weave Calendar objects
    Then WeaveCalendar, WeaveEvent, WeaveRecurrence, WeaveAttendee, WeaveResource, WeaveAvailability, and ProviderRef are covered
    And preserved fields and lossy fields are reported support-safely
    And the Weave Calendar UI remains domain-stable

  @sprint27-files-provider-boundary
  Scenario: Files switch proof validates metadata permissions and lossy cases
    Given Nextcloud and MinIO S3 provider manifests exist
    When Sprint 27 maps the redacted Files fixture through Weave Files objects
    Then WeaveDrive, WeaveFolder, WeaveFile, WeaveVersion, WeaveShare, WeavePermission, WeaveLock, WeaveQuota, and ProviderRef are covered
    And file metadata and permissions are validated
    And lossy cases are reported instead of hidden

  @sprint27-identity-provider-boundary
  Scenario: Identity switch proof names mapping risk boundaries without secrets
    Given Keycloak and Authentik provider manifests exist
    When Sprint 27 maps the redacted Identity fixture through Weave identity objects
    Then OIDC, SAML, roles, groups, user mapping, session boundaries, and rollback limits are covered
    And auth evidence contains no secrets, raw assertions, bearer tokens, or raw provider payloads

  @sprint27-provider-neutrality-claim-gate
  Scenario: Provider neutrality beyond chat stays scoped to evidence
    Given Calendar, Files, Identity, Chat, and setup-flow evidence are tracked separately
    When the Sprint 27 provider-neutrality claim gate evaluates release wording
    Then chat-only evidence is blocked from broad provider-neutrality claims
    And the scoreboard shows each domain reality level
    And setup-flow evidence is named separately from Calendar, Files, and Identity provider-boundary evidence
