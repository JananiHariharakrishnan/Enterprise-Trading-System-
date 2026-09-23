import { verifyAccessToken } from './jwt';

export type AuthPayload = {
  sub: string;
  accountId: number;
  roles: string[];
  iat?: number;
  exp?: number;
  iss?: string;
};

export function parseAuthorizationHeader(authHeader: string | null): { ok: true; token: string } | { ok: false; error: string } {
  if (!authHeader) return { ok: false, error: 'AUTH-401' };
  if (!authHeader.toLowerCase().startsWith('bearer ')) return { ok: false, error: 'AUTH-401' };
  const token = authHeader.slice(7).trim();
  if (!token) return { ok: false, error: 'AUTH-401' };
  return { ok: true, token };
}

export function verifyAuthorizationHeader(authHeader: string | null): { ok: true; payload: AuthPayload } | { ok: false; error: string } {
  const parsed = parseAuthorizationHeader(authHeader);
  if (!parsed.ok) return { ok: false, error: parsed.error };

  const result = verifyAccessToken(parsed.token);
  if (!result.valid) return { ok: false, error: 'AUTH-401' };

  const payload = result.payload as any;

  if (
    typeof payload?.sub !== 'string' ||
    typeof payload?.accountId !== 'number' ||
    !Array.isArray(payload?.roles) ||
    payload.roles.length === 0 ||
    !payload.roles.every((role: unknown) => typeof role === 'string')
  ) {
    return { ok: false, error: 'AUTH-401' };
  }

  return { ok: true, payload: payload as AuthPayload };
}

export class AuthError extends Error {
  code = 'AUTH-401';
  constructor(message = 'Unauthorised') {
    super(message);
    this.name = 'AuthError';
  }
}

export function withAuth(handler: (req: Request, payload: AuthPayload) => Promise<Response> | Response) {
  return async function (req: Request) {
    const verified = verifyAuthorizationHeader(req.headers.get('authorization'));
    if (!verified.ok) {
      return new Response(JSON.stringify({ errorCode: 'AUTH-401', message: 'Unauthorised' }), { status: 401, headers: { 'content-type': 'application/json' } });
    }

    return handler(req, verified.payload);
  };
}
