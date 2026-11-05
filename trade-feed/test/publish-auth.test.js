const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomBytes } = require('node:crypto');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const net = require('node:net');
const path = require('node:path');
const { io } = require('socket.io-client');

async function startFeed(t, token) {
  const reservation = net.createServer();
  reservation.listen(0, '127.0.0.1');
  await once(reservation, 'listening');
  const port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  const env = { ...process.env, TRADE_FEED_PORT: String(port) };
  delete env.TRADE_FEED_PUBLISH_TOKEN;
  if (token !== undefined) env.TRADE_FEED_PUBLISH_TOKEN = token;
  const child = spawn(process.execPath, ['index.js'], {
    cwd: path.join(__dirname, '..'), env, stdio: ['ignore', 'pipe', 'pipe']
  });
  t.after(async () => {
    if (child.exitCode === null) {
      const exited = once(child, 'exit');
      child.kill();
      await exited;
    }
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Feed startup timed out')), 5000);
    child.once('error', reject);
    child.once('exit', code => {
      clearTimeout(timer);
      reject(new Error(`Feed exited with ${code}`));
    });
    child.stdout.on('data', data => {
      if (data.toString().includes('Socket.IO server running')) {
        clearTimeout(timer);
        resolve();
      }
    });
  });
  return async auth => {
    const socket = io(`http://127.0.0.1:${port}`, { auth, reconnection: false });
    t.after(() => socket.disconnect());
    await Promise.race([
      once(socket, 'connect'),
      once(socket, 'connect_error').then(([error]) => { throw error; })
    ]);
    return socket;
  };
}

async function subscribe(socket, topic) {
  const joined = once(socket, 'publish');
  socket.emit('subscribe', topic);
  await joined;
}

function receives(socket, payload, timeout = 500) {
  return new Promise(resolve => {
    const listener = message => {
      if (message.from !== 'System' && message.payload === payload) finish(true);
    };
    const timer = setTimeout(() => finish(false), timeout);
    function finish(received) {
      clearTimeout(timer);
      socket.off('publish', listener);
      resolve(received);
    }
    socket.on('publish', listener);
  });
}

test('only authenticated publishers can broadcast to anonymous subscribers', { timeout: 15000 }, async t => {
  const token = randomBytes(32).toString('hex');
  const connect = await startFeed(t, token);
  const subscriber = await connect();
  const topic = '/accounts/1/trades';
  await subscribe(subscriber, topic);
  for (const [label, auth] of [['anonymous', undefined], ['wrong token', { token: 'wrong' }],
    ['non-string token', { token: 123 }], ['same-length wrong token', { token: 'x'.repeat(token.length) }]]) {
    const publisher = await connect(auth);
    const received = receives(subscriber, label);
    publisher.emit('publish', { topic, payload: label });
    assert.equal(await received, false, `${label} publish must not be delivered`);
  }
  const publisher = await connect({ token });
  const received = receives(subscriber, 'authorized', 3000);
  publisher.emit('publish', { topic, payload: 'authorized' });
  assert.equal(await received, true, 'authorized publish must be delivered');
});

test('publishing fails closed when no token is configured', { timeout: 10000 }, async t => {
  const connect = await startFeed(t);
  const subscriber = await connect();
  const topic = '/accounts/1/trades';
  await subscribe(subscriber, topic);
  const publisher = await connect({ token: 'any-token' });
  const received = receives(subscriber, 'unconfigured');
  publisher.emit('publish', { topic, payload: 'unconfigured' });
  assert.equal(await received, false, 'unconfigured publish must not be delivered');
});
