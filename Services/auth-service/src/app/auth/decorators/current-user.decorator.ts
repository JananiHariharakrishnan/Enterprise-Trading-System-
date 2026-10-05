import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import type { AuthPayload } from '../interfaces/auth-payload.interface.js';
import type { AuthenticatedRequest } from '../guards/jwt-auth.guard.js';

export const CurrentUser = createParamDecorator((_: unknown, context: ExecutionContext): AuthPayload => {
  const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
  return request.auth as AuthPayload;
});


