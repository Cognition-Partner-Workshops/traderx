const { test, before, after } = require('node:test');
const assert = require('node:assert');
const { spawn } = require('node:child_process');
const path = require('node:path');
const { io } = require('socket.io-client');

const PORT = 18000 + Math.floor(Math.random() * 1000);
const URL = `http://localhost:${PORT}`;
const TOKEN = 'test-internal-token';
let server;
const sockets = [];

function connect(auth) {
  return new Promise((resolve, reject) => {
    const s = io(URL, { auth, transports: ['websocket'], reconnection: false });
    sockets.push(s);
    s.on('connect', () => resolve(s));
    s.on('connect_error', reject);
  });
}

function collect(socket) {
  const messages = [];
  socket.on('publish', (m) => messages.push(m));
  return messages;
}

const settle = () => new Promise((r) => setTimeout(r, 300));

before(async () => {
  server = spawn(process.execPath, [path.join(__dirname, '..', 'index.js')], {
    env: { ...process.env, TRADE_FEED_PORT: String(PORT), TRADE_FEED_PUBLISH_TOKEN: TOKEN },
    stdio: ['ignore', 'pipe', 'inherit'],
  });
  await new Promise((resolve) => server.stdout.on('data', (d) => {
    if (d.toString().includes('running')) resolve();
  }));
});

after(() => {
  sockets.forEach((s) => s.close());
  server.kill();
});

async function publishAsInternal(topic, payload) {
  const publisher = await connect({ token: TOKEN });
  publisher.emit('publish', { topic, type: 'Trade', payload });
  await settle();
}

test('client can follow its own account topics but not others', async () => {
  const client = await connect();
  const received = collect(client);
  client.emit('subscribe', '/accounts/1/trades');
  client.emit('subscribe', '/accounts/1/positions');
  await settle();
  await publishAsInternal('/accounts/1/trades', { id: 'a1' });
  await publishAsInternal('/accounts/2/trades', { id: 'a2' });
  const ids = received.filter((m) => m.type === 'Trade').map((m) => m.payload.id);
  assert.deepStrictEqual(ids, ['a1']);
});

test('anonymous client cannot subscribe to the wildcard, internal, or malformed topics', async () => {
  const client = await connect();
  const received = collect(client);
  for (const topic of ['/*', '/trades', ['/*'], { 0: '/*' }, '/accounts/1/trades/../..', '/accounts/x/trades', '/accounts/1/orders']) {
    client.emit('subscribe', topic);
  }
  await settle();
  await publishAsInternal('/trades', { id: 'order' });
  await publishAsInternal('/accounts/3/positions', { id: 'p3' });
  assert.deepStrictEqual(received, []);
});

test('authenticated internal service can subscribe to /trades', async () => {
  const internal = await connect({ token: TOKEN });
  const received = collect(internal);
  internal.emit('subscribe', '/trades');
  await settle();
  await publishAsInternal('/trades', { id: 'order-1' });
  assert.deepStrictEqual(received.filter((m) => m.type === 'Trade').map((m) => m.payload.id), ['order-1']);
});

test('client with wrong token cannot subscribe to /trades', async () => {
  const client = await connect({ token: 'wrong' });
  const received = collect(client);
  client.emit('subscribe', '/trades');
  await settle();
  await publishAsInternal('/trades', { id: 'order-2' });
  assert.deepStrictEqual(received, []);
});

test('join/leave does not leak other clients socket ids', async () => {
  const first = await connect();
  const received = collect(first);
  first.emit('subscribe', '/accounts/7/trades');
  await settle();
  const second = await connect();
  second.emit('subscribe', '/accounts/7/trades');
  second.emit('unsubscribe', '/accounts/7/trades');
  await settle();
  const leaked = received.filter((m) => JSON.stringify(m).includes(second.id));
  assert.deepStrictEqual(leaked, []);
  assert.deepStrictEqual(received, []);
});
