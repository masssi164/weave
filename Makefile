.PHONY: ci client-ci server-ci infra-static admin-ci acceptance-contract live-stack-help

ci: acceptance-contract client-ci server-ci infra-static admin-ci

client-ci:
	$(MAKE) -C client offline-contract-test

server-ci:
	cd server && ./gradlew test

infra-static:
	@find infra/weave-workspace/tests -maxdepth 1 -type f -name '*-test.sh' -print0 | sort -z | xargs -0 -n1 bash

admin-ci:
	cd admin-console && npm run ci

acceptance-contract:
	cd client && dart run tool/acceptance_contract.dart guard --root .. --features e2e/features --mapping e2e/scenario_mappings.json

live-stack-help:
	@printf '%s\n' 'Live stack E2E is intentionally opt-in. Use the GitHub workflow with I_HAVE_SOLAR_STORAGE_BUDGET when runner power/storage budget is available.'
