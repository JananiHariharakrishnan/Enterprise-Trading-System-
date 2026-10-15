export interface AppApiConfig {
  tradeApiUrl: string;
  authApiUrl: string;
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<AppApiConfig>;
  }
}

/**
 * Centralized API endpoints for the Frontend.
 * These string literals default to localhost for local development (ng serve),
 * and are substituted at runtime/container startup via URL_REWRITES in Docker.
 */
export const DEFAULT_TRADE_API_URL = 'http://localhost:8081';
export const DEFAULT_AUTH_API_URL = 'http://localhost:3000';

export const API_CONFIG: AppApiConfig = {
  tradeApiUrl: (typeof window !== 'undefined' && window.__APP_CONFIG__?.tradeApiUrl) || DEFAULT_TRADE_API_URL,
  authApiUrl: (typeof window !== 'undefined' && window.__APP_CONFIG__?.authApiUrl) || DEFAULT_AUTH_API_URL,
};

export function getTradeApiUrl(): string {
  const url = (typeof window !== 'undefined' && window.__APP_CONFIG__?.tradeApiUrl) || API_CONFIG.tradeApiUrl;
  return url.replace(/\/+$/, '');
}

export function getTradeApiV1Url(): string {
  const base = getTradeApiUrl();
  return base.endsWith('/api/v1') ? base : `${base}/api/v1`;
}

export function getAuthApiUrl(): string {
  const url = (typeof window !== 'undefined' && window.__APP_CONFIG__?.authApiUrl) || API_CONFIG.authApiUrl;
  return url.replace(/\/+$/, '');
}

