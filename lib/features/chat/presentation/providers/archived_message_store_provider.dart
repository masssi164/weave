import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:weave/core/persistence/shared_preferences_store.dart';
import 'package:weave/features/chat/data/services/archived_message_store.dart';

final archivedMessageStoreProvider = Provider<ArchivedMessageStore>((ref) {
  return ArchivedMessageStore(store: ref.watch(preferencesStoreProvider));
});
