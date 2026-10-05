import { TokenStorageService } from './token-storage.service';

export interface TokenInterceptorOptions {
  allowedOrigins: string[];
  unauthenticatedPaths?: string[];
  sessionExpiryDelaySeconds?: number; // Delay before reloading on session expiry (default: 0)
}

export function createTokenInterceptor(tokenStorage: TokenStorageService, opts: TokenInterceptorOptions) {
  const allowed = new Set(opts.allowedOrigins || []);
  const unauthPaths = new Set(opts.unauthenticatedPaths || []);
  const expiryDelay = (opts.sessionExpiryDelaySeconds ?? 0) * 1000; // Convert to ms

  function headersToRecord(h: HeadersInit | undefined): Record<string, string> {
    const out: Record<string, string> = {};
    if (!h) return out;
    if (h instanceof Headers) {
      h.forEach((v, k) => { out[k] = v; });
      return out;
    }
    if (Array.isArray(h)) {
      for (const pair of h) {
        if (pair && pair.length >= 2) out[pair[0]] = pair[1];
      }
      return out;
    }
    return { ...(h as Record<string, string>) };
  }

  const pre = async function (context: any) {
    try {
      const url = new URL(context.url);
      const origin = url.origin;
      if (!allowed.has(origin)) return { url: context.url, init: context.init };

      if (unauthPaths.has(url.pathname)) return { url: context.url, init: context.init };

      const token = tokenStorage.getAccessToken();
      if (!token) return { url: context.url, init: context.init };

      const currentHeaders = headersToRecord(context.init && context.init.headers);
      const hasAuth = Object.keys(currentHeaders).some(k => k.toLowerCase() === 'authorization');
      if (!hasAuth) {
        currentHeaders['Authorization'] = `Bearer ${token}`;
      }

      const init = { ...context.init, headers: currentHeaders };
      return { url: context.url, init };
    } catch (e) {
      return { url: context.url, init: context.init };
    }
  };

  const post = async function (context: any): Promise<Response | void> {
    try {
      const response = context.response as Response;

      // Handle session expiration: 401 or 403
      if (response.status === 401 || response.status === 403) {
        tokenStorage.clear();
        // Reload page after configured delay
        setTimeout(() => {
          window.location.reload();
        }, expiryDelay);
      }

      return response;
    } catch (e) {
      return context.response instanceof Response ? context.response : undefined;
    }
  };

  return { pre, post };
}


