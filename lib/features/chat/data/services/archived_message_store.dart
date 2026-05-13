import 'dart:convert';

import 'package:weave/core/persistence/preferences_store.dart';

class ArchivedMessageStore {
  const ArchivedMessageStore({required PreferencesStore store})
    : _store = store;

  static const storageKeyPrefix = 'chat.archived_messages.';

  final PreferencesStore _store;

  Future<Set<String>> loadArchivedMessageIds(String roomId) async {
    final raw = await _store.getString(_storageKey(roomId));
    if (raw == null || raw.isEmpty) {
      return <String>{};
    }

    final decoded = jsonDecode(raw);
    if (decoded is! List) {
      throw const FormatException('Archived message storage was invalid.');
    }

    return decoded.map((value) => value.toString()).toSet();
  }

  Future<void> archiveMessage({
    required String roomId,
    required String messageId,
  }) async {
    final archivedIds = await loadArchivedMessageIds(roomId);
    archivedIds.add(messageId);
    await _saveArchivedMessageIds(roomId, archivedIds);
  }

  Future<void> restoreMessage({
    required String roomId,
    required String messageId,
  }) async {
    final archivedIds = await loadArchivedMessageIds(roomId);
    archivedIds.remove(messageId);
    await _saveArchivedMessageIds(roomId, archivedIds);
  }

  Future<void> _saveArchivedMessageIds(
    String roomId,
    Set<String> archivedIds,
  ) async {
    if (archivedIds.isEmpty) {
      await _store.remove(_storageKey(roomId));
      return;
    }

    await _store.setString(
      _storageKey(roomId),
      jsonEncode(archivedIds.toList()..sort()),
    );
  }

  String _storageKey(String roomId) => '$storageKeyPrefix$roomId';
}
