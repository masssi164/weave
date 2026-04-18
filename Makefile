integration-test:
	@dart_defines_file=$$(mktemp); \
	trap 'rm -f "$$dart_defines_file"' EXIT; \
	printf '%s\n' \
	  '{' \
	  '  "WEAVE_BASE_URL": "'"$${WEAVE_BASE_URL:-https://weave.local}"'",' \
	  '  "WEAVE_TEST_USERNAME": "'"$${WEAVE_TEST_USERNAME}"'",' \
	  '  "WEAVE_TEST_PASSWORD": "'"$${WEAVE_TEST_PASSWORD}"'"' \
	  '}' > "$$dart_defines_file"; \
	flutter test integration_test/ \
	  --dart-define-from-file="$$dart_defines_file"
