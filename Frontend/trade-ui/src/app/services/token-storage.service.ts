import { Injectable } from '@angular/core';

const ACCESS_TOKEN_KEY = 'auth.accessToken';
const REFRESH_TOKEN_KEY = 'auth.refreshToken';
const EXPIRES_AT_KEY = 'auth.expiresAt';

/**
 * Default session duration in seconds (15 minutes).
 * Can be changed here or overridden when setting tokens.
 */
export const SESSION_DURATION_SECONDS = 15 * 60;

@Injectable({
  providedIn: 'root'
})
export class TokenStorageService {
  setTokens(accessToken: string, refreshToken: string, durationSeconds: number = SESSION_DURATION_SECONDS): void {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    sessionStorage.setItem(EXPIRES_AT_KEY, String(Date.now() + durationSeconds * 1000));
  }

  getAccessToken(): string | null {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  }

  getExpiresAt(): number | null {
    const raw = sessionStorage.getItem(EXPIRES_AT_KEY);
    return raw ? Number(raw) : null;
  }

  setExpiresAt(epochMs: number): void {
    sessionStorage.setItem(EXPIRES_AT_KEY, String(epochMs));
  }

  clear(): void {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(EXPIRES_AT_KEY);
  }
}

