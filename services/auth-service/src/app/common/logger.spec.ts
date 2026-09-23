import { describe, expect, it } from 'vitest';
import { redact } from './logger.js';

describe('logger redaction', () => {
  it('redacts sensitive keys at any depth', () => {
    const input = {
      username: 'priya.menon',
      password: 'secret',
      nested: {
        refreshToken: 'abcd',
        meta: {
          authorization: 'Bearer token',
        },
      },
    };

    expect(redact(input)).toEqual({
      username: 'priya.menon',
      password: '[REDACTED]',
      nested: {
        refreshToken: '[REDACTED]',
        meta: {
          authorization: '[REDACTED]',
        },
      },
    });
  });
});

