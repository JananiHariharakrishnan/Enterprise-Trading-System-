import { describe, it, expect, vi } from 'vitest';
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
      url: 'http://localhost:8081/api/v1/accounts',
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

  it('reloads page on 401 response', async () => {
    const mockTokenStorage = new MockTokenStorage();
    const clearSpy = vi.spyOn(mockTokenStorage, 'clear');
    const reloadSpy = vi.fn();

    const middleware = createPlatformAuthMiddleware(mockTokenStorage);
    const response = new Response('Unauthorized', { status: 401 });

    // Mock setTimeout and window.location.reload
    vi.useFakeTimers();
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, reload: reloadSpy },
      writable: true,
    });

    // Test with protected API route (not an unauthenticated path)
    await middleware.post?.({
      url: 'http://localhost:8081/api/v1/accounts',
      response
    } as any);

    expect(clearSpy).toHaveBeenCalled();

    vi.runAllTimers();
    expect(reloadSpy).toHaveBeenCalled();

    // Restore
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    });
    vi.useRealTimers();
  });

  it('reloads page on 403 response', async () => {
    const mockTokenStorage = new MockTokenStorage();
    const clearSpy = vi.spyOn(mockTokenStorage, 'clear');
    const reloadSpy = vi.fn();

    const middleware = createPlatformAuthMiddleware(mockTokenStorage);
    const response = new Response('Forbidden', { status: 403 });

    // Mock setTimeout and window.location.reload
    vi.useFakeTimers();
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, reload: reloadSpy },
      writable: true,
    });

    // Test with protected API route (not an unauthenticated path)
    await middleware.post?.({
      url: 'http://localhost:8081/api/v1/accounts',
      response
    } as any);

    expect(clearSpy).toHaveBeenCalled();

    vi.runAllTimers();
    expect(reloadSpy).toHaveBeenCalled();

    // Restore
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    });
    vi.useRealTimers();
  });

  it('does not reload page on 401 response for unauthenticated paths', async () => {
    const mockTokenStorage = new MockTokenStorage();
    const clearSpy = vi.spyOn(mockTokenStorage, 'clear');
    const reloadSpy = vi.fn();

    const middleware = createPlatformAuthMiddleware(mockTokenStorage);
    const response = new Response('Unauthorized', { status: 401 });

    // Mock setTimeout and window.location.reload
    vi.useFakeTimers();
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, reload: reloadSpy },
      writable: true,
    });

    // Test with unauthenticated path (should not clear or reload)
    await middleware.post?.({
      url: 'http://localhost:3000/auth/login',
      response
    } as any);

    expect(clearSpy).not.toHaveBeenCalled();

    vi.runAllTimers();
    expect(reloadSpy).not.toHaveBeenCalled();

    // Restore
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    });
    vi.useRealTimers();
  });
});

