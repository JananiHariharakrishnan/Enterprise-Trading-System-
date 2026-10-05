import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UnauthorizedException } from '@nestjs/common';

const SECRET = 'guard-secret';
const USER_ID = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
const ACCOUNT_ID = 42;
const ROLES = ['CUSTOMER'];

describe('JwtAuthGuard execution', () => {
  beforeEach(() => {
    vi.resetModules();
    process.env.JWT_SECRET = SECRET;
  });

  it('accepts a valid authorization header and attaches auth payload', async () => {
    const tokens = await import('../tokens/auth.tokens.js');
    const { JwtAuthGuard } = await import('./jwt-auth.guard.js');

    const token = tokens.signAccessToken({ id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES });

    const req: any = { headers: { authorization: `Bearer ${token}` } };
    const ctx: any = { switchToHttp: () => ({ getRequest: () => req }) };

    const guard = new JwtAuthGuard();
    const ok = guard.canActivate(ctx);
    expect(ok).toBe(true);
    expect(req.auth).toBeDefined();
    expect(req.auth.sub).toBe(USER_ID);
    expect(req.auth.accountId).toBe(ACCOUNT_ID);
  });

  it('throws UnauthorizedException for missing header', async () => {
    const { JwtAuthGuard } = await import('./jwt-auth.guard.js');
    const req: any = { headers: {} };
    const ctx: any = { switchToHttp: () => ({ getRequest: () => req }) };
    const guard = new JwtAuthGuard();

    expect(() => guard.canActivate(ctx)).toThrow(UnauthorizedException);
  });

  it('throws UnauthorizedException for malformed header', async () => {
    const { JwtAuthGuard } = await import('./jwt-auth.guard.js');
    const req: any = { headers: { authorization: 'NotBearer token' } };
    const ctx: any = { switchToHttp: () => ({ getRequest: () => req }) };
    const guard = new JwtAuthGuard();

    expect(() => guard.canActivate(ctx)).toThrow(UnauthorizedException);
  });
});
