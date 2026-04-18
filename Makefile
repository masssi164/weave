integration-test:
	flutter test integration_test/ \
	  --dart-define=WEAVE_BASE_URL=$${WEAVE_BASE_URL:-https://weave.local} \
	  --dart-define=WEAVE_TEST_USERNAME=$${WEAVE_TEST_USERNAME} \
	  --dart-define=WEAVE_TEST_PASSWORD=$${WEAVE_TEST_PASSWORD}
