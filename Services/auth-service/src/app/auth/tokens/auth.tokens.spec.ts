import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('jwt claims contract', () => {
  const SECRET = 'test-secret-please-change';
  const WRONG_SECRET = 'wrong-secret';
  const USER_ID = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
  const ACCOUNT_ID = 42;
  const ROLES = ['CUSTOMER'];

  beforeEach(() => {
    vi.resetModules();
    process.env.JWT_SECRET = SECRET;
  });

  it('a token is issued with the contract claims and expiry', async () => {
    const { signAccessToken, verifyAccessToken, ACCESS_TOKEN_LIFETIME_SECONDS } = await import('../tokens/auth.tokens.js');
    const token = signAccessToken({ id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES });
    const verification = verifyAccessToken(token);

    expect(verification.valid).toBe(true);
    if (verification.valid) {
      const payload = verification.payload;
      expect(payload.sub).toBe(USER_ID);
      expect(payload.accountId).toBe(ACCOUNT_ID);
      expect(payload.roles).toEqual(ROLES);
      expect(payload.iss).toBe('auth-service');
      expect(payload.exp! - payload.iat!).toBe(ACCESS_TOKEN_LIFETIME_SECONDS);
      expect(Object.keys(payload).sort()).toEqual(['accountId', 'exp', 'iat', 'iss', 'roles', 'sub']);
    }
  });

  it('the token signature verifies with the service key', async () => {
    const { signAccessToken } = await import('../tokens/auth.tokens.js');
    const token = signAccessToken({ id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES });

    vi.resetModules();
    process.env.JWT_SECRET = SECRET;
    const same = await import('../tokens/auth.tokens.js');
    expect(same.verifyAccessToken(token).valid).toBe(true);

    vi.resetModules();
    process.env.JWT_SECRET = WRONG_SECRET;
    const wrong = await import('../tokens/auth.tokens.js');
    expect(wrong.verifyAccessToken(token).valid).toBe(false);
  });

  it('no claim outside the contract set is present', async () => {
    const { signAccessToken, verifyAccessToken } = await import('../tokens/auth.tokens.js');
    const token = signAccessToken({ id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES });
    const verification = verifyAccessToken(token);

    expect(verification.valid).toBe(true);
    if (verification.valid) {
      expect(new Set(Object.keys(verification.payload))).toEqual(new Set(['sub', 'accountId', 'roles', 'iat', 'exp', 'iss']));
    }
  });
});

