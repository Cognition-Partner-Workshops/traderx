const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function server(source) {
  const logs = [];
  const emissions = [];
  const handlers = {};
  let connection;
  const io = {
    sockets: { in: () => ({ emit: (event, message) => emissions.push(message) }) },
    on: (event, callback) => { connection = callback; }
  };
  const app = { get() {} };
  vm.runInNewContext(source, {
    require: (name) => ({
      'socket.io': { Server: function () { return io; } },
      express: () => app,
      winston: { createLogger: () => ({ info: (...args) => logs.push(args) }), transports: { Console: function () {} } },
      http: { createServer: () => ({ listen() {} }) }
    })[name],
    process: { env: {} }, __dirname
  });
  connection({ id: 'socket', on: (event, callback) => { handlers[event] = callback; }, join() {}, leave() {} });
  return { logs, emissions, handlers };
}

test('D4 publish logs metadata only, not the payload or envelope', () => {
  const { logs, emissions, handlers } = server(fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8'));
  const payload = { accountId: 'PRIVATE-ACCOUNT', quantity: 123456, security: 'PRIVATE-TRADE' };
  handlers.publish({ topic: '/trades', type: 'TradeOrder', payload });
  const publish = logs.find(args => args[0].startsWith('Publish'));
  assert.equal(publish[0], 'Publish');
  assert.deepEqual(JSON.parse(JSON.stringify(publish[1])), { topic: '/trades', type: 'TradeOrder' });
  assert.ok(!JSON.stringify(logs).includes('PRIVATE'));
  assert.deepEqual(emissions[0].payload, payload);
});

test('D3 metadata and subscription logging cannot inject CR/LF', () => {
  const { logs, handlers } = server(fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8'));
  handlers.publish({ topic: '/trades\r\nFORGED', type: 'TradeOrder\nFORGED', payload: {} });
  handlers.subscribe('/trades\r\nFORGED');
  handlers.unusbscribe('/trades\r\nFORGED');
  for (const args of logs) {
    for (const arg of args) {
      for (const value of typeof arg === 'object' ? Object.values(arg) : [arg]) {
        assert.doesNotMatch(value, /[\r\n]/);
      }
    }
  }
});
