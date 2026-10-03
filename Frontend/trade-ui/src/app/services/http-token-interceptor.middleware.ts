import { TokenStorageService } from './token-storage.service';

export interface TokenInterceptorOptions {
  allowedOrigins: string[]; // exact origins: "https://host:port" or "http://host:port"
  unauthenticatedPaths?: string[]; // paths (absolute, starting with /) to never attach token even on allowed origins
}

export function createTokenInterceptor(tokenStorage: TokenStorageService, opts: TokenInterceptorOptions) {
  const allowed = new Set(opts.allowedOrigins || []);
  const unauthPaths = new Set(opts.unauthenticatedPaths || []);

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

  return async function pre(context: any) {
    try {
      const url = new URL(context.url);
      const origin = url.origin;
      if (!allowed.has(origin)) return { url: context.url, init: context.init };

      // path-level exclusion (e.g. /auth/login)
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
      // on error, do not attach token
      return { url: context.url, init: context.init };
    }
  };
}
