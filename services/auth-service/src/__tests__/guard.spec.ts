describe('auth guard', () => {
  const SECRET = 'test-secret-please-change';
  const WRONG_SECRET = 'wrong-secret';
  const USER_ID = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
  const ACCOUNT_ID = 42;
  const ROLES = ['CUSTOMER'];

  beforeEach(() => {
    jest.resetModules();
    process.env.JWT_SECRET = SECRET;
  });

  test('a valid token is accepted', () => {
    const { signAccessToken } = require('../lib/jwt');
    const { verifyAuthorizationHeader } = require('../lib/guard');

    const user = { id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES };
    const token = signAccessToken(user);

    const res = verifyAuthorizationHeader(`Bearer ${token}`);
    expect(res.ok).toBe(true);
    if (res.ok) {
      expect(res.payload.sub).toBe(USER_ID);
      expect(res.payload.accountId).toBe(ACCOUNT_ID);
      expect(Array.isArray(res.payload.roles)).toBe(true);
    }
  });

  test('an expired token is refused', () => {
    // Build a token with past expiry by signing with expiresIn negative value
    jest.resetModules();
    process.env.JWT_SECRET = SECRET;
    const jwtLib = require('jsonwebtoken');
    const { signAccessToken } = require('../lib/jwt');
    const { verifyAuthorizationHeader } = require('../lib/guard');

    // Use the same payload contract but set exp in the past via options
    const payload = { accountId: ACCOUNT_ID, roles: ROLES };
    const token = jwtLib.sign(payload, SECRET, { algorithm: 'HS256', expiresIn: -10, issuer: 'auth-service', subject: USER_ID });

    const res = verifyAuthorizationHeader(`Bearer ${token}`);
    expect(res.ok).toBe(false);
    // Ensure the guard returns the AUTH-401 error code
    expect(res).toHaveProperty('error', 'AUTH-401');
  });

  test('a token with wrong signature is refused before any claim is read', () => {
    // Sign a genuine, unexpired token with a different key
    const jwtLib = require('jsonwebtoken');
    // create token with WRONG_SECRET
    const payload = { accountId: ACCOUNT_ID, roles: ROLES };
    const token = jwtLib.sign(payload, WRONG_SECRET, { algorithm: 'HS256', expiresIn: '1h', issuer: 'auth-service', subject: USER_ID });

    // Ensure our env secret is the correct one
    jest.resetModules();
    process.env.JWT_SECRET = SECRET;
    const { verifyAuthorizationHeader } = require('../lib/guard');

    const res = verifyAuthorizationHeader(`Bearer ${token}`);
    expect(res.ok).toBe(false);
    // Ensure the guard returns the AUTH-401 error code
    expect(res).toHaveProperty('error', 'AUTH-401');
  });

  test('a malformed header is refused', () => {
    const { verifyAuthorizationHeader } = require('../lib/guard');

    const r1 = verifyAuthorizationHeader(null);
    expect(r1.ok).toBe(false);
    expect(r1).toHaveProperty('error', 'AUTH-401');

    const r2 = verifyAuthorizationHeader('');
    expect(r2.ok).toBe(false);
    expect(r2).toHaveProperty('error', 'AUTH-401');

    const r3 = verifyAuthorizationHeader('Bearer');
    expect(r3.ok).toBe(false);
    expect(r3).toHaveProperty('error', 'AUTH-401');

    const r4 = verifyAuthorizationHeader('NotBearer token');
    expect(r4.ok).toBe(false);
    expect(r4).toHaveProperty('error', 'AUTH-401');
  });

  test('withAuth returns 401 Response with errorCode AUTH-401 when unauthorised', async () => {
    const { withAuth } = require('../lib/guard');

    const handler = withAuth(async (req, payload) => {
      return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'content-type': 'application/json' } });
    });

    const response = await handler(new Request('http://localhost/', { method: 'GET' }));
    expect(response.status).toBe(401);
    const body = JSON.parse(await response.text());
    expect(body.errorCode).toBe('AUTH-401');
  });
});