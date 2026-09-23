// services/auth-service/src/lib/logger.ts

const SENSITIVE_KEYS = [
  'password',
  'password_hash',
  'refreshToken',
  'accessToken',
  'refreshtoken',
  'accesstoken',
  'token',
  'secret',
  'pwd',
  'pass',
  'authorization',
  'auth',
];

function isLikelyTokenString(s: unknown): boolean {
  if (typeof s !== 'string') return false;
  const trimmed = s.trim();

  // JWT-like: header.payload.signature (base64url segments)
  const jwtRegex = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
  if (jwtRegex.test(trimmed) && trimmed.length > 20) return true;

  // Long hex string (common API keys)
  const hexRegex = /^[A-Fa-f0-9]{32,}$/;
  if (hexRegex.test(trimmed)) return true;

  // Long base64-ish string (>= 32) or other long random tokens
  if (trimmed.length >= 32) return true;

  return false;
}

function redactValue(_: any): string {
  return '[REDACTED]';
}

/**
 * Recursively redacts sensitive data from an object or value.
 * - If a primitive string is passed directly, redacts it only if it looks like a token.
 * - Non-string primitives (number/boolean/null/undefined) are returned unchanged.
 * - Arrays are mapped.
 * - Objects are traversed; keys matching the SENSITIVE_KEYS (case-insensitive) are redacted.
 * - String property values that look like tokens are also redacted.
 */
export function redact(input: any): any {
  // Primitive string passed directly to logger: redact when it looks like a token.
  if (typeof input === 'string') {
    if (isLikelyTokenString(input)) return '[REDACTED]';
    return input;
  }

  // Non-string primitives unchanged
  if (input === null || typeof input !== 'object') return input;

  // Arrays => map each element through redact
  if (Array.isArray(input)) return input.map(redact);

  // Plain object => redact sensitive keys/values recursively
  const out: any = {};
  for (const [k, v] of Object.entries(input)) {
    const keyLower = String(k).toLowerCase();

    // Case-insensitive key match
    if (SENSITIVE_KEYS.some((sk) => sk.toLowerCase() === keyLower)) {
      out[k] = redactValue(v);
      continue;
    }

    // If value is a string that looks like a token, redact it
    if (typeof v === 'string' && isLikelyTokenString(v)) {
      out[k] = '[REDACTED]';
      continue;
    }

    // Recurse for nested objects/arrays
    if (v && typeof v === 'object') {
      out[k] = redact(v);
      continue;
    }

    // Preserve other primitives
    out[k] = v;
  }

  return out;
}

/**
 * Simple centralized logger that always runs the supplied meta through `redact` before logging.
 * Call patterns supported:
 *   logger.info('message')                   // logs message only
 *   logger.info('message', { user: 'joe' }) // logs message and redacted meta
 *   logger.info('message', 'jwt-token')     // primitive token will be redacted
 */
export const logger = {
  info: (msg: string, meta?: any) => {
    if (meta !== undefined) {
      try {
        console.info(msg, JSON.stringify(redact(meta)));
      } catch {
        // Fallback: avoid throwing from logger; log a minimal safe message
        console.info(msg, '"[REDACTED]"');
      }
    } else {
      console.info(msg);
    }
  },

  warn: (msg: string, meta?: any) => {
    if (meta !== undefined) {
      try {
        console.warn(msg, JSON.stringify(redact(meta)));
      } catch {
        console.warn(msg, '"[REDACTED]"');
      }
    } else {
      console.warn(msg);
    }
  },

  error: (msg: string, meta?: any) => {
    if (meta !== undefined) {
      try {
        console.error(msg, JSON.stringify(redact(meta)));
      } catch {
        console.error(msg, '"[REDACTED]"');
      }
    } else {
      console.error(msg);
    }
  },
};

export default logger;