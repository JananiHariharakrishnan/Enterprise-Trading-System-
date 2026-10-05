import { Inject, Injectable } from '@angular/core';
import { Configuration, DefaultApi } from '../generated/auth';
import { PLATFORM_AUTH_MIDDLEWARE } from './platform-auth-middleware.provider';
import { Middleware } from '../generated/auth/runtime';

@Injectable({
  providedIn: 'root'
})
export class AuthApiClientService {
  readonly client: DefaultApi;

  constructor(@Inject(PLATFORM_AUTH_MIDDLEWARE) platformAuthMiddleware: Middleware) {
    const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;

    this.client = new DefaultApi(
      new Configuration({
        basePath: `http://${baseHost}:3000`,
        middleware: [platformAuthMiddleware]
      })
    );
  }
}
