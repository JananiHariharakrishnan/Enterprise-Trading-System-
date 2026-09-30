import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createTokenInterceptor } from './http-token-interceptor.middleware';
import { TokenStorageService } from './token-storage.service';

class MockTokenStorage implements TokenStorageService {
  private token: string | null = null;
  setTokens(): void {}
  getAccessToken(): string | null { return this.token; }
  getRefreshToken(): string | null { return null; }
  clear(): void {}
  // test helper
  setToken(token: string | null) { this.token = token; }
}

describe('Token interceptor', () => {
  let tokenStorage: MockTokenStorage;
  let interceptor: ReturnType<typeof createTokenInterceptor>;

  beforeEach(() => {
    tokenStorage = new MockTokenStorage();
    interceptor = createTokenInterceptor(tokenStorage as any, {
      allowedOrigins: ['http://localhost:8080', 'http://localhost:3000'],
      unauthenticatedPaths: ['/auth/login', '/auth/register', '/auth/refresh']
    });
  });

  it('request to platform API carries the bearer token', async () => {
    tokenStorage.setToken('abc-123');
    const ctx = { url: 'http://localhost:8080/api/v1/accounts/1', init: { headers: {} } as RequestInit };
    const out = await interceptor(ctx as any);
    const headers = (out.init as any).headers;
    expect(headers['Authorization'] || headers['authorization']).toBe(`Bearer abc-123`);
  });

  it('request to third-party origin does not carry the bearer token', async () => {
    tokenStorage.setToken('abc-123');
    const ctx = { url: 'https://third.party.net/data', init: { headers: {} } as RequestInit };
    const out = await interceptor(ctx as any);
    const headers = (out.init as any).headers;
    expect(headers['Authorization'] || headers['authorization']).toBeUndefined();
  });

  it('unauthenticated auth routes are called without a header', async () => {
    tokenStorage.setToken('abc-123');
    for (const path of ['/auth/login', '/auth/register', '/auth/refresh']) {
      const ctx = { url: `http://localhost:3000${path}`, init: { headers: {} } as RequestInit };
      const out = await interceptor(ctx as any);
      const headers = (out.init as any).headers;
      expect(headers['Authorization'] || headers['authorization']).toBeUndefined();
    }
  });
  it('preserves existing Authorization header', async () => {
    tokenStorage.setToken('abc-123');
    const ctx = {
      url: 'http://localhost:8080/api/v1/accounts/1',
      init: { headers: { Authorization: 'Bearer existing' } } as RequestInit
    };
    const out = await interceptor(ctx as any);
    const headers = (out.init as any).headers;
    // interceptor should NOT overwrite an existing Authorization header
    expect(headers['Authorization'] || headers['authorization']).toBe('Bearer existing');
  });

  it('does not attach token when URL is malformed', async () => {
    tokenStorage.setToken('abc-123');
    // this is not a valid URL and will throw inside the interceptor's new URL(...) call
    const ctx = { url: 'not-a-url', init: { headers: {} } as RequestInit };
    const out = await interceptor(ctx as any);
    const headers = (out.init as any).headers;
    // on error the interceptor returns the original init unchanged (no Authorization added)
    expect(headers['Authorization'] || headers['authorization']).toBeUndefined();
  });
});
