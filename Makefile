.PHONY: offline-contract-test acceptance-feature-mapping integration-contract-test integration-app-e2e integration-test marketing-screenshots

offline-contract-test: acceptance-feature-mapping
	@WEAVE_OFFLINE_CONTRACT_ONLY=true \
	WEAVE_BOOTSTRAP_ENV=/dev/null \
	WEAVE_TEST_USERNAME= \
	WEAVE_TEST_PASSWORD= \
	$(MAKE) integration-contract-test

acceptance-feature-mapping:
	@flutter test test/live_stack_feature_mapping_test.dart

integration-contract-test:
	@dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	bootstrap_env_default="$(CURDIR)/../weave-infra/weave-workspace/.generated/bootstrap.env"; \
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
	WEAVE_API_BASE_URL="$${WEAVE_API_BASE_URL:-$${WEAVE_BASE_URL:-https://api.weave.local/api}}"; \
	WEAVE_BASE_URL="$${WEAVE_API_BASE_URL}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.local/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL:-$${WEAVE_NEXTCLOUD_URL:-}}"; \
	WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL:-$${WEAVE_MATRIX_URL:-}}"; \
	if [ "$${WEAVE_OFFLINE_CONTRACT_ONLY:-false}" != "true" ] && { [ -z "$${WEAVE_TEST_USERNAME:-}" ] || [ -z "$${WEAVE_TEST_PASSWORD:-}" ]; }; then \
	  echo "Real WEAVE_TEST_USERNAME/WEAVE_TEST_PASSWORD are required for integration-contract-test. Use make offline-contract-test for the no-network PR-safe gate." >&2; \
	  exit 2; \
	fi; \
	printf '%s\n' \
	  "{" \
	  "  \"WEAVE_API_BASE_URL\": \"$$WEAVE_API_BASE_URL\"," \
	  "  \"WEAVE_BASE_URL\": \"$$WEAVE_BASE_URL\"," \
	  "  \"WEAVE_OIDC_ISSUER_URL\": \"$$WEAVE_OIDC_ISSUER_URL\"," \
	  "  \"WEAVE_OIDC_CLIENT_ID\": \"$$WEAVE_OIDC_CLIENT_ID\"," \
	  "  \"WEAVE_NEXTCLOUD_BASE_URL\": \"$$WEAVE_NEXTCLOUD_BASE_URL\"," \
	  "  \"WEAVE_MATRIX_HOMESERVER_URL\": \"$$WEAVE_MATRIX_HOMESERVER_URL\"," \
	  "  \"WEAVE_TEST_USERNAME\": \"$${WEAVE_TEST_USERNAME:-}\"," \
	  "  \"WEAVE_TEST_PASSWORD\": \"$${WEAVE_TEST_PASSWORD:-}\"," \
	  "  \"WEAVE_OFFLINE_CONTRACT_ONLY\": \"$${WEAVE_OFFLINE_CONTRACT_ONLY:-false}\"" \
	  "}" > "$$dart_defines_file"; \
	flutter test test/live_stack_contract_test.dart \
	  --dart-define-from-file="$$dart_defines_file"

integration-app-e2e:
	@dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	$(MAKE) integration-contract-test; \
	bootstrap_env_default="$(CURDIR)/../weave-infra/weave-workspace/.generated/bootstrap.env"; \
	bootstrap_env="$${WEAVE_BOOTSTRAP_ENV:-$$bootstrap_env_default}"; \
	if [ -f "$$bootstrap_env" ]; then \
	  . "$$bootstrap_env"; \
	fi; \
	WEAVE_API_BASE_URL="$${WEAVE_API_BASE_URL:-$${WEAVE_BASE_URL:-https://api.weave.local/api}}"; \
	WEAVE_BASE_URL="$${WEAVE_API_BASE_URL}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.local/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL:-$${WEAVE_NEXTCLOUD_URL:-}}"; \
	WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL:-$${WEAVE_MATRIX_URL:-}}"; \
	test_device="$${WEAVE_INTEGRATION_TEST_DEVICE:-$${FLUTTER_TEST_DEVICE:-macos}}"; \
	if [ -z "$${WEAVE_TEST_USERNAME:-}" ] || [ -z "$${WEAVE_TEST_PASSWORD:-}" ]; then \
	  echo "Real WEAVE_TEST_USERNAME/WEAVE_TEST_PASSWORD are required for integration-app-e2e." >&2; \
	  exit 2; \
	fi; \
	printf '%s\n' \
	  "{" \
	  "  \"WEAVE_API_BASE_URL\": \"$$WEAVE_API_BASE_URL\"," \
	  "  \"WEAVE_BASE_URL\": \"$$WEAVE_BASE_URL\"," \
	  "  \"WEAVE_OIDC_ISSUER_URL\": \"$$WEAVE_OIDC_ISSUER_URL\"," \
	  "  \"WEAVE_OIDC_CLIENT_ID\": \"$$WEAVE_OIDC_CLIENT_ID\"," \
	  "  \"WEAVE_NEXTCLOUD_BASE_URL\": \"$$WEAVE_NEXTCLOUD_BASE_URL\"," \
	  "  \"WEAVE_MATRIX_HOMESERVER_URL\": \"$$WEAVE_MATRIX_HOMESERVER_URL\"," \
	  "  \"WEAVE_TEST_USERNAME\": \"$${WEAVE_TEST_USERNAME}\"," \
	  "  \"WEAVE_TEST_PASSWORD\": \"$${WEAVE_TEST_PASSWORD}\"" \
	  "}" > "$$dart_defines_file"; \
	flutter test integration_test/live_stack_app_e2e_test.dart -d "$$test_device" \
	  --dart-define-from-file="$$dart_defines_file"

integration-test: acceptance-feature-mapping integration-app-e2e

marketing-screenshots:
	@python3 tool/generate_marketing_screenshots.py
