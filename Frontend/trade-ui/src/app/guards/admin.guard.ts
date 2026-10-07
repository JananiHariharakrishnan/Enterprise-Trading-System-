import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthSessionService } from '../services/auth-session.service';
import { getSafeReturnUrl } from './return-url.util';

export const adminGuard: CanActivateFn = async (_route, state) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (!authSession.isAuthenticated()) {
    const returnUrl = getSafeReturnUrl(state?.url);
    return router.createUrlTree(['/login'], returnUrl ? { queryParams: { returnUrl } } : (undefined as any));
  }

  const user = await authSession.loadCurrentUser();
  if (!user) {
    const returnUrl = getSafeReturnUrl(state?.url);
    return router.createUrlTree(['/login'], returnUrl ? { queryParams: { returnUrl } } : (undefined as any));
  }

  if (!user.roles?.includes('ADMIN')) {
    return router.createUrlTree(['/dashboard']);
  }

  return true;
};
