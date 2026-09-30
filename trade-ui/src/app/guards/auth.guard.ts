import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthSessionService } from '../services/auth-session.service';

export const authGuard: CanActivateFn = async (_route, state) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  // If not authenticated, redirect to login carrying where the user was going.
  if (!authSession.isAuthenticated()) {
    const returnUrl = state?.url && state.url.startsWith('/') ? state.url : undefined;
    return router.createUrlTree(['/login'], returnUrl ? { queryParams: { returnUrl } } : undefined as any);
  }

  const user = await authSession.loadCurrentUser();
  if (!user) {
    const returnUrl = state?.url && state.url.startsWith('/') ? state.url : undefined;
    return router.createUrlTree(['/login'], returnUrl ? { queryParams: { returnUrl } } : undefined as any);
  }

  return true;
};
