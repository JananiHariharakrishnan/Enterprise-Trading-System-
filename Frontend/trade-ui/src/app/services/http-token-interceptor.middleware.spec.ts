import { describe, it, expect } from 'vitest';
import { createTokenInterceptor } from './http-token-interceptor.middleware';

class DummyTokenStorage {
  token: string | null = 'abc123';
  getAccessToken() { return this.token; }
}

describe('createTokenInterceptor', () => {
  it('attaches Authorization header for allowed origin when no auth present', async () => {
    const ts = new DummyTokenStorage();
    const interceptor = createTokenInterceptor(ts as any, { allowedOrigins: ['https://api.example'] });

    const ctx = { url: 'https://api.example/path', init: { headers: {} } };
    const out = await interceptor.pre(ctx);
    expect((out.init.headers as Record<string,string>)['Authorization']).toBe('Bearer abc123');
  });

  it('does not attach when origin not allowed', async () => {
    const ts = new DummyTokenStorage();
    const interceptor = createTokenInterceptor(ts as any, { allowedOrigins: ['https://other'] });

    const ctx = { url: 'https://api.example/path', init: { headers: {} } };
    const out = await interceptor.pre(ctx);
    expect(out.init.headers).toBeDefined();
    expect((out.init.headers as Record<string,string>)['Authorization']).toBeUndefined();
  });

  it('does not overwrite existing Authorization header', async () => {
    const ts = new DummyTokenStorage();
    const interceptor = createTokenInterceptor(ts as any, { allowedOrigins: ['https://api.example'] });

    const ctx = { url: 'https://api.example/path', init: { headers: { Authorization: 'Basic xyz' } } };
    const out = await interceptor.pre(ctx);
    expect((out.init.headers as Record<string,string>)['Authorization']).toBe('Basic xyz');
  });

  it('skips when token missing', async () => {
    const ts = new DummyTokenStorage();
    ts.token = null;
    const interceptor = createTokenInterceptor(ts as any, { allowedOrigins: ['https://api.example'] });

    const ctx = { url: 'https://api.example/path', init: { headers: {} } };
    const out = await interceptor.pre(ctx);
    expect((out.init.headers as Record<string,string>)['Authorization']).toBeUndefined();
  });

  it('respects unauthenticatedPaths exclusion', async () => {
    const ts = new DummyTokenStorage();
    const interceptor = createTokenInterceptor(ts as any, { allowedOrigins: ['https://api.example'], unauthenticatedPaths: ['/auth/login'] });

    const ctx = { url: 'https://api.example/auth/login', init: { headers: {} } };
    const out = await interceptor.pre(ctx);
    expect((out.init.headers as Record<string,string>)['Authorization']).toBeUndefined();
  });

  it('handles headers as Headers instance', async () => {
    const ts = new DummyTokenStorage();
    const interceptor = createTokenInterceptor(ts as any, { allowedOrigins: ['https://api.example'] });

    const headers = new Headers();
    headers.append('x-test', '1');
    const ctx = { url: 'https://api.example/path', init: { headers } };
    const out = await interceptor.pre(ctx);
    expect((out.init.headers as Record<string,string>)['x-test']).toBe('1');
    expect((out.init.headers as Record<string,string>)['Authorization']).toBe('Bearer abc123');
  });
});
