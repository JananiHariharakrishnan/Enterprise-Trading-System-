import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthSessionService } from '../services/auth-session.service';

class AuthSessionServiceStub {
  isAuthenticated = vi.fn(() => true);
  loadCurrentUser = vi.fn();
}

describe('adminGuard', () => {
  let router: Router;
  let authSession: AuthSessionServiceStub;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useClass: AuthSessionServiceStub }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
  });

  it('redirects to login when unauthenticated', async () => {
    authSession.isAuthenticated.mockReturnValue(false);

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, { url: '/admin' } as any));

    expect(result).toEqual(router.createUrlTree(['/login'], { queryParams: { returnUrl: '/admin' } }));
  });

  it('redirects to login when user profile fails to load', async () => {
    authSession.loadCurrentUser.mockResolvedValue(null);

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, { url: '/admin' } as any));

    expect(result).toEqual(router.createUrlTree(['/login'], { queryParams: { returnUrl: '/admin' } }));
  });

  it('redirects non-admin user to dashboard', async () => {
    authSession.loadCurrentUser.mockResolvedValue({ id: 'u-1', roles: ['CUSTOMER'] });

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, { url: '/admin' } as any));

    expect(result).toEqual(router.createUrlTree(['/dashboard']));
  });

  it('allows access when user has ADMIN role', async () => {
    authSession.loadCurrentUser.mockResolvedValue({ id: 'u-admin', roles: ['ADMIN'] });

    const result = await TestBed.runInInjectionContext(() => adminGuard({} as any, { url: '/admin' } as any));

    expect(result).toBe(true);
  });
});
