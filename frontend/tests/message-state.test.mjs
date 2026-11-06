import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeMessages } from '../src/features/messages/message-state.js';
const message = (id, status, timestamp = '2026-10-14T12:00:00Z') => ({
  id,
  status,
  createdAt: timestamp,
  updatedAt: timestamp,
});
test('HTTP result and STOMP event resolve to the same internal message', () => {
  const rows = mergeMessages(
    [message('one', 'PENDING')],
    [message('one', 'SIMULATED'), message('one', 'SIMULATED')],
  );
  assert.equal(rows.length, 1);
  assert.equal(rows[0].status, 'SIMULATED');
});
test('reconciliation preserves newer status and merges missed messages chronologically', () => {
  const read = message('one', 'READ', '2026-10-14T12:00:03Z');
  const result = mergeMessages(
    [read],
    [message('one', 'ACCEPTED'), message('two', 'RECEIVED', '2026-10-14T12:00:04Z')],
  );
  assert.equal(result[0].status, 'READ');
  assert.deepEqual(
    result.map((row) => row.id),
    ['one', 'two'],
  );
});
test('equal timestamps do not downgrade a delivery confirmation', () => {
  assert.equal(mergeMessages([message('one', 'READ')], [message('one', 'SENT')])[0].status, 'READ');
});
test('message versions preserve an edit when stale status frames share the timestamp', () => {
  const edited = { ...message('one', 'SENT'), version: 5, content: { text: 'Corrigido' } };
  const stale = { ...message('one', 'SENT'), version: 4, content: { text: 'Anterior' } };
  assert.equal(mergeMessages([edited], [stale])[0].content.text, 'Corrigido');
  assert.equal(mergeMessages([stale], [edited])[0].version, 5);
});
test('simulated acceptance can advance to read within the same millisecond', () => {
  assert.equal(mergeMessages([message('one', 'SIMULATED')], [message('one', 'READ')])[0].status, 'READ');
});
