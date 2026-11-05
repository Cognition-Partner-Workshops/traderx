const { test } = require('node:test');
const assert = require('node:assert/strict');
const { customerMessage } = require('./customer-message');
const ref = 'a7a8e4a5-272b-4af8-846b-8021a282917c';

test('internal numeric order keys become public references before any customer broadcast', () => {
  const source = { topic: '/trades', payload: { accountId: 22214, accountReference: ref, quantity: 22214 } };
  const output = customerMessage(source);
  assert.equal(output.payload.accountId, ref);
  assert.equal(output.payload.quantity, 22214);
  assert.equal(source.payload.accountId, 22214);
  assert.deepEqual(customerMessage({ topic: `/accounts/${ref}/trades`, payload: { accountId: '****2214' } }).payload, { accountId: '****2214' });
});

test('null and empty messages stay safe', () => {
  assert.equal(customerMessage(null), null);
  assert.equal(customerMessage({ topic: '/*', payload: null }).payload, null);
  assert.deepEqual(customerMessage({ topic: '/trades', payload: {} }).payload, {});
});

test('unsafe account topics, nested fields, numbers and legacy trade ids cannot be broadcast', () => {
  assert.equal(customerMessage({ topic: '/accounts/22214/trades' }), null);
  for (const payload of [{ accountId: 22214 }, { trade: { accountId: '22214' } }, { accountNumber: '22214' }, { accountReference: '22214' }, { id: 'TRADE-22214-AABBCC' }]) {
    assert.equal(customerMessage({ topic: '/trades', payload }), null);
  }
});
