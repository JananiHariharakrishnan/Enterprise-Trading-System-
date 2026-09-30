import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { AuthSessionService } from '../services/auth-session.service';
import { registerRedirectGuard } from './register-redirect.guard';

describe('registerRedirectGuard', () => {
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

  it('redirects unauthenticated users to home', async () => {
    const urlTree = {};
    authSession.isAuthenticated.mockReturnValue(false);
    router.createUrlTree.mockReturnValue(urlTree);

    const result = await TestBed.runInInjectionContext(() =>
      registerRedirectGuard(route, state)
    );

    expect(result).toBe(urlTree);
    expect(authSession.loadCurrentUser).not.toHaveBeenCalled();
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });

  it('redirects to home when the session exists but the user cannot be loaded', async () => {
    const urlTree = {};
    authSession.isAuthenticated.mockReturnValue(true);
    authSession.loadCurrentUser.mockResolvedValue(null);
    router.createUrlTree.mockReturnValue(urlTree);

    const result = await TestBed.runInInjectionContext(() =>
      registerRedirectGuard(route, state)
    );

    expect(result).toBe(urlTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
  });

  it('allows navigation when the authenticated user is present', async () => {
    authSession.isAuthenticated.mockReturnValue(true);
    authSession.loadCurrentUser.mockResolvedValue({ id: 'user-1' });

    const result = await TestBed.runInInjectionContext(() =>
      registerRedirectGuard(route, state)
    );

    expect(result).toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });
});