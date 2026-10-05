import { describe, it, expect } from 'vitest';
import jwt from 'jsonwebtoken';
import {
  signAccessToken,
  verifyAccessToken,
  parseAuthorizationHeader,
  verifyAuthorizationHeader,
  makeRefreshToken,
  parseRefreshToken,
} from '../src/app/auth/tokens/auth.tokens.js';

describe('auth.tokens', () => {
  it('parseAuthorizationHeader rejects null/invalid/empty', () => {
    expect(parseAuthorizationHeader(null).ok).toBe(false);
    expect(parseAuthorizationHeader('Basic x').ok).toBe(false);
    expect(parseAuthorizationHeader('Bearer ').ok).toBe(false);
    const ok = parseAuthorizationHeader('Bearer abc');
    expect(ok.ok).toBe(true);
    if (ok.ok) expect(ok.token).toBe('abc');
  });

  it('sign and verify access token roundtrip', () => {
    const token = signAccessToken({ id: 'u1', accountId: 1, roles: ['CUSTOMER'] });
    const res = verifyAccessToken(token);
    expect(res.valid).toBe(true);
    if (res.valid) expect(res.payload.sub).toBe('u1');
  });

  it('verifyAuthorizationHeader rejects malformed payload', () => {
    // create a token missing roles
    const small = jwt.sign({ accountId: 1 }, process.env.JWT_SECRET || 'dev-secret', { algorithm: 'HS256', subject: 'u1' });
    const bad = verifyAuthorizationHeader(`Bearer ${small}`);
    expect(bad.ok).toBe(false);
  });

  it('makeRefreshToken and parseRefreshToken', () => {
    const t = makeRefreshToken();
    const parsed = parseRefreshToken(t);
    expect(parsed).not.toBeNull();
    if (parsed) {
      expect(parsed.tokenId.length).toBeGreaterThan(0);
      expect(parsed.secret.length).toBeGreaterThan(0);
    }

    expect(parseRefreshToken('no-sep')).toBeNull();
    expect(parseRefreshToken('.start')).toBeNull();
    expect(parseRefreshToken('end.')).toBeNull();
  });
});
