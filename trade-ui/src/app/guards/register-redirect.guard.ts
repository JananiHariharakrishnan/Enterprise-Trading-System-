import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from '../services/auth-session.service';

export const registerRedirectGuard: CanActivateFn = async () => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (!authSession.isAuthenticated()) {
    return router.createUrlTree(['/']);
  }

  const user = await authSession.loadCurrentUser();
  if (!user) {
    return router.createUrlTree(['/']);
  }

  return true;
};