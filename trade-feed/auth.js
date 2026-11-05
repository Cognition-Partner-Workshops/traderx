const jwt = require("jsonwebtoken");

const ISSUER = "traderx";
const ROLE_SERVICE = "SERVICE";
const ROLE_ADMIN = "ADMIN";
const ACCOUNT_TOPIC = /^\/accounts\/(\d+)\/(trades|positions)$/;

function loadSecret(env = process.env) {
  const secret = env.TRADERX_AUTH_SECRET;
  if (!secret || Buffer.byteLength(secret, "utf8") < 32) {
    throw new Error("TRADERX_AUTH_SECRET must be set and at least 32 bytes long");
  }
  return secret;
}

/** Validates an HS256 TraderX bearer token and returns the caller identity, or throws. */
function verifyToken(token, secret) {
  if (typeof token !== "string" || token.length === 0) {
    throw new Error("missing token");
  }
  const claims = jwt.verify(token, secret, { algorithms: ["HS256"], issuer: ISSUER });
  const roles = Array.isArray(claims.roles) ? claims.roles.map((r) => String(r).toUpperCase()) : [];
  return { subject: claims.sub, roles, token };
}

function hasRole(identity, role) {
  return identity.roles.includes(role);
}

function isPrivileged(identity) {
  return hasRole(identity, ROLE_SERVICE) || hasRole(identity, ROLE_ADMIN);
}

/** Only trusted services may put messages on the bus. */
function canPublish(identity) {
  return hasRole(identity, ROLE_SERVICE);
}

/**
 * Subscription rules:
 *  - SERVICE / ADMIN callers may subscribe to any topic (including the "/*" firehose).
 *  - USER callers may only subscribe to /accounts/{id}/trades|positions for accounts
 *    they own, as decided by account-service (which enforces the ownership mapping).
 */
async function canSubscribe(identity, topic, ownsAccount) {
  if (typeof topic !== "string") {
    return false;
  }
  if (isPrivileged(identity)) {
    return true;
  }
  const match = ACCOUNT_TOPIC.exec(topic);
  if (!match) {
    return false;
  }
  return ownsAccount(identity, Number(match[1]));
}

/** Default ownership check: account-service returns 200 only to owners (or privileged callers). */
function accountServiceOwnershipCheck(accountServiceUrl, fetchImpl = globalThis.fetch) {
  return async (identity, accountId) => {
    try {
      const response = await fetchImpl(`${accountServiceUrl}/account/${accountId}`, {
        headers: { Authorization: `Bearer ${identity.token}` },
      });
      return response.status === 200;
    } catch (err) {
      return false;
    }
  };
}

/** Socket.IO middleware: reject the handshake unless auth.token is a valid TraderX token. */
function socketAuthMiddleware(secret) {
  return (socket, next) => {
    try {
      socket.data.identity = verifyToken(socket.handshake.auth && socket.handshake.auth.token, secret);
      next();
    } catch (err) {
      next(new Error("unauthorized"));
    }
  };
}

module.exports = {
  ISSUER,
  loadSecret,
  verifyToken,
  canPublish,
  canSubscribe,
  isPrivileged,
  accountServiceOwnershipCheck,
  socketAuthMiddleware,
};
