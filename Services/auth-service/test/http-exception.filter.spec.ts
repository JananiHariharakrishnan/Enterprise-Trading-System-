import { describe, it, expect } from 'vitest';
import {
  envelope,
  formatValidationMessage,
  formatConflictMessage,
  formatUnexpectedMessage,
  isDatabasePasswordConfigError,
  isPostgresLikeError,
  formatPostgresMessage,
  databaseStatus,
  PlatformExceptionFilter,
} from '../src/app/common/http-exception.filter.js';

describe('http-exception.filter', () => {
  it('envelope validation', () => {
    const env = envelope(422, undefined);
    expect(env.errorCode).toBe('VAL-422');
  });

  it('formatValidationMessage with string response', () => {
    const fake = { getResponse: () => 'oops' } as any;
    expect(formatValidationMessage(fake)).toContain('oops');
  });

  it('formatConflictMessage and postgres formatter', () => {
    const pg: any = { code: '23505', message: 'dup', detail: 'd', table: 't', column: 'c', constraint: 'uq' };
    process.env.NODE_ENV = 'development';
    expect(formatConflictMessage(pg)).toContain('Registration failed');
    expect(formatPostgresMessage(pg)).toContain('[23505]');
    expect(databaseStatus(pg)).toBe(409);
  });

  it('formatUnexpectedMessage with password config error', () => {
    const err = new Error('SASL: SCRAM-SERVER-FIRST-MESSAGE: client password must be a string');
    const m = formatUnexpectedMessage(err);
    expect(m).toContain('database password');
    expect(isDatabasePasswordConfigError(err)).toBe(true);
  });

  it('PlatformExceptionFilter handles HttpException and generic', () => {
    const filter = new PlatformExceptionFilter();
    const res: any = { status: vi.fn().mockReturnThis(), json: vi.fn() };
    const host: any = { switchToHttp: () => ({ getResponse: () => res }) };

    filter.catch({ getStatus: () => 418, instanceof: () => true } as any, host);
    expect(res.status).toHaveBeenCalled();

    filter.catch({ message: 'boom' }, host);
    expect(res.json).toHaveBeenCalled();
  });
});
