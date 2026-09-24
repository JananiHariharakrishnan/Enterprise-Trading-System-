import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';
import { verifyAuthorizationHeader } from '../tokens/auth.tokens.js';
import type { AuthPayload } from '../interfaces/auth-payload.interface.js';

export type AuthenticatedRequest = Request & { auth?: AuthPayload };

@Injectable()
export class JwtAuthGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const verified = verifyAuthorizationHeader(request.headers.authorization ?? null);

    if (!verified.ok) {
      throw new UnauthorizedException();
    }

    request.auth = verified.payload;
    return true;
  }
}

