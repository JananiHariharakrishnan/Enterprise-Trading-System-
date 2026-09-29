import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthSessionService } from '../services/auth-session.service';

class AuthSessionServiceStub {
  isAuthenticated = vi.fn(() => true);
  loadCurrentUser = vi.fn();
}

describe('authGuard', () => {
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

  it('redirects to login when token is missing', async () => {
    authSession.isAuthenticated.mockReturnValue(false);

    const result = await TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(result).toEqual(router.createUrlTree(['/login']));
  });

  it('redirects to login when profile verification fails', async () => {
    authSession.loadCurrentUser.mockResolvedValue(null);

    const result = await TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(result).toEqual(router.createUrlTree(['/login']));
  });

  it('allows dashboard route when session and profile are valid', async () => {
    authSession.loadCurrentUser.mockResolvedValue({ id: 'u-1' });

    const result = await TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(result).toBe(true);
  });
});
