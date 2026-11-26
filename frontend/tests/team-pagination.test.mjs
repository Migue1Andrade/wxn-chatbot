import test from 'node:test';
import assert from 'node:assert/strict';
import { requestedTeamPage } from '../src/features/collaboration/team-pagination.js';

test('failed pagination retry requests the same next page until a response commits it', () => {
  let currentPage = 0;
  const firstAttempt = requestedTeamPage(currentPage, true);
  assert.equal(firstAttempt, 1);
  // A failed request does not commit currentPage.
  const retry = requestedTeamPage(currentPage, true);
  assert.equal(retry, firstAttempt);
  currentPage = retry;
  assert.equal(requestedTeamPage(currentPage, true), 2);
});

test('fresh collaboration load starts from page zero', () => {
  assert.equal(requestedTeamPage(5, false), 0);
});
