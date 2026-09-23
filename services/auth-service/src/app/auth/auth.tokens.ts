import { randomBytes } from 'node:crypto';
import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import type { AuthPayload } from './interfaces/auth-payload.interface.js';

export const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret';
export const ACCESS_TOKEN_LIFETIME_SECONDS = 15 * 60;
export const REFRESH_TOKEN_SEPARATOR = '.';
let lastIssuedAt = 0;

export type JwtUser = {
  id: string;
  accountId: number;
  roles: string[];
};

export function signAccessToken(user: JwtUser): string {
  const now = Math.floor(Date.now() / 1000);
  const issuedAt = now > lastIssuedAt ? now : lastIssuedAt + 1;
  lastIssuedAt = issuedAt;

  return jwt.sign(
    {
      accountId: user.accountId,
      iat: issuedAt,
      roles: user.roles,
    },
    JWT_SECRET,
    {
      algorithm: 'HS256',
      expiresIn: ACCESS_TOKEN_LIFETIME_SECONDS,
      issuer: 'auth-service',
      subject: user.id,
    },
  );
}

export function verifyAccessToken(token: string): { valid: true; payload: AuthPayload } | { valid: false; error: unknown } {
  try {
    return {
      valid: true,
      payload: jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] }) as AuthPayload,
    };
  } catch (error) {
    return { valid: false, error };
  }
}

export function parseAuthorizationHeader(authHeader: string | null): { ok: true; token: string } | { ok: false; error: string } {
  if (!authHeader) {
    return { ok: false, error: 'AUTH-401' };
  }

  if (!authHeader.toLowerCase().startsWith('bearer ')) {
    return { ok: false, error: 'AUTH-401' };
  }

  const token = authHeader.slice(7).trim();
  if (!token) {
    return { ok: false, error: 'AUTH-401' };
  }

  return { ok: true, token };
}

export function verifyAuthorizationHeader(authHeader: string | null): { ok: true; payload: AuthPayload } | { ok: false; error: string } {
  const parsed = parseAuthorizationHeader(authHeader);
  if (!parsed.ok) {
    return parsed;
  }

  const result = verifyAccessToken(parsed.token);
  if (!result.valid) {
    return { ok: false, error: 'AUTH-401' };
  }

  const payload = result.payload;
  if (
    typeof payload.sub !== 'string' ||
    typeof payload.accountId !== 'number' ||
    !Array.isArray(payload.roles) ||
    payload.roles.length === 0 ||
    !payload.roles.every((role) => typeof role === 'string')
  ) {
    return { ok: false, error: 'AUTH-401' };
  }

  return { ok: true, payload };
}

export function makeRefreshToken(): string {
  return `${uuidv4()}${REFRESH_TOKEN_SEPARATOR}${randomBytes(32).toString('base64url')}`;
}

export function parseRefreshToken(token: string): { tokenId: string; secret: string } | null {
  const separatorIndex = token.indexOf(REFRESH_TOKEN_SEPARATOR);
  if (separatorIndex <= 0 || separatorIndex >= token.length - 1) {
    return null;
  }

  return {
    tokenId: token.slice(0, separatorIndex),
    secret: token.slice(separatorIndex + 1),
  };
}

