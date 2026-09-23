import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { UserRecord } from './store';

export const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret';
export const ACCESS_TOKEN_LIFETIME = 15 * 60; // 15 minutes (seconds)

export function signAccessToken(user: UserRecord): string {
  // Use jsonwebtoken options so it emits `iat` and `exp` consistently.
  // Put non-reserved fields in the payload, and pass `subject`, `issuer`
  // and `expiresIn` as options to ensure `iat` and `exp` are present.
  const payload = {
    accountId: user.accountId,
    roles: user.roles,
  } as const;

  return jwt.sign(payload as any, JWT_SECRET, {
    algorithm: 'HS256',
    expiresIn: ACCESS_TOKEN_LIFETIME,
    issuer: 'auth-service',
    subject: user.id,
  });
}

export function verifyAccessToken(token: string) {
  try {
    const decoded = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] }) as any;
    return { valid: true, payload: decoded };
  } catch (err) {
    return { valid: false, error: err };
  }
}

export function makeRefreshToken() {
  return uuidv4().replace(/-/g, '');
}