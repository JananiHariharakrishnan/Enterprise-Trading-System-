import { describe, it, expect } from 'vitest';
import { createPlatformAuthMiddleware } from './platform-auth-middleware.provider';
import { TokenStorageService } from './token-storage.service';

class MockTokenStorage implements TokenStorageService {
  setTokens(): void {}
  getAccessToken(): string | null { return 'token-xyz'; }
  getRefreshToken(): string | null { return null; }
  clear(): void {}
}

describe('platform auth middleware provider', () => {
  it('attaches bearer token for protected platform routes', async () => {
    const middleware = createPlatformAuthMiddleware(new MockTokenStorage());
    const out = await middleware.pre?.({
      url: 'http://localhost:8888/api/v1/accounts',
      init: { headers: {} }
    } as any);

    const headers = (out?.init as any).headers;
    expect(headers['Authorization'] || headers['authorization']).toBe('Bearer token-xyz');
  });

  it('does not attach token for unauthenticated auth routes', async () => {
    const middleware = createPlatformAuthMiddleware(new MockTokenStorage());
    const out = await middleware.pre?.({
      url: 'http://localhost:3000/auth/login',
      init: { headers: {} }
    } as any);

    const headers = (out?.init as any).headers;
    expect(headers['Authorization'] || headers['authorization']).toBeUndefined();
  });
});
