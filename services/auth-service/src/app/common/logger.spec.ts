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

  it('redacts sensitive keys when meta is an Error object', () => {
    const error = new Error('login failed');
    (error as Error & { password?: string }).password = 'super-secret';
    (error as Error & { nested?: { refreshToken: string } }).nested = { refreshToken: 'token-value' };

    expect(redact(error)).toEqual(
      expect.objectContaining({
        name: 'Error',
        message: 'login failed',
        password: '[REDACTED]',
        nested: {
          refreshToken: '[REDACTED]',
        },
      }),
    );
  });
});

