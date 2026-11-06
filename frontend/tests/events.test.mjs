import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// Read the constants independently of browser-only Angular to validate all public names.
test('event names are globally unique and use their domain prefix', () => {
  const domains = ['app', 'chat', 'message', 'user', 'member'];
  const names = domains.flatMap((domain) => {
    const source = readFileSync(
      new URL(`../src/core/events/${domain}-events.constant.js`, import.meta.url),
      'utf8',
    );
    const values = [...source.matchAll(/'(wxn:[^']+)'/g)].map((match) => match[1]);
    assert.ok(values.length > 0);
    values.forEach((value) => assert.ok(value.startsWith(`wxn:${domain}:`)));
    return values;
  });
  assert.equal(new Set(names).size, names.length);
});
