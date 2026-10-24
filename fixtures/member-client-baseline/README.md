# Shared member client scenario

`v1.json` describes the normal user journey shared by product preparation, two
independent iOS simulator actors, and ordinary Dogfood acceptance. It fixes the
three required domains, the organization default Space, two **member** roles,
current `VIEW`/`EDIT` grants, and run-scoped Chat, Files, and Calendar content.

Resolve the input through the one compiler:

```sh
python3 tools/member_test_scenario.py --run-id member-candidate-1
```

Python consumers import `resolve_scenario`, `scenario_sha256`,
`validate_resolved_scenario`, and `validate_member_bindings` from
`tools/member_test_scenario.py`. JVM preparation calls the same compiler through
`MemberTestScenario`. The native app receives its resolved input at runtime, so
the collaboration binary contains no run identity and can execute another
instance without rebuilding. Its runtime fixture must agree with the profile,
and the actual signed-in organization role must be `member`.

The recipe contains no real identities, credentials, tokens, provider endpoints,
or opaque product resource IDs. A preparation owner resolves logical
`member-a`, `member-b`, and `shared-space` references against the intended
environment. Before acceptance it reads back each current organization role,
the account identity, the member-visible default Space, and the exact current
grant plus its strong version. The private `memberBindings` field records those
observations; it does not confer access. The two members have distinct subjects
and account references, with no administrator grant or Weaver entitlement needed
for the user journey.

## Shared input, isolated execution

Each execution has its own organization/account bindings and namespaced content.
Integration and simulator runs use disposable data; ordinary Dogfood and physical
device work retain their designated prepared accounts and the owner's existing
state. The contract is shared, not a mutable database or a credential file.
Changing a content template changes the canonical `scenarioSha256`, which is
bound to the execution evidence. Stale or locally invented resolved content is
rejected rather than completed with defaults.

The acceptance actions still use normal app PKCE, the Weave User API and its
published Matrix endpoint. The template does not write product storage or inject
app sessions. Administrative preparation and current-grant revocation probes use
a separate setup actor. Specialized owner, guest denial, MCP, provider lifecycle,
and full onboarding scenarios remain additional coverage and do not redefine the
ordinary member roles in this profile.

Passing recipe validation proves input consistency only. Real two-user UI
collaboration and its recovery/readback checks must pass before a physical owner
handoff is represented as ready.

## Targeted product flow

`-Dweave.e2e.mode=member-collaboration` selects the ordinary member acceptance
path in `FreshProductFlow`. On the owned disposable E2E stack it automatically
prepares a separate setup owner and denied guest using the established normal
activation flow, configures the three domains and default Space, and runs
`compose.sh e2e provision-members` to prepare A/B through the bounded helper.
It then verifies fresh PKCE identities/current Space access and runs the real
two-actor native collaboration. No full Admin Console or MCP acceptance journey
runs before that client check. The default `full` mode retains that specialized
coverage.

All prepared identities and logs live in a run-specific private directory below
`client/build/private-ios-qualification/`, outside uploaded product evidence.
Reusing an explicitly prepared isolated setup is possible by supplying both
`weave.e2e.prepared-member-input` (the helper's private
`weave.member-collaboration-input/v1` output) and
`weave.e2e.prepared-probe-input` (`weave.member-collaboration-probes/v1`). The
latter contains `runId`, `organizationId`, and separate `admin`/`denied` login
objects with `email`, `password`, and actual OIDC `identityRef`. Neither input
may substitute a probe identity for A/B; the live login role and canonical
account must match before any collaboration action.

The targeted output is `weave.test-app-product-flow/v4-members`. It records
source/specification/artifact binding, the scenario hash, hashed actor identities,
ordinary role and current Space checks, and successful native collaboration.
It makes no owner HIL, full onboarding, administrative, or MCP acceptance claim.
