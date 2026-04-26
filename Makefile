integration-test:
	@dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	bootstrap_env_default="$(CURDIR)/../weave-infra/weave-workspace/.generated/bootstrap.env"; \
	bootstrap_env="$${WEAVE_BOOTSTRAP_ENV:-$$bootstrap_env_default}"; \
	if [ ! -f "$$bootstrap_env" ] && [ -f "/tmp/weave-infra/weave-workspace/.generated/bootstrap.env" ]; then \
	  bootstrap_env="/tmp/weave-infra/weave-workspace/.generated/bootstrap.env"; \
	fi; \
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
	test_device="$${WEAVE_INTEGRATION_TEST_DEVICE:-$${FLUTTER_TEST_DEVICE:-macos}}"; \
	run_app_e2e="$${WEAVE_RUN_APP_E2E:-true}"; \
	if [ -f "$$bootstrap_env" ]; then \
	  . "$$bootstrap_env"; \
	fi; \
	if [ "$$caller_WEAVE_BASE_URL_set" = x ] && [ -n "$$caller_WEAVE_BASE_URL" ]; then WEAVE_BASE_URL="$$caller_WEAVE_BASE_URL"; fi; \
	if [ "$$caller_WEAVE_OIDC_ISSUER_URL_set" = x ] && [ -n "$$caller_WEAVE_OIDC_ISSUER_URL" ]; then WEAVE_OIDC_ISSUER_URL="$$caller_WEAVE_OIDC_ISSUER_URL"; fi; \
	if [ "$$caller_WEAVE_OIDC_CLIENT_ID_set" = x ] && [ -n "$$caller_WEAVE_OIDC_CLIENT_ID" ]; then WEAVE_OIDC_CLIENT_ID="$$caller_WEAVE_OIDC_CLIENT_ID"; fi; \
	if [ "$$caller_WEAVE_NEXTCLOUD_BASE_URL_set" = x ] && [ -n "$$caller_WEAVE_NEXTCLOUD_BASE_URL" ]; then WEAVE_NEXTCLOUD_BASE_URL="$$caller_WEAVE_NEXTCLOUD_BASE_URL"; fi; \
	if [ "$$caller_WEAVE_MATRIX_HOMESERVER_URL_set" = x ] && [ -n "$$caller_WEAVE_MATRIX_HOMESERVER_URL" ]; then WEAVE_MATRIX_HOMESERVER_URL="$$caller_WEAVE_MATRIX_HOMESERVER_URL"; fi; \
	if [ "$$caller_WEAVE_TEST_USERNAME_set" = x ] && [ -n "$$caller_WEAVE_TEST_USERNAME" ]; then WEAVE_TEST_USERNAME="$$caller_WEAVE_TEST_USERNAME"; fi; \
	if [ "$$caller_WEAVE_TEST_PASSWORD_set" = x ] && [ -n "$$caller_WEAVE_TEST_PASSWORD" ]; then WEAVE_TEST_PASSWORD="$$caller_WEAVE_TEST_PASSWORD"; fi; \
	WEAVE_BASE_URL="$${WEAVE_BASE_URL:-https://api.weave.local/api}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.local/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	WEAVE_NEXTCLOUD_BASE_URL="$${WEAVE_NEXTCLOUD_BASE_URL:-$${WEAVE_NEXTCLOUD_URL:-}}"; \
	WEAVE_MATRIX_HOMESERVER_URL="$${WEAVE_MATRIX_HOMESERVER_URL:-$${WEAVE_MATRIX_URL:-}}"; \
	printf '%s\n' \
	  '{' \
	  '  "WEAVE_BASE_URL": "'"$$WEAVE_BASE_URL"'",' \
	  '  "WEAVE_OIDC_ISSUER_URL": "'"$$WEAVE_OIDC_ISSUER_URL"'",' \
	  '  "WEAVE_OIDC_CLIENT_ID": "'"$$WEAVE_OIDC_CLIENT_ID"'",' \
	  '  "WEAVE_NEXTCLOUD_BASE_URL": "'"$$WEAVE_NEXTCLOUD_BASE_URL"'",' \
	  '  "WEAVE_MATRIX_HOMESERVER_URL": "'"$$WEAVE_MATRIX_HOMESERVER_URL"'",' \
	  '  "WEAVE_TEST_USERNAME": "'"$${WEAVE_TEST_USERNAME}"'",' \
	  '  "WEAVE_TEST_PASSWORD": "'"$${WEAVE_TEST_PASSWORD}"'"' \
	  '}' > "$$dart_defines_file"; \
	flutter test test/live_stack_contract_test.dart \
	  --dart-define-from-file="$$dart_defines_file" || exit $$?; \
	if [ "$$run_app_e2e" = "false" ]; then \
	  exit 0; \
	fi; \
	if [ "$${WEAVE_TEST_USERNAME}" = dummy ] || [ "$${WEAVE_TEST_PASSWORD}" = dummy ]; then \
	  echo "Dummy credentials: offline live-stack contract checks completed; skipping real live E2E."; \
	  exit 0; \
	fi; \
	for test_file in integration_test/app_test.dart integration_test/live_stack_app_e2e_test.dart; do \
	  flutter test "$$test_file" -d "$$test_device" \
	    --dart-define-from-file="$$dart_defines_file" || exit $$?; \
	done
