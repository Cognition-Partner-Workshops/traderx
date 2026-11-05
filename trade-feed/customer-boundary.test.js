const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function broker() {
  const events = [];
  const handlers = {};
  const socket = { id: 'fs09-client', on: (event, handler) => { handlers[event] = handler; }, join() {}, leave() {}, emit: (event, data) => events.push({ event, data }) };
  const io = { sockets: { in: () => ({ emit: (event, data) => events.push({ event, data }) }) }, on: (event, connected) => connected(socket) };
  const dependencies = {
    'socket.io': { Server: function () { return io; } },
    express: () => ({ get() {} }),
    winston: { createLogger: () => ({ info() {} }), transports: { Console: function () {} } },
    http: { createServer: () => ({ listen() {} }) }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'index.js'), 'utf8'), {
    require: name => Object.hasOwn(dependencies, name) ? dependencies[name] : require(name),
    process: { env: { TRADE_FEED_PORT: '18909' } }, __dirname
  });
  return { handlers, events };
}

test('public trade-feed broadcasts replace full numeric order accounts with opaque references', () => {
  const { handlers, events } = broker();
  const reference = 'a7a8e4a5-272b-4af8-846b-8021a282917c';
  handlers.publish({ topic: '/trades', payload: { accountId: 22214, accountReference: reference } });
  const message = events.find(event => event.event === 'publish');
  assert.equal(message.data.payload.accountId, reference, 'Customer feed must not expose internal account numbers');
});

test('subscription messages never echo a full numeric account topic', () => {
  const { handlers, events } = broker();
  handlers.subscribe('/accounts/22214/trades');
  assert.equal(events.some(event => event.event === 'publish'), false, 'Customer join message must not disclose account topic');
  assert.equal(events[0].data, 'Invalid account reference');
});
