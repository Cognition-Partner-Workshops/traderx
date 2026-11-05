const sockio = require("socket.io");
const express = require("express");
const winston = require("winston");
const auth = require("./auth");

const log = winston.createLogger({
  transports: [
    new winston.transports.Console()
  ]
});

// command names
const SUBSCRIBE = "subscribe";
const UNSUBSCRIBE = "unsubscribe";
const PUBLISH = "publish";
const ERROR = "error";

function wrapMessage(sender, topic, payloadType, payload) {
  return {
    type: payloadType || "message",
    from: sender,
    topic: topic,
    date: new Date().getTime(),
    payload: payload
  };
}

function joinMessage(user, topic) {
  return wrapMessage('System', topic, 'message', { message: `New Joiner ${user} to topic ${topic}` });
}

function leaveMessage(user, topic) {
  return wrapMessage('System', topic, 'message', { message: `${user} has left ${topic}` });
}

/**
 * Builds the trade-feed server. Every socket must present a valid TraderX bearer
 * token in the handshake; publishing is restricted to SERVICE callers and
 * subscriptions are checked against the caller's role / account ownership.
 */
function createServer({ secret, ownsAccount }) {
  const app = express();
  const http = require('http').createServer(app);
  const io = new sockio.Server(http, {
    cors: {
      origin: "*",
      methods: ["GET", "POST"]
    }
  });

  // Explicitly public: static test page and liveness probe only.
  app.get('/', (req, res) => {
    res.sendFile(__dirname + '/index.html');
  });
  app.get('/health', (req, res) => {
    res.json({ status: 'ok' });
  });

  function broadcast(from, data) {
    const message = wrapMessage(from, data.topic, data.type, data.payload);
    log.info(`Publish ${data.topic} -> ${JSON.stringify(message)}`);
    io.sockets.in([data.topic, "/*"]).emit(PUBLISH, message);
  }

  io.use(auth.socketAuthMiddleware(secret));

  io.on('connection', (socket) => {
    const identity = socket.data.identity;
    log.info(`New Connection from ${socket.id} (${identity.subject})`);

    socket.on(SUBSCRIBE, async (topic) => {
      if (!(await auth.canSubscribe(identity, topic, ownsAccount))) {
        log.warn(`Denied subscribe ${topic} for ${identity.subject}`);
        socket.emit(ERROR, { command: SUBSCRIBE, topic, message: "forbidden" });
        return;
      }
      log.info(`Subscribe ${topic}`);
      socket.join(topic);
      broadcast('System', joinMessage(identity.subject, topic));
    });

    socket.on(UNSUBSCRIBE, (topic) => {
      if (typeof topic !== "string") {
        return;
      }
      log.info(`Unsubscribe ${topic}`);
      broadcast('System', leaveMessage(identity.subject, topic));
      socket.leave(topic);
    });

    socket.on(PUBLISH, (data) => {
      if (!auth.canPublish(identity)) {
        log.warn(`Denied publish from ${identity.subject}`);
        socket.emit(ERROR, { command: PUBLISH, message: "forbidden" });
        return;
      }
      if (!data || typeof data.topic !== "string") {
        socket.emit(ERROR, { command: PUBLISH, message: "topic required" });
        return;
      }
      broadcast(identity.subject, data);
    });
  });

  return { app, http, io };
}

module.exports = { createServer };

if (require.main === module) {
  const port = process.env.TRADE_FEED_PORT || 18086;
  const accountServiceUrl = process.env.ACCOUNT_SERVICE_URL || "http://localhost:18088";
  const { http } = createServer({
    secret: auth.loadSecret(),
    ownsAccount: auth.accountServiceOwnershipCheck(accountServiceUrl),
  });
  http.listen(port, () => {
    log.info(`Socket.IO server running at http://localhost:${port}/`);
  });
}
