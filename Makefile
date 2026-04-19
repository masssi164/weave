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
	caller_WEAVE_TEST_USERNAME_set="$${WEAVE_TEST_USERNAME+x}"; \
	caller_WEAVE_TEST_USERNAME="$${WEAVE_TEST_USERNAME-}"; \
	caller_WEAVE_TEST_PASSWORD_set="$${WEAVE_TEST_PASSWORD+x}"; \
	caller_WEAVE_TEST_PASSWORD="$${WEAVE_TEST_PASSWORD-}"; \
	test_device="$${WEAVE_INTEGRATION_TEST_DEVICE:-$${FLUTTER_TEST_DEVICE:-macos}}"; \
	if [ -f "$$bootstrap_env" ]; then \
	  . "$$bootstrap_env"; \
	fi; \
	if [ "$$caller_WEAVE_BASE_URL_set" = x ]; then WEAVE_BASE_URL="$$caller_WEAVE_BASE_URL"; fi; \
	if [ "$$caller_WEAVE_OIDC_ISSUER_URL_set" = x ]; then WEAVE_OIDC_ISSUER_URL="$$caller_WEAVE_OIDC_ISSUER_URL"; fi; \
	if [ "$$caller_WEAVE_OIDC_CLIENT_ID_set" = x ]; then WEAVE_OIDC_CLIENT_ID="$$caller_WEAVE_OIDC_CLIENT_ID"; fi; \
	if [ "$$caller_WEAVE_TEST_USERNAME_set" = x ]; then WEAVE_TEST_USERNAME="$$caller_WEAVE_TEST_USERNAME"; fi; \
	if [ "$$caller_WEAVE_TEST_PASSWORD_set" = x ]; then WEAVE_TEST_PASSWORD="$$caller_WEAVE_TEST_PASSWORD"; fi; \
	WEAVE_BASE_URL="$${WEAVE_BASE_URL:-https://api.weave.local}"; \
	WEAVE_OIDC_ISSUER_URL="$${WEAVE_OIDC_ISSUER_URL:-https://keycloak.weave.local/realms/weave}"; \
	WEAVE_OIDC_CLIENT_ID="$${WEAVE_OIDC_CLIENT_ID:-weave-app}"; \
	printf '%s\n' \
	  '{' \
	  '  "WEAVE_BASE_URL": "'"$$WEAVE_BASE_URL"'",' \
	  '  "WEAVE_OIDC_ISSUER_URL": "'"$$WEAVE_OIDC_ISSUER_URL"'",' \
	  '  "WEAVE_OIDC_CLIENT_ID": "'"$$WEAVE_OIDC_CLIENT_ID"'",' \
	  '  "WEAVE_TEST_USERNAME": "'"$${WEAVE_TEST_USERNAME}"'",' \
	  '  "WEAVE_TEST_PASSWORD": "'"$${WEAVE_TEST_PASSWORD}"'"' \
	  '}' > "$$dart_defines_file"; \
	flutter test integration_test/ -d "$$test_device" \
	  --dart-define-from-file="$$dart_defines_file"
