import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { PLATFORM_AUTH_MIDDLEWARE, createPlatformAuthMiddleware } from './services/platform-auth-middleware.provider';
import { TokenStorageService } from './services/token-storage.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(),
    provideRouter(routes),
    {
      provide: PLATFORM_AUTH_MIDDLEWARE,
      useFactory: createPlatformAuthMiddleware,
      deps: [TokenStorageService]
    }
  ]
};
