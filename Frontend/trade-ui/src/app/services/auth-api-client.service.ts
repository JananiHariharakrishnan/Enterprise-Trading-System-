import { Inject, Injectable } from '@angular/core';
import { Configuration, DefaultApi } from '../generated/auth';
import { PLATFORM_AUTH_MIDDLEWARE } from './platform-auth-middleware.provider';
import { Middleware } from '../generated/auth/runtime';

import { getAuthApiUrl } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class AuthApiClientService {
  readonly client: DefaultApi;

  constructor(@Inject(PLATFORM_AUTH_MIDDLEWARE) platformAuthMiddleware: Middleware) {
    this.client = new DefaultApi(
      new Configuration({
        basePath: getAuthApiUrl(),
        middleware: [platformAuthMiddleware]
      })
    );
  }
}
