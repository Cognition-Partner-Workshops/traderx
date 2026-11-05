const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const jwt = require("jsonwebtoken");
const { io } = require("socket.io-client");
const { createServer } = require("../index");

const secret = "unit-test-secret-that-is-at-least-32-bytes";
const ownership = { user01: [22214] };

function token(sub, roles, options = {}) {
  return jwt.sign({ sub, roles }, secret, { algorithm: "HS256", issuer: "traderx", expiresIn: "5m", ...options });
}

let http;
let url;

before(async () => {
  ({ http } = createServer({
    secret,
    ownsAccount: async (identity, accountId) => (ownership[identity.subject] || []).includes(accountId),
  }));
  await new Promise((resolve) => http.listen(0, resolve));
  url = `http://localhost:${http.address().port}`;
});

after(() => new Promise((resolve) => http.close(resolve)));

function connect(auth) {
  return new Promise((resolve, reject) => {
    const socket = io(url, { auth, transports: ["websocket"], reconnection: false });
    socket.on("connect", () => resolve(socket));
    socket.on("connect_error", (err) => reject(err));
  });
}

function waitFor(socket, event) {
  return new Promise((resolve) => socket.once(event, resolve));
}

test("handshake without a token is rejected", async () => {
  await assert.rejects(connect({}), /unauthorized/);
});

test("handshake with a token signed by another key is rejected", async () => {
  const forged = jwt.sign({ sub: "mallory", roles: ["SERVICE"] }, "another-secret-that-is-also-32-bytes-long", { issuer: "traderx" });
  await assert.rejects(connect({ token: forged }), /unauthorized/);
});

test("USER may subscribe to an owned account topic and receives SERVICE publishes", async () => {
  const user = await connect({ token: token("user01", ["USER"]) });
  const service = await connect({ token: token("trade-processor", ["SERVICE"]) });
  try {
    user.emit("subscribe", "/accounts/22214/trades");
    await waitFor(user, "publish"); // join notice
    const delivered = waitFor(user, "publish");
    service.emit("publish", { topic: "/accounts/22214/trades", type: "Trade", payload: { id: "T1" } });
    const message = await delivered;
    assert.equal(message.from, "trade-processor");
    assert.deepEqual(message.payload, { id: "T1" });
  } finally {
    user.close();
    service.close();
  }
});

test("USER may not subscribe to another account or the firehose", async () => {
  const user = await connect({ token: token("user01", ["USER"]) });
  try {
    user.emit("subscribe", "/accounts/99999/trades");
    const denied = await waitFor(user, "error");
    assert.equal(denied.command, "subscribe");
    user.emit("subscribe", "/*");
    const deniedAll = await waitFor(user, "error");
    assert.equal(deniedAll.topic, "/*");
  } finally {
    user.close();
  }
});

test("USER may not publish", async () => {
  const user = await connect({ token: token("user01", ["USER"]) });
  try {
    user.emit("publish", { topic: "/trades", type: "TradeOrder", payload: {} });
    const denied = await waitFor(user, "error");
    assert.equal(denied.command, "publish");
  } finally {
    user.close();
  }
});
