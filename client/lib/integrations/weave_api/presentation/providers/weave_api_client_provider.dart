import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:weave/integrations/weave_api/data/services/weave_api_client.dart';

final weaveApiHttpClientProvider = Provider<http.Client>((ref) {
  final client = http.Client();
  ref.onDispose(client.close);
  return client;
});

final weaveApiClientProvider = Provider<WeaveApiClient>((ref) {
  return HttpWeaveApiClient(httpClient: ref.watch(weaveApiHttpClientProvider));
});
