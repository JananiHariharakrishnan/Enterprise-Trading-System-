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

export function redact(input: any): any {
  if (typeof input === 'string') {
    if (isLikelyTokenString(input)) return '[REDACTED]';
    return input;
  }
  if (input === null || typeof input !== 'object') return input;
  if (Array.isArray(input)) return input.map(redact);
  const out: any = {};
  for (const [k, v] of Object.entries(input)) {
    const keyLower = String(k).toLowerCase();
    if (SENSITIVE_KEYS.some((sk) => sk.toLowerCase() === keyLower)) {
      out[k] = redactValue(v);
      continue;
    }
    if (typeof v === 'string' && isLikelyTokenString(v)) {
      out[k] = '[REDACTED]';
      continue;
    }
    if (v && typeof v === 'object') {
      out[k] = redact(v);
      continue;
    }
    out[k] = v;
  }

  return out;
}
export const logger = {
  info: (msg: string, meta?: any) => {
    if (meta !== undefined) {
      try {
        console.info(msg, JSON.stringify(redact(meta)));
      } catch {
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