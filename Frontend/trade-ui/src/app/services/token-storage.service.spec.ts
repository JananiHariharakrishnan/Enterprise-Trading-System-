import { describe, it, expect, beforeEach } from 'vitest';
import { TokenStorageService, SESSION_DURATION_SECONDS } from './token-storage.service';

describe('TokenStorageService', () => {
  let service: TokenStorageService;

  beforeEach(() => {
    sessionStorage.clear();
    service = new TokenStorageService();
  });

  it('stores and retrieves tokens and calculates expiresAt with default 15 minutes', () => {
    const before = Date.now();
    service.setTokens('access-123', 'refresh-123');
    const after = Date.now();

    expect(service.getAccessToken()).toBe('access-123');
    expect(service.getRefreshToken()).toBe('refresh-123');

    const expiresAt = service.getExpiresAt();
    expect(expiresAt).not.toBeNull();
    expect(expiresAt!).toBeGreaterThanOrEqual(before + SESSION_DURATION_SECONDS * 1000);
    expect(expiresAt!).toBeLessThanOrEqual(after + SESSION_DURATION_SECONDS * 1000);
  });

  it('allows customizing durationSeconds when setting tokens', () => {
    const customDuration = 300; // 5 minutes
    const before = Date.now();
    service.setTokens('access-123', 'refresh-123', customDuration);

    const expiresAt = service.getExpiresAt();
    expect(expiresAt).not.toBeNull();
    expect(expiresAt!).toBeGreaterThanOrEqual(before + customDuration * 1000);
  });

  it('clears all tokens and expiresAt on clear()', () => {
    service.setTokens('access-123', 'refresh-123');
    expect(service.getAccessToken()).toBe('access-123');
    expect(service.getExpiresAt()).not.toBeNull();

    service.clear();

    expect(service.getAccessToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    expect(service.getExpiresAt()).toBeNull();
  });

  it('allows manual setExpiresAt', () => {
    const target = 1800000000000;
    service.setExpiresAt(target);
    expect(service.getExpiresAt()).toBe(target);
  });
});
