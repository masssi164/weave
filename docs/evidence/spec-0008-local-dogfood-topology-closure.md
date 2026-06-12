# Spec 0008 local dogfood topology closure evidence

Spec 0008 makes `weave.test` the only active local dogfood URL truth and treats obsolete `.local` / `weave.local` aliases as drift unless they appear in explicit rejection tests or non-domain identifiers.

## PR #723 reality check

- PR: https://github.com/masssi164/weave/pull/723
- State: merged
- Target branch: `main`
- Merge commit: `bdba20d7b178263cb6a3bc0f4787d346b80c95e2`
- Verification: GitHub reported `Gradle CI` success before merge.
- Current `origin/dev` contains that merge commit before this closure slice.

## Drift closure

The repository now includes `tools/forbidden_domain_drift_scan.py` as the repeatable forbidden-domain scan for active tracked repo text. The scan fails on obsolete `.local` hostnames, including `weave.local`, across specs, docs, code, config, tests, release fixtures, and workflow files. It intentionally allows package/local-name identifiers, `weave-local-ca` filenames, `weave-local-*` artifact identifiers, and explicit negative tests that prove legacy local hosts are rejected.

The active drift fixed in this slice was:

- provider-lab Synapse default server name: `weave-lab.local` -> `weave.test`
- disposable restore proof Matrix fixture: `restore-proof.local` -> `restore-proof.weave.test`
- raw-provider redaction and correlation negative-test examples: `.local` examples -> `.example.invalid`

## Evidence commands

```text
python3 tools/forbidden_domain_drift_scan.py
# No forbidden obsolete .local/weave.local domain drift found in tracked active repo text.
```

Before merge, this closure PR must also pass:

```text
git diff --check
./gradlew mcpDomainToolNameCheck specCorpusConformance specContract acceptanceContract docsCheck --console=plain
```
