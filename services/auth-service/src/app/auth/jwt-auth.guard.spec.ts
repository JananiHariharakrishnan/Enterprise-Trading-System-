import jwt from 'jsonwebtoken';
import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('jwt auth guard helpers', () => {
  const SECRET = 'guard-secret';
  const WRONG_SECRET = 'wrong-secret';
  const USER_ID = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
  const ACCOUNT_ID = 42;
  const ROLES = ['CUSTOMER'];

  beforeEach(() => {
    vi.resetModules();
    process.env.JWT_SECRET = SECRET;
  });

  it('a valid token is accepted', async () => {
    const tokens = await import('./auth.tokens.js');
    const token = tokens.signAccessToken({ id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES });
    const result = tokens.verifyAuthorizationHeader(`Bearer ${token}`);

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.payload.sub).toBe(USER_ID);
      expect(result.payload.accountId).toBe(ACCOUNT_ID);
    }
  });

  it('an expired token is refused', async () => {
    const tokens = await import('./auth.tokens.js');
    const token = jwt.sign({ accountId: ACCOUNT_ID, roles: ROLES }, SECRET, {
      algorithm: 'HS256',
      expiresIn: -10,
      issuer: 'auth-service',
      subject: USER_ID,
    });

    expect(tokens.verifyAuthorizationHeader(`Bearer ${token}`)).toEqual({ ok: false, error: 'AUTH-401' });
  });

  it('a token with a wrong signature is refused before any claim is read', async () => {
    const tokens = await import('./auth.tokens.js');
    const token = jwt.sign({ accountId: ACCOUNT_ID, roles: ROLES }, WRONG_SECRET, {
      algorithm: 'HS256',
      expiresIn: '1h',
      issuer: 'auth-service',
      subject: USER_ID,
    });

    expect(tokens.verifyAuthorizationHeader(`Bearer ${token}`)).toEqual({ ok: false, error: 'AUTH-401' });
  });

  it('a malformed header is refused', async () => {
    const tokens = await import('./auth.tokens.js');

    expect(tokens.verifyAuthorizationHeader(null)).toEqual({ ok: false, error: 'AUTH-401' });
    expect(tokens.verifyAuthorizationHeader('')).toEqual({ ok: false, error: 'AUTH-401' });
    expect(tokens.verifyAuthorizationHeader('Bearer')).toEqual({ ok: false, error: 'AUTH-401' });
    expect(tokens.verifyAuthorizationHeader('NotBearer token')).toEqual({ ok: false, error: 'AUTH-401' });
  });
});

