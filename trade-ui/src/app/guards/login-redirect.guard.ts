import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthSessionService } from '../services/auth-session.service';

export const loginRedirectGuard: CanActivateFn = async () => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (!authSession.isAuthenticated()) {
    return true;
  }

  const user = await authSession.loadCurrentUser();
  if (!user) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
