const weaveE2eStackScope = String.fromEnvironment('WEAVE_E2E_STACK_SCOPE');

void requireIsolatedStackScope({String scope = weaveE2eStackScope}) {
  if (scope.trim() != 'isolated') {
    throw StateError(
      'Destructive live E2EE checks require WEAVE_E2E_STACK_SCOPE=isolated.',
    );
  }
}
