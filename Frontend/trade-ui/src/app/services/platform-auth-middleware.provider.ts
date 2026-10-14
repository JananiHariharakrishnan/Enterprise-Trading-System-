import { InjectionToken } from '@angular/core';
import { Middleware } from '../generated/trade';
import { createTokenInterceptor } from './http-token-interceptor.middleware';
import { TokenStorageService } from './token-storage.service';
import { getTradeApiUrl, getAuthApiUrl } from '../config/api.config';

export const PLATFORM_AUTH_MIDDLEWARE = new InjectionToken<Middleware>('PLATFORM_AUTH_MIDDLEWARE');

// Configure session expiry delay in seconds (0 = instant reload)
export const SESSION_EXPIRY_DELAY_SECONDS = 0;

export function createPlatformAuthMiddleware(
  tokenStorage: TokenStorageService,
  reloadHandler?: () => void
): Middleware {
  const isBrowser = typeof window !== 'undefined';
  const allowedOrigins = new Set<string>();

  if (isBrowser) {
    allowedOrigins.add(window.location.origin);
  }

  const tradeUrl = getTradeApiUrl();
  try {
    allowedOrigins.add(new URL(tradeUrl).origin);
  } catch {
    allowedOrigins.add(tradeUrl);
  }

  const authUrl = getAuthApiUrl();
  try {
    allowedOrigins.add(new URL(authUrl).origin);
  } catch {
    allowedOrigins.add(authUrl);
  }

  return createTokenInterceptor(tokenStorage, {
    allowedOrigins: Array.from(allowedOrigins),
    unauthenticatedPaths: ['/auth/login', '/auth/register', '/auth/refresh'],
    sessionExpiryDelaySeconds: SESSION_EXPIRY_DELAY_SECONDS,
    reloadHandler
  });
}
