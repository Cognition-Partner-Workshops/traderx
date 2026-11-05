const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const path = require('node:path');
const net = require('node:net');
const fs = require('node:fs');
const { io: connect } = require('socket.io-client');

const SERVER = path.join(__dirname, '..', 'index.js');
const PRELOAD = path.join(__dirname, 'support', 'graceful-exit.js');

function freePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
  });
}

function startServer(port) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ['-r', PRELOAD, SERVER], {
      env: { ...process.env, TRADE_FEED_PORT: String(port) },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    const timer = setTimeout(() => reject(new Error('server did not start')), 10000);
    child.stdout.on('data', (buf) => {
      if (buf.toString().includes(`http://localhost:${port}/`)) {
        clearTimeout(timer);
        resolve(child);
      }
    });
    child.on('exit', (code) => {
      clearTimeout(timer);
      reject(new Error(`server exited early with code ${code}`));
    });
  });
}

function stopServer(child) {
  return new Promise((resolve) => {
    if (child.exitCode !== null) return resolve();
    child.removeAllListeners('exit');
    child.on('exit', () => resolve());
    child.kill('SIGTERM');
  });
}

describe('trade-feed pub/sub server', () => {
  let child;
  let url;
  const clients = [];

  before(async () => {
    const port = await freePort();
    url = `http://localhost:${port}`;
    child = await startServer(port);
  });

  after(async () => {
    clients.forEach((c) => c.close());
    await stopServer(child);
  });

  async function client() {
    const c = connect(url, { transports: ['websocket'], forceNew: true, reconnection: false });
    clients.push(c);
    await new Promise((resolve, reject) => {
      c.once('connect', resolve);
      c.once('connect_error', reject);
    });
    return c;
  }

  // Collects 'publish' messages; next(pred) resolves with the first matching message.
  function inbox(c) {
    const received = [];
    const waiters = [];
    c.on('publish', (msg) => {
      received.push(msg);
      for (let i = waiters.length - 1; i >= 0; i--) {
        if (waiters[i].pred(msg)) {
          waiters[i].resolve(msg);
          waiters.splice(i, 1);
        }
      }
    });
    return {
      received,
      next(pred, timeoutMs = 2000) {
        const hit = received.find(pred);
        if (hit) return Promise.resolve(hit);
        return new Promise((resolve, reject) => {
          const w = { pred, resolve };
          waiters.push(w);
          setTimeout(() => {
            const idx = waiters.indexOf(w);
            if (idx >= 0) {
              waiters.splice(idx, 1);
              reject(new Error('timed out waiting for message'));
            }
          }, timeoutMs);
        });
      },
    };
  }

  const settle = (ms = 300) => new Promise((r) => setTimeout(r, ms));

  // Subscribing and waiting for our own join notice guarantees the server has joined the room.
  async function subscribe(c, box, topic) {
    c.emit('subscribe', topic);
    await box.next((m) => m.topic === topic && m.from === 'System' &&
      m.payload.message === `New Joiner ${c.id} to topic ${topic}`);
  }

  test('GET / serves the trade-feed inspector HTML page', async () => {
    const res = await fetch(`${url}/`);
    assert.equal(res.status, 200);
    assert.match(res.headers.get('content-type'), /text\/html/);
    const body = await res.text();
    assert.equal(body, fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8'));
  });

  test('subscribe announces the new joiner to the topic as a System message', async () => {
    const c = await client();
    const box = inbox(c);
    const before = Date.now();
    c.emit('subscribe', 'accounts.22214');
    const msg = await box.next((m) => m.topic === 'accounts.22214');
    assert.equal(msg.type, 'message');
    assert.equal(msg.from, 'System');
    assert.deepEqual(msg.payload, { message: `New Joiner ${c.id} to topic accounts.22214` });
    assert.ok(msg.date >= before - 1000 && msg.date <= Date.now() + 1000, 'date is a current epoch-ms timestamp');
  });

  test('existing subscribers are notified when another client joins their topic', async () => {
    const a = await client();
    const b = await client();
    const boxA = inbox(a);
    const boxB = inbox(b);
    await subscribe(a, boxA, '/trades');
    await subscribe(b, boxB, '/trades');
    const notice = await boxA.next((m) => m.payload && m.payload.message === `New Joiner ${b.id} to topic /trades`);
    assert.equal(notice.from, 'System');
  });

  test('publish wraps the trade with sender, topic, type and payload and delivers it to topic subscribers', async () => {
    const sub = await client();
    const pub = await client();
    const box = inbox(sub);
    await subscribe(sub, box, '/accounts/42/trades');

    const trade = { id: 'T-1', accountId: 42, security: 'IBM', side: 'Buy', quantity: 100, state: 'New' };
    pub.emit('publish', { topic: '/accounts/42/trades', type: 'TradeEvent', payload: trade });

    const msg = await box.next((m) => m.type === 'TradeEvent');
    assert.equal(msg.from, pub.id, 'sender is the publishing socket id');
    assert.equal(msg.topic, '/accounts/42/trades');
    assert.deepEqual(msg.payload, trade);
    assert.equal(typeof msg.date, 'number');
  });

  test('publish without a type defaults the message type to "message"', async () => {
    const sub = await client();
    const pub = await client();
    const box = inbox(sub);
    await subscribe(sub, box, 'untyped');
    pub.emit('publish', { topic: 'untyped', payload: { hello: 'world' } });
    const msg = await box.next((m) => m.from === pub.id);
    assert.equal(msg.type, 'message');
    assert.deepEqual(msg.payload, { hello: 'world' });
  });

  test('messages are routed only to subscribers of the published topic', async () => {
    const onA = await client();
    const onB = await client();
    const pub = await client();
    const boxA = inbox(onA);
    const boxB = inbox(onB);
    await subscribe(onA, boxA, '/accounts/1/trades');
    await subscribe(onB, boxB, '/accounts/2/trades');

    pub.emit('publish', { topic: '/accounts/1/trades', type: 'TradeEvent', payload: { id: 'A' } });
    await boxA.next((m) => m.from === pub.id);
    await settle();
    assert.equal(boxB.received.filter((m) => m.from === pub.id).length, 0,
      'subscriber of another account must not receive the trade');
  });

  test('wildcard "/*" subscribers receive every topic, exactly once even if also subscribed to the topic', async () => {
    const inspector = await client();
    const pub = await client();
    const box = inbox(inspector);
    await subscribe(inspector, box, '/*');
    await subscribe(inspector, box, '/positions');

    pub.emit('publish', { topic: '/positions', type: 'PositionEvent', payload: { qty: 1 } });
    pub.emit('publish', { topic: '/other', type: 'OtherEvent', payload: { qty: 2 } });
    await box.next((m) => m.type === 'OtherEvent');
    await settle();
    assert.equal(box.received.filter((m) => m.type === 'PositionEvent').length, 1);
    assert.equal(box.received.filter((m) => m.type === 'OtherEvent').length, 1);
  });

  test('unsubscribe (server command name "unusbscribe") announces the leave and stops delivery', async () => {
    const leaver = await client();
    const stayer = await client();
    const pub = await client();
    const boxL = inbox(leaver);
    const boxS = inbox(stayer);
    await subscribe(leaver, boxL, '/desk');
    await subscribe(stayer, boxS, '/desk');

    leaver.emit('unusbscribe', '/desk');
    const leave = await boxS.next((m) => m.payload && m.payload.message === `${leaver.id} has left /desk`);
    assert.equal(leave.from, 'System');
    assert.equal(leave.topic, '/desk');
    assert.equal(leave.type, 'message');
    // leave notice is broadcast before socket.leave, so the leaver also sees it
    await boxL.next((m) => m.payload && m.payload.message === `${leaver.id} has left /desk`);

    pub.emit('publish', { topic: '/desk', type: 'AfterLeave', payload: {} });
    await boxS.next((m) => m.type === 'AfterLeave');
    await settle();
    assert.equal(boxL.received.filter((m) => m.type === 'AfterLeave').length, 0);
  });

  // KNOWN BUG: the server registers the unsubscribe handler under the misspelled
  // event "unusbscribe" (index.js UNSUBSCRIBE constant). Clients such as the Java
  // SocketIOJSONSubscriber emit "unsubscribe", which the server silently ignores,
  // so they keep receiving messages. Enable once the event name is fixed.
  test('client emitting "unsubscribe" stops receiving topic messages', { todo: 'server listens on misspelled "unusbscribe"' }, async () => {
    const c = await client();
    const pub = await client();
    const box = inbox(c);
    await subscribe(c, box, '/bug');
    c.emit('unsubscribe', '/bug');
    await settle();
    pub.emit('publish', { topic: '/bug', type: 'AfterLeave', payload: {} });
    await settle();
    assert.equal(box.received.filter((m) => m.type === 'AfterLeave').length, 0);
  });
});
