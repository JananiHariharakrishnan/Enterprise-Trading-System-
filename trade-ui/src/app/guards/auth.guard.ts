import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthSessionService } from '../services/auth-session.service';

export const authGuard: CanActivateFn = async () => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (!authSession.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  const user = await authSession.loadCurrentUser();
  if (!user) {
    return router.createUrlTree(['/login']);
  }

  return true;
};
