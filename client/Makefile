.PHONY: offline-contract-test acceptance-feature-mapping release-spine-contract integration-contract-test integration-app-e2e integration-multi-user-e2e integration-test marketing-screenshots

offline-contract-test: acceptance-feature-mapping
	@WEAVE_OFFLINE_CONTRACT_ONLY=true \
	WEAVE_BOOTSTRAP_ENV=/dev/null \
	WEAVE_TEST_USERNAME= \
	WEAVE_TEST_PASSWORD= \
	$(MAKE) integration-contract-test

acceptance-feature-mapping:
	@flutter test test/live_stack_feature_mapping_test.dart

release-spine-contract:
	@flutter test \
	  test/release_1/v0_1_release_spine_contract_test.dart \
	  test/release_1/ux_release_copy_contract_test.dart

integration-contract-test:
	@dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	bootstrap_env_default="$(CURDIR)/../infra/weave-workspace/.generated/bootstrap.env"; \
	bootstrap_env="$${WEAVE_BOOTSTRAP_ENV:-$$bootstrap_env_default}"; \
	caller_WEAVE_API_BASE_URL_set="$${WEAVE_API_BASE_URL+x}"; \
	caller_WEAVE_API_BASE_URL="$${WEAVE_API_BASE_URL-}"; \
	caller_WEAVE_BASE_URL_set="$${WEAVE_BASE_URL+x}"; \
	caller_WEAVE_BASE_URL="$${WEAVE_BASE_URL-}"; \
	caller_WEAVE_OIDC_ISSUER_URL_set="$${WEAVE_OIDC_ISSUER_URL+x}"; \
	caller_WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL-}"; \
	caller_WEAVE_OIDC_CLIENT_ID_set="$${WEAVE_OIDC_CLIENT_ID+x}"; \
	caller_WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID-}"; \
	caller_WEAVE_NEXTCLOUD_BASE_URL_set="$${WEAVE_NEXTCLOUD_BASE_URL+x}"; \
	caller_WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL-}"; \
	caller_WEAVE_MATRIX_HOMESERVER_URL_set="$${WEAVE_MATRIX_HOMESERVER_URL+x}"; \
	caller_WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL-}"; \
	caller_WEAVE_TEST_USERNAME_set="$${WEAVE_TEST_USERNAME+x}"; \
	caller_WEAVE_TEST_USERNAME="$${WEAVE_TEST_USERNAME-}"; \
	caller_WEAVE_TEST_PASSWORD_set="$${WEAVE_TEST_PASSWORD+x}"; \
	caller_WEAVE_TEST_PASSWORD="$${WEAVE_TEST_PASSWORD-}"; \
	caller_WEAVE_OFFLINE_CONTRACT_ONLY_set="$${WEAVE_OFFLINE_CONTRACT_ONLY+x}"; \
	caller_WEAVE_OFFLINE_CONTRACT_ONLY="$${WEAVE_OFFLINE_CONTRACT_ONLY-}"; \
	if [ -f "$$bootstrap_env" ]; then \
	  . "$$bootstrap_env"; \
	fi; \
	if [ "$$caller_WEAVE_API_BASE_URL_set" = x ] && [ -n "$$caller_WEAVE_API_BASE_URL" ]; then WEAVE_API_BASE_URL="$$caller_WEAVE_API_BASE_URL"; fi; \
	if [ "$$caller_WEAVE_BASE_URL_set" = x ] && [ -n "$$caller_WEAVE_BASE_URL" ]; then WEAVE_BASE_URL="$$caller_WEAVE_BASE_URL"; fi; \
	if [ "$$caller_WEAVE_OIDC_ISSUER_URL_set" = x ] && [ -n "$$caller_WEAVE_OIDC_ISSUER_URL" ]; then WEAVE_OIDC_ISSUER_URL="$$caller_WEAVE_OIDC_ISSUER_URL"; fi; \
	if [ "$$caller_WEAVE_OIDC_CLIENT_ID_set" = x ] && [ -n "$$caller_WEAVE_OIDC_CLIENT_ID" ]; then WEAVE_OIDC_CLIENT_ID="$$caller_WEAVE_OIDC_CLIENT_ID"; fi; \
	if [ "$$caller_WEAVE_NEXTCLOUD_BASE_URL_set" = x ] && [ -n "$$caller_WEAVE_NEXTCLOUD_BASE_URL" ]; then WEAVE_NEXTCLOUD_BASE_URL="$$caller_WEAVE_NEXTCLOUD_BASE_URL"; fi; \
	if [ "$$caller_WEAVE_MATRIX_HOMESERVER_URL_set" = x ] && [ -n "$$caller_WEAVE_MATRIX_HOMESERVER_URL" ]; then WEAVE_MATRIX_HOMESERVER_URL="$$caller_WEAVE_MATRIX_HOMESERVER_URL"; fi; \
	if [ "$$caller_WEAVE_TEST_USERNAME_set" = x ] && [ -n "$$caller_WEAVE_TEST_USERNAME" ]; then WEAVE_TEST_USERNAME="$$caller_WEAVE_TEST_USERNAME"; fi; \
	if [ "$$caller_WEAVE_TEST_PASSWORD_set" = x ] && [ -n "$$caller_WEAVE_TEST_PASSWORD" ]; then WEAVE_TEST_PASSWORD="$$caller_WEAVE_TEST_PASSWORD"; fi; \
	if [ "$$caller_WEAVE_OFFLINE_CONTRACT_ONLY_set" = x ] && [ -n "$$caller_WEAVE_OFFLINE_CONTRACT_ONLY" ]; then WEAVE_OFFLINE_CONTRACT_ONLY="$$caller_WEAVE_OFFLINE_CONTRACT_ONLY"; fi; \
	if [ "$${WEAVE_OFFLINE_CONTRACT_ONLY:-false}" = "true" ]; then WEAVE_TEST_USERNAME=""; WEAVE_TEST_PASSWORD=""; fi; \
	WEAVE_API_BASE_URL="$${WEAVE_API_BASE_URL:-$${WEAVE_BASE_URL:-https://api.weave.test/api}}"; \
	WEAVE_BASE_URL="$${WEAVE_API_BASE_URL}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.test/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL:-$${WEAVE_NEXTCLOUD_URL:-}}"; \
	WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL:-}"; \
	matrix_extra_root_base64=""; \
	if [ "$${WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED:-false}" = "true" ]; then \
	  matrix_extra_root_path="$${WEAVE_MATRIX_EXTRA_ROOT_CERTIFICATE_PATH:-}"; \
	  if [ ! -s "$$matrix_extra_root_path" ]; then \
	    echo "WEAVE_MATRIX_EXTRA_ROOT_CERTIFICATE_PATH must name a non-empty PEM file when live-test Matrix trust is enabled." >&2; \
	    exit 2; \
	  fi; \
	  matrix_extra_root_bytes=$$(wc -c < "$$matrix_extra_root_path" | tr -d '[:space:]'); \
	  if [ "$$matrix_extra_root_bytes" -gt 65536 ]; then \
	    echo "The live-test Matrix extra root exceeds the 64 KiB safety limit." >&2; \
	    exit 2; \
	  fi; \
	  matrix_extra_root_base64=$$(base64 < "$$matrix_extra_root_path" | tr -d '\n'); \
	fi; \
	if [ "$${WEAVE_OFFLINE_CONTRACT_ONLY:-false}" != "true" ] && { [ -z "$${WEAVE_TEST_USERNAME:-}" ] || [ -z "$${WEAVE_TEST_PASSWORD:-}" ]; }; then \
	  echo "Real WEAVE_TEST_USERNAME/WEAVE_TEST_PASSWORD are required for integration-contract-test. Use make offline-contract-test for the no-network PR-safe gate." >&2; \
	  exit 2; \
	fi; \
	jq -n \
	  --arg WEAVE_API_BASE_URL "$$WEAVE_API_BASE_URL" \
	  --arg WEAVE_BASE_URL "$$WEAVE_BASE_URL" \
	  --arg WEAVE_OIDC_ISSUER_URL "$$WEAVE_OIDC_ISSUER_URL" \
	  --arg WEAVE_OIDC_CLIENT_ID "$$WEAVE_OIDC_CLIENT_ID" \
	  --arg WEAVE_NEXTCLOUD_BASE_URL "$$WEAVE_NEXTCLOUD_BASE_URL" \
	  --arg WEAVE_MATRIX_HOMESERVER_URL "$$WEAVE_MATRIX_HOMESERVER_URL" \
	  --arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED "$${WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED:-false}" \
	  --arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_BASE64 "$$matrix_extra_root_base64" \
	  --arg WEAVE_TEST_USERNAME "$${WEAVE_TEST_USERNAME:-}" \
	  --arg WEAVE_TEST_PASSWORD "$${WEAVE_TEST_PASSWORD:-}" \
	  --arg WEAVE_OFFLINE_CONTRACT_ONLY "$${WEAVE_OFFLINE_CONTRACT_ONLY:-false}" \
	  '$$ARGS.named' > "$$dart_defines_file"; \
	flutter test test/live_stack_contract_test.dart \
	  --dart-define-from-file="$$dart_defines_file"

integration-app-e2e:
	@set -e; \
	dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	$(MAKE) integration-contract-test; \
	bootstrap_env_default="$(CURDIR)/../infra/weave-workspace/.generated/bootstrap.env"; \
	bootstrap_env="$${WEAVE_BOOTSTRAP_ENV:-$$bootstrap_env_default}"; \
	if [ -f "$$bootstrap_env" ]; then \
	  . "$$bootstrap_env"; \
	fi; \
	WEAVE_API_BASE_URL="$${WEAVE_API_BASE_URL:-$${WEAVE_BASE_URL:-https://api.weave.test/api}}"; \
	WEAVE_BASE_URL="$${WEAVE_API_BASE_URL}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.test/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL:-$${WEAVE_NEXTCLOUD_URL:-}}"; \
	WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL:-}"; \
	test_device="$${WEAVE_INTEGRATION_TEST_DEVICE:-$${FLUTTER_TEST_DEVICE:-macos}}"; \
	matrix_extra_root_base64=""; \
	if [ "$${WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED:-false}" = "true" ]; then \
	  matrix_extra_root_path="$${WEAVE_MATRIX_EXTRA_ROOT_CERTIFICATE_PATH:-}"; \
	  if [ ! -s "$$matrix_extra_root_path" ]; then \
	    echo "WEAVE_MATRIX_EXTRA_ROOT_CERTIFICATE_PATH must name a non-empty PEM file when live-test Matrix trust is enabled." >&2; \
	    exit 2; \
	  fi; \
	  matrix_extra_root_bytes=$$(wc -c < "$$matrix_extra_root_path" | tr -d '[:space:]'); \
	  if [ "$$matrix_extra_root_bytes" -gt 65536 ]; then \
	    echo "The live-test Matrix extra root exceeds the 64 KiB safety limit." >&2; \
	    exit 2; \
	  fi; \
	  matrix_extra_root_base64=$$(base64 < "$$matrix_extra_root_path" | tr -d '\n'); \
	fi; \
	if [ -z "$${WEAVE_TEST_USERNAME:-}" ] || [ -z "$${WEAVE_TEST_PASSWORD:-}" ]; then \
	  echo "Real WEAVE_TEST_USERNAME/WEAVE_TEST_PASSWORD are required for integration-app-e2e." >&2; \
	  exit 2; \
	fi; \
	if [ "$${WEAVE_E2E_STACK_SCOPE:-}" != "isolated" ]; then \
	  echo "integration-app-e2e requires WEAVE_E2E_STACK_SCOPE=isolated." >&2; \
	  exit 2; \
	fi; \
	jq -n \
	  --arg WEAVE_API_BASE_URL "$$WEAVE_API_BASE_URL" \
	  --arg WEAVE_BASE_URL "$$WEAVE_BASE_URL" \
	  --arg WEAVE_OIDC_ISSUER_URL "$$WEAVE_OIDC_ISSUER_URL" \
	  --arg WEAVE_OIDC_CLIENT_ID "$$WEAVE_OIDC_CLIENT_ID" \
	  --arg WEAVE_NEXTCLOUD_BASE_URL "$$WEAVE_NEXTCLOUD_BASE_URL" \
	  --arg WEAVE_MATRIX_HOMESERVER_URL "$$WEAVE_MATRIX_HOMESERVER_URL" \
	  --arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED "$${WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED:-false}" \
	  --arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_BASE64 "$$matrix_extra_root_base64" \
	  --arg WEAVE_E2E_STACK_SCOPE "$${WEAVE_E2E_STACK_SCOPE}" \
	  --arg WEAVE_TEST_USERNAME "$${WEAVE_TEST_USERNAME}" \
	  --arg WEAVE_TEST_PASSWORD "$${WEAVE_TEST_PASSWORD}" \
	  '$$ARGS.named' > "$$dart_defines_file"; \
	for test_file in \
	  integration_test/live_stack_app_e2e_test.dart \
	  integration_test/matrix_e2ee_live_e2e_test.dart \
	  integration_test/app_test.dart; do \
	  flutter test "$$test_file" -d "$$test_device" \
	    --dart-define-from-file="$$dart_defines_file"; \
	done

integration-multi-user-e2e:
	@set -e; \
	dart_defines_file=$$(mktemp); \
	raw_log=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file" "$$raw_log"' EXIT; \
	bootstrap_env_default="$(CURDIR)/../infra/weave-workspace/.generated/bootstrap.env"; \
	bootstrap_env="$${WEAVE_BOOTSTRAP_ENV:-$$bootstrap_env_default}"; \
	if [ -f "$$bootstrap_env" ]; then . "$$bootstrap_env"; fi; \
	WEAVE_API_BASE_URL="$${WEAVE_API_BASE_URL:-$${WEAVE_BASE_URL:-https://api.weave.test/api}}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.test/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL:-$${WEAVE_NEXTCLOUD_URL:-}}"; \
	WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL:-}"; \
	test_device="$${WEAVE_MULTI_USER_TEST_DEVICE:-$${WEAVE_INTEGRATION_TEST_DEVICE:-$${FLUTTER_TEST_DEVICE:-macos}}}"; \
	first_index="$${WEAVE_E2E_RUN_INDEX_FIRST:-1}"; \
	second_index="$${WEAVE_E2E_RUN_INDEX_SECOND:-2}"; \
	case "$$first_index" in ''|*[!0-9]*|0) echo "Multi-user run indexes must be positive integers." >&2; exit 2;; esac; \
	case "$$second_index" in ''|*[!0-9]*|0) echo "Multi-user run indexes must be positive integers." >&2; exit 2;; esac; \
	if [ "$$first_index" = "$$second_index" ]; then echo "Multi-user run indexes must be distinct." >&2; exit 2; fi; \
	if [ -z "$${WEAVE_E2E_RUN_ID:-}" ] || \
	   [ -z "$${WEAVE_E2E_AUTHOR_USERNAME:-}" ] || [ -z "$${WEAVE_E2E_AUTHOR_PASSWORD:-}" ] || \
	   [ -z "$${WEAVE_E2E_COLLABORATOR_USERNAME:-}" ] || [ -z "$${WEAVE_E2E_COLLABORATOR_PASSWORD:-}" ] || \
	   [ -z "$${WEAVE_E2E_OUTSIDER_USERNAME:-}" ] || [ -z "$${WEAVE_E2E_OUTSIDER_PASSWORD:-}" ]; then \
	  echo "Run ID plus author/collaborator/outsider credentials are required for integration-multi-user-e2e." >&2; exit 2; \
	fi; \
	if [ -z "$${WEAVE_CANDIDATE_COMMIT:-}" ] || [ -z "$${WEAVE_CANDIDATE_EVIDENCE_REF:-}" ]; then \
	  echo "Immutable candidate commit and evidence reference are required for integration-multi-user-e2e." >&2; exit 2; \
	fi; \
	case "$${WEAVE_E2E_MISSING_CAPABILITY_VERIFIED:-false}" in true|false) ;; *) echo "Missing-capability evidence must be true or false." >&2; exit 2;; esac; \
	case "$${WEAVE_E2E_EXPIRED_TOKEN_VERIFIED:-false}" in true|false) ;; *) echo "Expired-token evidence must be true or false." >&2; exit 2;; esac; \
	case "$${WEAVE_E2E_REVOKED_SESSION_VERIFIED:-false}" in true|false) ;; *) echo "Revoked-session evidence must be true or false." >&2; exit 2;; esac; \
	if [ "$${WEAVE_E2E_STACK_SCOPE:-}" != "isolated" ]; then echo "integration-multi-user-e2e requires WEAVE_E2E_STACK_SCOPE=isolated." >&2; exit 2; fi; \
	case "$${WEAVE_E2E_EXECUTION_MODE:-collaboration}" in collaboration|calendar-failure-containment) ;; *) echo "Unsupported WEAVE_E2E_EXECUTION_MODE." >&2; exit 2;; esac; \
	matrix_extra_root_base64=""; \
	if [ "$${WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED:-false}" = "true" ]; then \
	  matrix_extra_root_path="$${WEAVE_MATRIX_EXTRA_ROOT_CERTIFICATE_PATH:-}"; \
	  if [ ! -s "$$matrix_extra_root_path" ]; then echo "The configured Matrix test root must be a non-empty PEM file." >&2; exit 2; fi; \
	  matrix_extra_root_bytes=$$(wc -c < "$$matrix_extra_root_path" | tr -d '[:space:]'); \
	  if [ "$$matrix_extra_root_bytes" -gt 65536 ]; then echo "The Matrix test root exceeds the 64 KiB safety limit." >&2; exit 2; fi; \
	  matrix_extra_root_base64=$$(base64 < "$$matrix_extra_root_path" | tr -d '\n'); \
	fi; \
	overall_status=0; \
	for run_index in "$$first_index" "$$second_index"; do \
	  jq -n \
	    --arg WEAVE_API_BASE_URL "$$WEAVE_API_BASE_URL" \
	    --arg WEAVE_BASE_URL "$$WEAVE_API_BASE_URL" \
	    --arg WEAVE_OIDC_ISSUER_URL "$$WEAVE_OIDC_ISSUER_URL" \
	    --arg WEAVE_OIDC_CLIENT_ID "$$WEAVE_OIDC_CLIENT_ID" \
	    --arg WEAVE_NEXTCLOUD_BASE_URL "$$WEAVE_NEXTCLOUD_BASE_URL" \
	    --arg WEAVE_MATRIX_HOMESERVER_URL "$$WEAVE_MATRIX_HOMESERVER_URL" \
	    --arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED "$${WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_ENABLED:-false}" \
	    --arg WEAVE_MATRIX_LIVE_TEST_EXTRA_ROOT_BASE64 "$$matrix_extra_root_base64" \
	    --arg WEAVE_CANDIDATE_COMMIT "$$WEAVE_CANDIDATE_COMMIT" \
	    --arg WEAVE_CANDIDATE_EVIDENCE_REF "$$WEAVE_CANDIDATE_EVIDENCE_REF" \
	    --arg WEAVE_E2E_RUN_ID "$$WEAVE_E2E_RUN_ID" \
	    --arg WEAVE_E2E_RUN_INDEX "$$run_index" \
	    --arg WEAVE_E2E_AUTHOR_USERNAME "$$WEAVE_E2E_AUTHOR_USERNAME" \
	    --arg WEAVE_E2E_AUTHOR_PASSWORD "$$WEAVE_E2E_AUTHOR_PASSWORD" \
	    --arg WEAVE_E2E_COLLABORATOR_USERNAME "$$WEAVE_E2E_COLLABORATOR_USERNAME" \
	    --arg WEAVE_E2E_COLLABORATOR_PASSWORD "$$WEAVE_E2E_COLLABORATOR_PASSWORD" \
	    --arg WEAVE_E2E_OUTSIDER_USERNAME "$$WEAVE_E2E_OUTSIDER_USERNAME" \
	    --arg WEAVE_E2E_OUTSIDER_PASSWORD "$$WEAVE_E2E_OUTSIDER_PASSWORD" \
	    --arg WEAVE_E2E_MISSING_CAPABILITY_VERIFIED "$${WEAVE_E2E_MISSING_CAPABILITY_VERIFIED:-false}" \
	    --arg WEAVE_E2E_EXPIRED_TOKEN_VERIFIED "$${WEAVE_E2E_EXPIRED_TOKEN_VERIFIED:-false}" \
	    --arg WEAVE_E2E_REVOKED_SESSION_VERIFIED "$${WEAVE_E2E_REVOKED_SESSION_VERIFIED:-false}" \
	    --arg WEAVE_E2E_STACK_SCOPE "$${WEAVE_E2E_STACK_SCOPE}" \
	    --arg WEAVE_E2E_EXECUTION_MODE "$${WEAVE_E2E_EXECUTION_MODE:-collaboration}" \
	    '$$ARGS.named' > "$$dart_defines_file"; \
	  set +e; \
	  flutter test integration_test/multi_user_collaboration_e2e_test.dart -d "$$test_device" \
	    --dart-define-from-file="$$dart_defines_file" > "$$raw_log" 2>&1; \
	  test_status=$$?; \
	  set -e; \
	  if ! python3 tool/sanitize_multi_user_test_log.py --input "$$raw_log" --run-index "$$run_index" --test-exit-code "$$test_status"; then overall_status=1; fi; \
	  if [ "$$test_status" -ne 0 ]; then overall_status=1; fi; \
	done; \
	exit "$$overall_status"

integration-test: acceptance-feature-mapping release-spine-contract integration-app-e2e

marketing-screenshots:
	@python3 tool/generate_marketing_screenshots.py
