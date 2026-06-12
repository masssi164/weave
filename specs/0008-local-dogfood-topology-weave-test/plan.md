# Plan

1. Scan active repo files with `rg weave.local` and migrate to `weave.test` where not historical.
2. Keep generated screenshots and fixtures deterministic.
3. Gate with `clientCi`, `acceptanceContract`, `specContract`, `infraStatic`, and CI.
