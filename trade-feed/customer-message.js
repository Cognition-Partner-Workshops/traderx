const reference = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const masked = /^\*{4}\d{1,4}$/;

function customerMessage(data) {
  if (!data || typeof data.topic !== 'string') return null;
  const accountTopic = data.topic.match(/^\/accounts\/([^/]+)(?:\/|$)/);
  if (accountTopic && !reference.test(accountTopic[1])) return null;

  function sanitize(value) {
    if (value === null || typeof value !== 'object') return value;
    if (Array.isArray(value)) return value.map(sanitize);
    const result = {};
    for (const [key, item] of Object.entries(value)) {
      if ((key === 'accountId' || key === 'accountid') && item != null) {
        if (typeof item === 'string' && (reference.test(item) || masked.test(item))) {
          result[key] = item;
        } else if (reference.test(value.accountReference || '')) {
          result[key] = value.accountReference;
        } else {
          throw new Error('Invalid account reference');
        }
      } else if (key === 'accountNumber' && item != null && !masked.test(item)) {
        throw new Error('Invalid account reference');
      } else if (key === 'accountReference' && item != null && !reference.test(item)) {
        throw new Error('Invalid account reference');
      } else if (key === 'id' && typeof item === 'string' && /^TRADE-\d+-/.test(item)) {
        throw new Error('Invalid account reference');
      } else {
        result[key] = sanitize(item);
      }
    }
    return result;
  }

  try {
    return { ...data, payload: sanitize(data.payload) };
  } catch (error) {
    return null;
  }
}

module.exports = { customerMessage };
