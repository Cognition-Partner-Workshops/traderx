const sockio = require("socket.io");
const app = require('express')();
const winston = require('winston');
const http = require('http').createServer(app);

const io = new sockio.Server(http, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});
const port = process.env.TRADE_FEED_PORT || 18086;

const log = winston.createLogger({
  transports: [
    new winston.transports.Console()
  ]
});

// command names
const SUBSCRIBE = "subscribe";
const UNSUBSCRIBE = "unusbscribe";
const PUBLISH = "publish";

app.get('/', (req, res) => {
  res.sendFile(__dirname + '/index.html');
});

function wrapMessage( sender, topic, payloadType, payload){
  return {
    type: payloadType || "message",
    from: sender,
    topic: topic,
    date: new Date().getTime(),
    payload: payload
  }
}

function broadcast(from, data) {
  var message=wrapMessage(from,data.topic,data.type,data.payload);
  log.info(`Publish ${data.topic} -> ${JSON.stringify(message)}`);
  io.sockets.in([data.topic, "/*"]).emit(PUBLISH, message);
}

const crypto = require('node:crypto');

// Browsers may only follow per-account topics; the order stream is internal-only.
const CLIENT_TOPIC_PATTERN = /^\/accounts\/\d{1,18}\/(trades|positions)$/;
const INTERNAL_TOPICS = ['/trades'];

function isInternalService(socket) {
  const expected = process.env.TRADE_FEED_PUBLISH_TOKEN;
  const supplied = socket.handshake.auth && socket.handshake.auth.token;
  if (!expected || typeof supplied !== 'string') {
    return false;
  }
  const a = Buffer.from(expected);
  const b = Buffer.from(supplied);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function canSubscribe(socket, topic) {
  if (typeof topic !== 'string') {
    return false;
  }
  if (CLIENT_TOPIC_PATTERN.test(topic)) {
    return true;
  }
  return INTERNAL_TOPICS.includes(topic) && socket.data.internalSubscriber === true;
}

io.on('connection', (socket) => {
  socket.data.internalSubscriber = isInternalService(socket);
  log.info(`New Connection from ${socket.id}`);
  socket.on(SUBSCRIBE, (topic) => {
    if (!canSubscribe(socket, topic)) {
      log.warn(`Rejected subscribe from ${socket.id} to ${JSON.stringify(topic)}`);
      return;
    }
    log.info(`Subscribe ${topic}`);
    socket.join(topic);
  });
  socket.on(UNSUBSCRIBE, (topic) => {
    if (typeof topic !== 'string') {
      return;
    }
    log.info(`Unsubscribe ${topic}`);
    socket.leave(topic);
  });
  socket.on(PUBLISH, (data) => {
    broadcast(socket.id, data);
  });
});


http.listen(port, () => {
  log.info(`Socket.IO server running at http://localhost:${port}/`);
});