import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { matchesConversation } from '../src/features/chats/conversation-filters.js';

// Register the actual factory without requiring AngularJS's browser DOM in Node.
function createStore() {
  let store;
  const source = readFileSync(
    new URL('../src/features/chats/chat-state.service.js', import.meta.url),
    'utf8',
  ).replace(/^import .*;\n/gm, '');
  const angular = {
    module: () => ({
      factory: (_name, factory) => {
        store = factory();
      },
    }),
  };
  vm.runInNewContext(source, { angular, matchesConversation });
  store.state.readerId = 1;
  return store;
}
const conversation = (id = 'one', unreadCount = 3) => ({
  id,
  unreadCount,
  status: 'PENDING',
  version: 1,
  updatedAt: '2026-10-23T12:00:00Z',
});
const read = (store) =>
  store.read({
    memberId: 1,
    conversationId: 'one',
    lastReadAt: '2026-10-23T12:00:00Z',
    throughMessageId: '123',
    unreadCount: 0,
  });

test('loading another page preserves the confirmed unread count', () => {
  const store = createStore();
  store.replace([conversation()], store.revision());
  store.select({ ...store.state.conversations[0] });
  read(store);
  store.replace([...store.state.conversations, conversation('two')], store.revision());
  assert.equal(store.state.unread.one, 0);
  assert.equal(store.state.conversations.find((row) => row.id === 'one').unreadCount, 0);
  assert.equal(store.state.selected.unreadCount, 0);
});

test('a response started before a read cannot restore stale counts on a later page', () => {
  const store = createStore();
  store.replace([conversation()], store.revision());
  const requestedAt = store.revision();
  read(store);
  store.replace([conversation()], requestedAt);
  store.replace([...store.state.conversations, conversation('two')], store.revision());
  assert.equal(store.state.unread.one, 0);
  assert.equal(store.state.conversations.find((row) => row.id === 'one').unreadCount, 0);
  // A fresh server response can still report newly arrived unread messages.
  store.replace([conversation('one', 1)], store.revision());
  assert.equal(store.state.unread.one, 1);
});

test('punctuation-only search does not match every phone but still matches literal names', () => {
  const row = { ...conversation(), displayName: 'Ana', contactPhone: '+5581999999999' };
  for (const search of ['()', '-', ' ( - ) ']) {
    assert.equal(matchesConversation(row, { filter: 'all', search }), false);
  }
  assert.equal(
    matchesConversation({ ...row, displayName: 'Equipe ()' }, { filter: 'all', search: '()' }),
    true,
  );
  assert.equal(matchesConversation(row, { filter: 'all', search: '+55 (81) 99999-9999' }), true);
});
