// services/auth-service/src/lib/logger.test.ts

import { logger } from './logger';

describe('logger redaction of primitive strings', () => {
    let infoSpy: jest.SpyInstance;

    beforeEach(() => {
        infoSpy = jest.spyOn(console, 'info').mockImplementation(() => {});
    });

    afterEach(() => {
        infoSpy.mockRestore();
    });

    test('redacts a JWT-like token passed as primitive string', () => {
        // sample JWT-like string (header.payload.signature)
        const jwtLike = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.' +
            'eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6Ikpv' +
            'aG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.' +
            'SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';

        logger.info('login', jwtLike);

        // logger.info(msg, JSON.stringify(redact(obj))) -> the second arg will be JSON-stringified
        expect(infoSpy).toHaveBeenCalledTimes(1);
        const callArgs = infoSpy.mock.calls[0];
        expect(callArgs[0]).toBe('login');
        // second arg should be the JSON string for "[REDACTED]"
        expect(callArgs[1]).toBe(JSON.stringify('[REDACTED]'));
    });

    test('does not redact short harmless strings', () => {
        const short = 'ok';
        logger.info('note', short);

        expect(infoSpy).toHaveBeenCalledTimes(1);
        const callArgs = infoSpy.mock.calls[0];
        expect(callArgs[0]).toBe('note');
        expect(callArgs[1]).toBe(JSON.stringify(short));
    });
});

describe('logger nested redaction and request safety', () => {
    let infoSpy: jest.SpyInstance;

    beforeEach(() => {
        infoSpy = jest.spyOn(console, 'info').mockImplementation(() => {});
    });

    afterEach(() => {
        infoSpy.mockRestore();
    });

    test('redacts nested password and token fields', () => {
        const payload = {
            user: {
                name: 'alice',
                credentials: {
                    password: 'secret-password',
                    nested: { password_hash: 'should-be-redacted' }
                }
            },
            accessToken: 'verylongrandomstringthatlookslikeatoken1234567890'
        };

        logger.info('auth-attempt', payload);

        expect(infoSpy).toHaveBeenCalledTimes(1);
        const callArgs = infoSpy.mock.calls[0];
        expect(callArgs[0]).toBe('auth-attempt');

        // The second argument is a JSON string of the redacted object
        const logged = JSON.parse(callArgs[1]);
        expect(logged.user.credentials.password).toBe('[REDACTED]');
        expect(logged.user.credentials.nested.password_hash).toBe('[REDACTED]');
        expect(logged.accessToken).toBe('[REDACTED]');
    });

    test('discourages logging whole request bodies by redacting sensitive keys', () => {
        const req = {
            body: {
                username: 'bob',
                password: 'hunter2',
                other: 'value'
            },
            headers: { 'content-type': 'application/json' }
        };

        logger.info('received-request', req.body);

        expect(infoSpy).toHaveBeenCalledTimes(1);
        const callArgs = infoSpy.mock.calls[0];
        const logged = JSON.parse(callArgs[1]);

        // password should be redacted; other fields preserved
        expect(logged.password).toBe('[REDACTED]');
        expect(logged.username).toBe('bob');
        expect(logged.other).toBe('value');
    });
});