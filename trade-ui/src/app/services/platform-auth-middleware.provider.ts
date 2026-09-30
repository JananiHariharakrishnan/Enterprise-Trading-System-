import { InjectionToken } from '@angular/core';
import { Middleware } from '../generated/trade';
import { createTokenInterceptor } from './http-token-interceptor.middleware';
import { TokenStorageService } from './token-storage.service';

export const PLATFORM_AUTH_MIDDLEWARE = new InjectionToken<Middleware>('PLATFORM_AUTH_MIDDLEWARE');

export function createPlatformAuthMiddleware(tokenStorage: TokenStorageService): Middleware {
  const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
  return {
    pre: createTokenInterceptor(tokenStorage, {
      allowedOrigins: [`http://${baseHost}:8888`, `http://${baseHost}:3000`],
      unauthenticatedPaths: ['/auth/login', '/auth/register', '/auth/refresh']
    })
  };
}