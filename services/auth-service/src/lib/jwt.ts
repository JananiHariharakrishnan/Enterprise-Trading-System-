import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { UserRecord } from './store';

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret';
const ACCESS_TOKEN_LIFETIME = 15 * 60; // 15 minutes

export function signAccessToken(user: UserRecord) {
  const iat = Math.floor(Date.now() / 1000);
  const payload = {
    sub: user.id,
    accountId: user.accountId,
    roles: user.roles,
    iat,
    exp: iat + ACCESS_TOKEN_LIFETIME,
    iss: 'auth-service'
  } as any;
  return jwt.sign(payload, JWT_SECRET, { algorithm: 'HS256' });
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

