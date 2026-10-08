import { describe, it, expect, vi } from 'vitest';
import { redact, logger } from '../src/app/common/logger.js';

describe('logger', () => {
  it('redacts sensitive keys in nested objects', () => {
    const input = { password: 'x', nested: { secret: 'y', ok: 'v' }, list: [{ accessToken: 't' }] };
    const out = redact(input as any) as any;
    expect(out.password).toBe('[REDACTED]');
    expect(out.nested.secret).toBe('[REDACTED]');
    expect(out.list[0].accessToken).toBe('[REDACTED]');
  });

  it('redacts Error objects and logger writes', () => {
    const err = new Error('uh');
    // attach extra fields
    (err as any).password = 'p';
    const out = redact(err) as any;
    expect(out.message).toBe('uh');
    expect(out.password).toBe('[REDACTED]');

    const spy = vi.spyOn(console, 'info').mockImplementation(() => {});
    logger.info('hi', { secret: 's' });
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });

  it('writes a message when no metadata is supplied', () => {
    const spy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    logger.warn('plain');
    expect(spy).toHaveBeenCalledWith('plain');
    spy.mockRestore();
  });
});
