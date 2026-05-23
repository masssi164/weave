/// Builds public Weave backend facade URIs from the configured backend base.
///
/// Local/dev defaults historically stored `https://api.weave.local` while the
/// binding product contract is `https://api.weave.local/api`. This helper keeps
/// both forms safe by appending the `/api` facade prefix only when it is not
/// already the last configured path segment.
Uri weaveApiUri(Uri baseUrl, Iterable<String> pathSegments) {
  final cleanedBaseSegments = baseUrl.pathSegments
      .where((segment) => segment.trim().isNotEmpty)
      .toList(growable: false);
  final cleanedPathSegments = pathSegments
      .where((segment) => segment.trim().isNotEmpty)
      .toList(growable: false);

  if (cleanedPathSegments.isEmpty) {
    return baseUrl;
  }

  final endpointSegments =
      cleanedBaseSegments.isNotEmpty &&
          cleanedBaseSegments.last == 'api' &&
          cleanedPathSegments.first == 'api'
      ? cleanedPathSegments.skip(1)
      : cleanedPathSegments;

  return baseUrl.replace(
    pathSegments: [...cleanedBaseSegments, ...endpointSegments],
    queryParameters: null,
    fragment: null,
  );
}
