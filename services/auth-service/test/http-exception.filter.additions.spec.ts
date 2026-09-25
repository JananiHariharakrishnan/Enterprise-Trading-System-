import { describe, it, expect, vi } from 'vitest';
import * as filter from '../src/app/common/http-exception.filter.js';
import { HttpException } from '@nestjs/common';

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
});
