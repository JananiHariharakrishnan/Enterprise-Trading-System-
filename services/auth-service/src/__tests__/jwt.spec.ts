// NOTE: set process.env before requiring the module so the module reads the desired secret.
describe('jwt signing and verification contract', () => {
    const SECRET = 'test-secret-please-change';
    const WRONG_SECRET = 'wrong-secret';
    const USER_ID = '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f';
    const ACCOUNT_ID = 42;
    const ROLES = ['CUSTOMER'];

    beforeEach(() => {
        jest.resetModules();
        // Ensure the module reads the test secret at import time.
        process.env.JWT_SECRET = SECRET;
    });

    test('a token is issued with the contract claims and expiry', () => {
        const { signAccessToken, verifyAccessToken, ACCESS_TOKEN_LIFETIME } = require('../lib/jwt');

        const user = {
            id: USER_ID,
            accountId: ACCOUNT_ID,
            roles: ROLES,
            // other store fields are not required for signing
        };

        const token: string = signAccessToken(user);

        expect(typeof token).toBe('string');
        expect(token.split('.').length).toBe(3);

        const verification = verifyAccessToken(token);
        expect(verification.valid).toBe(true);

        const payload = verification.payload;
        // Required claims present and correct types/values
        expect(payload.sub).toBe(USER_ID);
        expect(payload.accountId).toBe(ACCOUNT_ID);
        expect(Array.isArray(payload.roles)).toBe(true);
        expect(payload.roles).toEqual(ROLES);

        // iat and exp as integers (seconds)
        expect(Number.isInteger(payload.iat)).toBe(true);
        expect(Number.isInteger(payload.exp)).toBe(true);

        // expiry is iat + ACCESS_TOKEN_LIFETIME
        expect(payload.exp - payload.iat).toBe(ACCESS_TOKEN_LIFETIME);

        // iat should not be in the future; exp should be after now
        const now = Math.floor(Date.now() / 1000);
        expect(payload.iat <= now).toBe(true);
        expect(payload.exp > now).toBe(true);

        // Issuer present and readable
        expect(typeof payload.iss).toBe('string');
        expect(payload.iss).toBe('auth-service');

        // No unauthorized claims: exact key set check
        const expectedKeys = ['sub', 'accountId', 'roles', 'iat', 'exp', 'iss'].sort();
        const actualKeys = Object.keys(payload).sort();
        expect(actualKeys).toEqual(expectedKeys);
    });

    test('the token signature verifies with the service key and fails with a different key', () => {
        // Sign with SECRET
        const { signAccessToken } = require('../lib/jwt');
        const user = { id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES };
        const token: string = signAccessToken(user);

        // Verify using same secret - should succeed
        jest.resetModules();
        process.env.JWT_SECRET = SECRET;
        const { verifyAccessToken: verifySame } = require('../lib/jwt');
        const ok = verifySame(token);
        expect(ok.valid).toBe(true);

        // Verify using wrong secret - should fail signature verification
        jest.resetModules();
        process.env.JWT_SECRET = WRONG_SECRET;
        const { verifyAccessToken: verifyWrong } = require('../lib/jwt');
        const bad = verifyWrong(token);
        expect(bad.valid).toBe(false);
    });

    test('no claim outside the contract set is present (again, explicit check)', () => {
        const { signAccessToken, verifyAccessToken } = require('../lib/jwt');
        const user = { id: USER_ID, accountId: ACCOUNT_ID, roles: ROLES };
        const token: string = signAccessToken(user);
        const verification = verifyAccessToken(token);
        expect(verification.valid).toBe(true);
        const payload = verification.payload;

        // Ensure only the contract fields exist
        const expectedKeys = new Set(['sub', 'accountId', 'roles', 'iat', 'exp', 'iss']);
        const keys = Object.keys(payload);
        for (const k of keys) {
            expect(expectedKeys.has(k)).toBe(true);
        }
        // Also ensure counts match exactly
        expect(keys.length).toBe(expectedKeys.size);
    });
});