# Strategy Sprint: organization embedding and provider-neutrality proof

Status: proposed next sprint plan. This sprint should run before feature/provider implementation slices such as the Identity+Boards vertical mapping.

## Why this sprint exists

Weave's current direction is right, but the proof is not yet strong enough. Provider neutrality cannot be a slogan. It must survive real organization setup, existing IAM, mixed self-hosted/cloud/external services, provider replacement, deprovisioning, guests, service principals, and explainable policy.

This sprint makes Weave credible as an organization platform instead of another collaboration silo.

## Sprint goal

Produce a professional, implementation-ready foundation for:

- embedding Weave into existing organizations;
- bootstrapping new organizations from Weave defaults;
- mixing self-hosted, managed-cloud, and external providers;
- replacing adapters later without changing member workflows;
- explaining effective policy, readiness, risk, and loss before go-live.

## Non-goals

Do not build these during this strategy sprint:

- autonomous Weaver writes;
- connector marketplace/public SDK;
- broad Office/ONLYOFFICE integration;
- full Teams/Slack migration tooling;
- generic provider marketplace UI;
- private personal calendar ingestion;
- raw provider setup in the member client;
- preview/scaffold/coming-soon member UX.

## Required deliverables

1. `docs/organization-embedding-contract.md`
   - organization lifecycle;
   - existing-org vs new-org setup;
   - tenant/domain binding;
   - roles, groups, guests, service principals;
   - mixed deployment topologies;
   - anti-silo acceptance.

2. `docs/identity-provisioning-strategy.md`
   - OIDC/OAuth2+PKCE;
   - SAML;
   - SCIM 2.0;
   - LDAP/AD as upstream/bridge;
   - immutable identity keys;
   - deprovisioning;
   - conflict quarantine;
   - effective policy shape.

3. `docs/provider-replacement-and-anti-silo-contract.md`
   - capability/provider matrix;
   - adapter capability manifests;
   - replacement workflow;
   - dry-run/loss report schema;
   - mixed-provider examples;
   - support-safe diagnostics.

4. Existing docs linked and normalized:
   - `docs/index.md`;
   - `docs/product-line-and-weaver-plan.md`;
   - `docs/architecture.md`;
   - `docs/admin-provisioned-first-use.md`;
   - `docs/admin-operator-handbook.md`;
   - `docs/canonical-feature-models.md`.

5. Implementation issue plan:
   - one issue for organization lifecycle/admin bootstrap;
   - one issue for identity provisioning/effective policy;
   - one issue for provider replacement dry-run/loss reports;
   - one issue for member/admin leakage guards;
   - one issue to rescope the Identity+Boards vertical slice after this foundation.

## Acceptance criteria

### 1. Organization setup realism

Accepted when the docs explain:

- initial creation of a new organization;
- embedding into an existing organization;
- verified domains;
- identity provider selection;
- SCIM/LDAP/AD/OIDC/SAML paths;
- group/role mapping preview;
- guest and external-user policy;
- service principals;
- break-glass/last-admin protection;
- readiness before inviting members;
- reconciliation and deprovisioning.

### 2. Provider-neutrality proof

Accepted when:

- provider categories are first-class and consistent;
- self-hosted, Microsoft-heavy, and hybrid topologies are documented with equal seriousness;
- external providers are not "maybe later" placeholders;
- provider IDs stay backend/admin-side;
- member UX remains Weave-owned across provider choices.

### 3. Effective policy proof

Accepted when:

- effective policy response shape is documented;
- grants and denies can be explained by user, group, org role, context role, provider mapping, readiness, and policy;
- unknown groups/roles/providers deny by default;
- `operator` is distinct from `org_admin`;
- member tokens alone cannot access admin/provider/policy endpoints in later implementation.

### 4. Anti-silo proof

Accepted when each provider-backed category defines:

- source-of-truth;
- export/delete behavior;
- provenance;
- lossy-field handling;
- replacement/migration requirement;
- support-safe readiness;
- risk notes.

### 5. Evidence and gates

Minimum gates for this docs/contract sprint:

```sh
make docs-check
./gradlew acceptanceContract
```

If scenario mappings or release evidence code are touched, also run the specific affected Gradle gate. Full implementation sprints later should run the relevant `serverCi`, `clientCi`, `infraStatic`, or `ci` gates.

## Recommended implementation order after this sprint

1. Admin bootstrap and organization manifest schemas.
2. Identity provisioning dry-run and effective-policy API shape.
3. Role/operator/context permission separation across backend/client/tests.
4. Provider capability manifests and readiness snapshots.
5. Provider replacement dry-run and loss reports, Chat or Boards first.
6. Rescoped Identity+Boards vertical slice using the above contracts.

## Decision needed before implementation

Before coding, decide whether the first real implementation slice should prove:

- **Identity-first:** org bootstrap + SCIM/OIDC/SAML/LDAP mapping + effective policy; or
- **Provider-replacement-first:** Chat or Boards dry-run replacement/loss reporting using stub/existing providers.

Recommendation: start Identity-first. Without trustworthy identity and effective policy, every provider slice rests on sand.
