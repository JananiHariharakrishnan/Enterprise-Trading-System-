import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';

const INTERNAL_SERVICE_TOKEN = process.env.INTERNAL_SERVICE_TOKEN ?? 'dev-internal-token';

@Injectable()
export class ServiceAuthGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const auth = request.headers.authorization ?? '';

    if (!auth.toLowerCase().startsWith('bearer ')) {
      throw new UnauthorizedException('Unauthorised internal caller');
    }

    const token = auth.slice(7).trim();
    if (!token || token !== INTERNAL_SERVICE_TOKEN) {
      throw new UnauthorizedException('Unauthorised internal caller');
    }

    return true;
  }
}
