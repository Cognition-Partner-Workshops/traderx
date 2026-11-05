const { test, before, after } = require("node:test");
const assert = require("node:assert");
const { spawn } = require("node:child_process");
const http = require("node:http");
const net = require("node:net");
const path = require("node:path");

const DISALLOWED_ORIGIN = "https://evil.example.com";

function freePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer().listen(0, "127.0.0.1", () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    }).on("error", reject);
  });
}

function request(port, method, origin) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: "127.0.0.1", port, method,
      path: "/socket.io/?EIO=4&transport=polling",
      headers: { Origin: origin, "Access-Control-Request-Method": "GET" }
    }, (res) => {
      res.resume();
      res.on("end", () => resolve(res));
    });
    req.on("error", reject);
    req.end();
  });
}

async function startFeed(env) {
  const port = await freePort();
  const childEnv = { ...process.env, TRADE_FEED_PORT: String(port) };
  delete childEnv.CORS_ALLOWED_ORIGINS;
  const child = spawn(process.execPath, [path.join(__dirname, "..", "index.js")], {
    env: { ...childEnv, ...env }, stdio: "ignore"
  });
  for (let i = 0; i < 100; i++) {
    try {
      await request(port, "GET", "http://localhost:8080");
      return { port, child };
    } catch {
      await new Promise((r) => setTimeout(r, 100));
    }
  }
  child.kill();
  throw new Error("trade-feed did not start");
}

let defaults;
let configured;

before(async () => {
  defaults = await startFeed({});
  configured = await startFeed({ CORS_ALLOWED_ORIGINS: "https://traderx.example.com, *" });
});

after(() => {
  defaults.child.kill();
  configured.child.kill();
});

for (const origin of ["http://localhost:8080", "http://localhost:18093", "http://localhost:18094"]) {
  test(`default allow-list sends Access-Control-Allow-Origin to ${origin}`, async () => {
    const get = await request(defaults.port, "GET", origin);
    const preflight = await request(defaults.port, "OPTIONS", origin);
    assert.strictEqual(get.headers["access-control-allow-origin"], origin);
    assert.strictEqual(preflight.headers["access-control-allow-origin"], origin);
  });
}

test("disallowed origin gets no Access-Control-Allow-Origin", async () => {
  const get = await request(defaults.port, "GET", DISALLOWED_ORIGIN);
  const preflight = await request(defaults.port, "OPTIONS", DISALLOWED_ORIGIN);
  assert.strictEqual(get.headers["access-control-allow-origin"], undefined);
  assert.strictEqual(preflight.headers["access-control-allow-origin"], undefined);
});

test("CORS_ALLOWED_ORIGINS replaces the defaults and '*' is ignored", async () => {
  const allowed = await request(configured.port, "GET", "https://traderx.example.com");
  assert.strictEqual(allowed.headers["access-control-allow-origin"], "https://traderx.example.com");
  for (const origin of ["http://localhost:18093", DISALLOWED_ORIGIN]) {
    const res = await request(configured.port, "GET", origin);
    assert.strictEqual(res.headers["access-control-allow-origin"], undefined);
  }
});
