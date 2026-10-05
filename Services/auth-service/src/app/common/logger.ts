const SENSITIVE_KEYS = new Set([
  'password',
  'password_hash',
  'refreshToken',
  'refresh_token',
  'accessToken',
  'access_token',
  'token',
  'secret',
  'authorization',
  'auth',
]);

function looksSensitiveKey(key: string): boolean {
  return SENSITIVE_KEYS.has(key) || SENSITIVE_KEYS.has(key.toLowerCase());
}

export function redact(input: unknown): unknown {
  if (typeof input === 'string') {
    return input;
  }

  if (input === null || typeof input !== 'object') {
    return input;
  }

  if (Array.isArray(input)) {
    return input.map((value) => redact(value));
  }

  if (input instanceof Error) {
    const errorView: Record<string, unknown> = {
      name: input.name,
      message: input.message,
      stack: input.stack,
    };

    for (const [key, value] of Object.entries(input)) {
      if (!(key in errorView)) {
        errorView[key] = value;
      }
    }

    return redact(errorView);
  }

  const output: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(input)) {
    if (looksSensitiveKey(key)) {
      output[key] = '[REDACTED]';
      continue;
    }

    output[key] = redact(value);
  }

  return output;
}

function write(level: 'info' | 'warn' | 'error', message: string, meta?: unknown): void {
  const logger = console[level];
  if (meta === undefined) {
    logger(message);
    return;
  }

  logger(message, JSON.stringify(redact(meta)));
}

export const logger = {
  info(message: string, meta?: unknown) {
    write('info', message, meta);
  },
  warn(message: string, meta?: unknown) {
    write('warn', message, meta);
  },
  error(message: string, meta?: unknown) {
    write('error', message, meta);
  },
};

