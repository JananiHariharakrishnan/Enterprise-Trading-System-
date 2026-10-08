import { describe, it, expect, vi } from 'vitest';
import * as filter from '../src/app/common/http-exception.filter.js';
import { HttpException, HttpStatus } from '@nestjs/common';

describe('http-exception.filter skeleton', () => {
  it('formatValidationMessage string response', () => {
    const ex = new HttpException('bad', 422);
    const msg = filter.formatValidationMessage(ex as any);
    expect(msg).toContain('Invalid input');
  });

  it('formatConflictMessage postgres-like non-prod', () => {
    const ex = { code: '23505', message: 'dup', detail: 'detail' } as any;
    process.env.NODE_ENV = 'test';
    const msg = filter.formatConflictMessage(ex);
    expect(msg).toContain('Registration failed');
  });

  it('formatUnexpectedMessage production hides details', () => {
    const old = process.env.NODE_ENV;
    process.env.NODE_ENV = 'production';
    const msg = filter.formatUnexpectedMessage(new Error('boom'));
    expect(msg).toBe('Unexpected server error');
    process.env.NODE_ENV = old;
  });

  it('covers remaining status, validation, and passthrough branches', () => {
    expect(filter.envelope(HttpStatus.FORBIDDEN).message).toBe('Unauthorised');
    expect(filter.envelope(HttpStatus.NOT_FOUND).errorCode).toBe('SRV-500');

    const stringMessage = new HttpException({ message: 'email must be an email' }, HttpStatus.UNPROCESSABLE_ENTITY);
    expect(filter.formatValidationMessage(stringMessage)).toBe('Invalid input: email must be an email');
    expect(filter.formatValidationMessage(new HttpException({ message: '   ' }, HttpStatus.BAD_REQUEST))).toBe('Invalid input');
    expect(filter.formatConflictMessage(new Error('not postgres'))).toBe('Registration failed');

    const previous = process.env.NODE_ENV;
    process.env.NODE_ENV = 'development';
    expect(filter.formatUnexpectedMessage({ code: '08006', message: 'connection failure' })).toContain('connection failure');
    process.env.NODE_ENV = previous;

    expect(filter.databaseStatus({ code: '23503', message: 'fk' })).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(filter.databaseStatus({ code: '23502', message: 'null' })).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(filter.databaseStatus({ code: '22P02', message: 'bad input' })).toBe(HttpStatus.UNPROCESSABLE_ENTITY);
    expect(filter.databaseStatus({ code: '08006', message: 'down' })).toBe(HttpStatus.INTERNAL_SERVER_ERROR);

    const response = { status: vi.fn().mockReturnThis(), json: vi.fn() };
    const host = { switchToHttp: () => ({ getResponse: () => response }) };
    const raw = { errorCode: 'AUTH-401', message: 'Unauthorised' };
    new filter.PlatformExceptionFilter().catch(new HttpException(raw, HttpStatus.UNAUTHORIZED), host as any);
    expect(response.json).toHaveBeenCalledWith(raw);
  });
});
