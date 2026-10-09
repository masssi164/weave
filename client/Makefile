.PHONY: offline-contract-test acceptance-feature-mapping release-spine-contract integration-contract-test integration-test physical-device-auth-e2e physical-device-product-e2e physical-device-matrix-recovery-e2e marketing-screenshots

offline-contract-test: acceptance-feature-mapping integration-contract-test

acceptance-feature-mapping:
	@flutter test test/live_stack_feature_mapping_test.dart

release-spine-contract:
	@flutter test \
	  test/release_1/v0_1_release_spine_contract_test.dart \
	  test/release_1/ux_release_copy_contract_test.dart

integration-contract-test:
	@dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	jq -n \
	  --arg WEAVE_API_BASE_URL "$${WEAVE_API_BASE_URL:-https://api.weave.test/api}" \
	  --arg WEAVE_OIDC_ISSUER_URL "$${WEAVE_OIDC_ISSUER_URL:-https://auth.weave.test/realms/weave}" \
	  --arg WEAVE_OIDC_CLIENT_ID "$${WEAVE_OIDC_CLIENT_ID:-weave-app}" \
	  --arg WEAVE_OFFLINE_CONTRACT_ONLY "true" \
	  '$$ARGS.named' > "$$dart_defines_file"; \
	flutter test test/live_stack_contract_test.dart \
	  --dart-define-from-file="$$dart_defines_file"

integration-test: acceptance-feature-mapping release-spine-contract integration-contract-test

# No username, password, token, or custom TLS dart-defines are allowed here.
# The production flutter_appauth implementation opens the operating-system
# browser and Keycloak owns activation and authentication.
physical-device-auth-e2e:
	@set -e; \
	device_id="$${WEAVE_PHYSICAL_DEVICE_ID:-}"; \
	if [ -z "$$device_id" ]; then \
	  echo "WEAVE_PHYSICAL_DEVICE_ID is required." >&2; exit 2; \
	fi; \
	if ! flutter devices --machine | jq -e --arg id "$$device_id" \
	  '.[] | select(.id == $$id and .emulator == false)' >/dev/null; then \
	  echo "WEAVE_PHYSICAL_DEVICE_ID must identify a connected physical device." >&2; exit 2; \
	fi; \
	if [ "$$device_id" = "macos" ]; then \
	  PATH="$(CURDIR)/tool/native_macos_open:$$PATH"; export PATH; \
	fi; \
	dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	jq -n \
	  --arg WEAVE_API_BASE_URL "$${WEAVE_DEVICE_API_BASE_URL:?WEAVE_DEVICE_API_BASE_URL is required}" \
	  --arg WEAVE_OIDC_ISSUER_URL "$${WEAVE_DEVICE_OIDC_ISSUER_URL:?WEAVE_DEVICE_OIDC_ISSUER_URL is required}" \
	  --arg WEAVE_OIDC_CLIENT_ID "$${WEAVE_OIDC_CLIENT_ID:-weave-app}" \
	  --arg WEAVE_SYSTEM_BROWSER_AUTH_E2E "true" \
	  --arg WEAVE_SINGLE_SIGN_IN_PRODUCT_E2E "$${WEAVE_DEVICE_PRODUCT_E2E:-false}" \
	  --arg WEAVE_MATRIX_HOMESERVER_URL "$${WEAVE_DEVICE_MATRIX_HOMESERVER_URL:-}" \
	  --arg WEAVE_MEMBER_MATRIX_E2E "$${WEAVE_DEVICE_MATRIX_RECOVERY_E2E:-false}" \
	  --arg WEAVE_DISPOSABLE_MATRIX_MESSAGE_E2E "$${WEAVE_DEVICE_MATRIX_RECOVERY_E2E:-false}" \
	  --arg WEAVE_TWO_DEVICE_MATRIX_RECOVERY_E2E "$${WEAVE_DEVICE_MATRIX_RECOVERY_E2E:-false}" \
	  --arg WEAVE_DEVICE_DISPOSABLE_STACK "$${WEAVE_DEVICE_DISPOSABLE_STACK:-false}" \
	  --arg WEAVE_NATIVE_TEST_RUN_ID "$${WEAVE_NATIVE_TEST_RUN_ID:-}" \
	  '$$ARGS.named' > "$$dart_defines_file"; \
	if [ "$${WEAVE_DEVICE_PRODUCT_E2E:-false}" = "true" ]; then \
	  flutter test integration_test/system_browser_auth_e2e_test.dart \
	    -d "$$device_id" --dart-define-from-file="$$dart_defines_file" \
	    --plain-name 'one fresh sign-in makes Files Calendar and native Matrix usable'; \
	else \
	  flutter test integration_test/system_browser_auth_e2e_test.dart \
	    -d "$$device_id" --dart-define-from-file="$$dart_defines_file"; \
	fi

# This journey must start from a fresh app install against a disposable stack.
physical-device-product-e2e:
	@test "$${WEAVE_DEVICE_DISPOSABLE_STACK:-}" = "true" || \
	  { echo "WEAVE_DEVICE_DISPOSABLE_STACK=true is required for product E2E." >&2; exit 2; }
	@WEAVE_DEVICE_PRODUCT_E2E=true $(MAKE) physical-device-auth-e2e

# Only run this against a disposable Weave stack and physical test device.
# The second AppAuth login must obtain a distinct OIDC session for the same
# member. The test revokes its temporary Matrix device after recovery.
physical-device-matrix-recovery-e2e:
	@test "$${WEAVE_DEVICE_DISPOSABLE_STACK:-}" = "true" || \
	  { echo "WEAVE_DEVICE_DISPOSABLE_STACK=true is required for device revocation." >&2; exit 2; }
	@WEAVE_DEVICE_MATRIX_RECOVERY_E2E=true $(MAKE) physical-device-auth-e2e

marketing-screenshots:
	@python3 tool/generate_marketing_screenshots.py
