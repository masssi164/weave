/// Builds public Weave backend facade URIs from the configured backend base.
///
/// The organization contract supplies the canonical API base ending in `/api`.
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

  if (cleanedBaseSegments.isEmpty || cleanedBaseSegments.last != 'api') {
    throw ArgumentError.value(
      baseUrl,
      'baseUrl',
      'WEAVE_API_BASE_PATH_INVALID',
    );
  }
  if (cleanedPathSegments.first == 'api') {
    throw ArgumentError.value(
      pathSegments,
      'pathSegments',
      'WEAVE_API_RELATIVE_PATH_INVALID',
    );
  }

  return baseUrl.replace(
    pathSegments: [...cleanedBaseSegments, ...cleanedPathSegments],
    queryParameters: null,
    fragment: null,
  );
}
