import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';

import { AuthSessionService } from '../services/auth-session.service';
import { loginRedirectGuard } from './login-redirect.guard';

describe('loginRedirectGuard', () => {
  let authSession: {
    isAuthenticated: ReturnType<typeof vi.fn>;
    loadCurrentUser: ReturnType<typeof vi.fn>;
  };

  let router: {
    createUrlTree: ReturnType<typeof vi.fn>;
  };

  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  beforeEach(() => {
    authSession = {
      isAuthenticated: vi.fn(),
      loadCurrentUser: vi.fn()
    };

    router = {
      createUrlTree: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthSessionService,
          useValue: authSession
        },
        {
          provide: Router,
          useValue: router
        }
      ]
    });
  });

  it('should allow navigation when the user is not authenticated', async () => {
    authSession.isAuthenticated.mockReturnValue(false);

    const result = await TestBed.runInInjectionContext(() =>
      loginRedirectGuard(route, state)
    );

    expect(result).toBe(true);
    expect(authSession.isAuthenticated).toHaveBeenCalled();
    expect(authSession.loadCurrentUser).not.toHaveBeenCalled();
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('should allow navigation when the user is authenticated but current user cannot be loaded', async () => {
    authSession.isAuthenticated.mockReturnValue(true);
    authSession.loadCurrentUser.mockResolvedValue(null);

    const result = await TestBed.runInInjectionContext(() =>
      loginRedirectGuard(route, state)
    );

    expect(result).toBe(true);
    expect(authSession.isAuthenticated).toHaveBeenCalled();
    expect(authSession.loadCurrentUser).toHaveBeenCalled();
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  it('should redirect authenticated users with a current user to the dashboard', async () => {
    const user = {
      id: 'user-1',
      username: 'testuser',
      accountId: 123
    };

    const urlTree = {};

    authSession.isAuthenticated.mockReturnValue(true);
    authSession.loadCurrentUser.mockResolvedValue(user);
    router.createUrlTree.mockReturnValue(urlTree);

    const result = await TestBed.runInInjectionContext(() =>
      loginRedirectGuard(route, state)
    );

    expect(result).toBe(urlTree);
    expect(authSession.isAuthenticated).toHaveBeenCalled();
    expect(authSession.loadCurrentUser).toHaveBeenCalled();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });
});