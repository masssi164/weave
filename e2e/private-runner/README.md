# Private Runner E2E

This directory is the test-first boundary for Weave's private execution plane and reduced provider stack.

## Profiles

- `runner-contract`: PostgreSQL, Weave Server, Weave MCP, one company-derived Runner, and one private internal API. It proves Runner contracts without paying the startup cost of collaboration providers.
- `provider-reference`: adds only the reference providers required by the product boundary: Keycloak, Nextcloud with Redis and Cron, and Tuwunel.
- `provider-cutover`: adds the optional Native Files provider to the reference graph. It does not add another IAM, Calendar, or Chat stack.

The private Runner and internal API publish no host ports. The Runner joins `runner-egress` to reach the Engine and the internal `company-private` network to reach company systems. The Engine never joins `company-private`.

## Test order

Run the fast structural contract first:

```bash
./gradlew privateRunnerStackContract
```

Run all currently executable Runner contracts:

```bash
./gradlew privateRunnerContractCi
```

Prepare disposable secrets and PKI before starting a profile manually:

```bash
bash e2e/private-runner/prepare-state.sh
docker compose \
  -f e2e/private-runner/compose.yaml \
  --profile runner-contract \
  config
```

The Gherkin file beside this document defines the full black-box journey before the Engine task API, MCP projection, and Runner image are complete. The journey becomes the required `privateRunnerE2e` gate only after its public boundaries exist; until then the draft PR must report the first missing executable boundary rather than claiming a live pass.

## Company extension contract

The fixture under `company-runner/` demonstrates the intended customer workflow:

1. inherit from the generic `weave-runner` image;
2. add a local capability bundle and JSON Schemas;
3. add company-owned handler binaries;
4. mount local credentials at runtime;
5. publish only the derived public capability bundle to the Engine.

The included handler accesses `internal-api` only through `company-private`; its token is mounted as a Docker secret and is never encoded in the capability metadata or task result.
