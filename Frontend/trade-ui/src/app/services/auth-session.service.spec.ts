import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { routes } from '../app.routes';

import { AuthApiClientService } from './auth-api-client.service';
import { AuthSessionService } from './auth-session.service';
import { ThemeService } from './theme.service';
import { TokenStorageService } from './token-storage.service';
import { PasswordHashService } from './password-hash.service';
import { ResponseError } from '../generated/auth';
import { authMappingComplete, tradeMappingComplete } from '../errors/error-messages';

class AuthApiClientServiceStub {
  client = {
    authLoginPost: vi.fn(),
    authRegisterPost: vi.fn(),
    authMeGet: vi.fn()
  };
}

describe('AuthSessionService', () => {
  let service: AuthSessionService;
  let apiStub: AuthApiClientServiceStub;
  let tokenStorage: TokenStorageService;
  let themeService: ThemeService;
  let router: Router;

  const mockUser = {
    id: 'u-1',
    username: 'priya.menon',
    accountId: 1,
    roles: ['CUSTOMER'],
    createdAt: new Date()
  };

  const mockLoginResponse = {
    accessToken: 'token-123',
    refreshToken: 'refresh-123',
    tokenType: 'Bearer',
    expiresIn: 900
  };

  beforeEach(async () => {
    sessionStorage.clear();

    await TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        AuthSessionService,
        TokenStorageService,
        {
          provide: AuthApiClientService,
          useClass: AuthApiClientServiceStub
        }
      ]
    }).compileComponents();

    service = TestBed.inject(AuthSessionService);

    apiStub = TestBed.inject(
      AuthApiClientService
    ) as unknown as AuthApiClientServiceStub;

    tokenStorage = TestBed.inject(TokenStorageService);
    themeService = TestBed.inject(ThemeService);
    router = TestBed.inject(Router);
  });

  describe('initial state', () => {
    it('should start unauthenticated when no access token exists', () => {
      expect(service.isAuthenticated()).toBe(false);
      expect(service.currentUser()).toBeNull();
    });
  });

  describe('login', () => {
    it('should sign in with valid credentials and persist tokens', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet.mockResolvedValue(
        mockUser
      );

      await service.login(
        'priya.menon',
        'correct horse battery staple'
      );

      expect(
        apiStub.client.authLoginPost
      ).toHaveBeenCalledWith({
        loginRequest: {
          username: 'priya.menon',
          password: new PasswordHashService().encodePassword('correct horse battery staple')
        }
      });

      expect(
        tokenStorage.getAccessToken()
      ).toBe('token-123');

      expect(
        service.currentUser()?.username
      ).toBe('priya.menon');

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });

    it('should load the current user after successful login', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet.mockResolvedValue(
        mockUser
      );

      await service.login(
        'priya.menon',
        'password'
      );

      expect(
        apiStub.client.authMeGet
      ).toHaveBeenCalledTimes(1);

      expect(
        service.currentUser()
      ).toEqual(mockUser);
    });

    it('should remain authenticated when profile loading fails with a server error', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet.mockRejectedValue(
        new ResponseError(
          new Response('{}', {
            status: 500
          })
        )
      );

      await service.login(
        'priya.menon',
        'password'
      );

      expect(
        service.isAuthenticated()
      ).toBe(true);

      expect(
        service.currentUser()
      ).toBeNull();

      expect(
        tokenStorage.getAccessToken()
      ).toBe('token-123');
    });

    it('should show a readable error for invalid credentials', async () => {
      apiStub.client.authLoginPost.mockRejectedValue(
        new ResponseError(
          new Response('{}', {
            status: 401
          })
        )
      );

      await expect(
        service.login('bad', 'bad')
      ).rejects.toThrow(
        'Invalid username or password.'
      );

      expect(
        tokenStorage.getAccessToken()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(false);
    });

    it('should show a readable error when Auth service cannot be reached', async () => {
      apiStub.client.authLoginPost.mockRejectedValue(
        new TypeError('Failed to fetch')
      );

      await expect(
        service.login(
          'priya.menon',
          'password'
        )
      ).rejects.toThrow(
        /Cannot reach Auth service/
      );

      expect(
        tokenStorage.getAccessToken()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(false);
    });

    it('should show the server error message for a non-401 ResponseError', async () => {
      const response = new Response(
        JSON.stringify({
          message: 'Account is locked.'
        }),
        {
          status: 400
        }
      );

      apiStub.client.authLoginPost.mockRejectedValue(
        new ResponseError(response)
      );

      await expect(
        service.login(
          'priya.menon',
          'password'
        )
      ).rejects.toThrow(
        'Account is locked.'
      );
    });

    it('should use the generic error when the server response has no message', async () => {
      const response = new Response(
        JSON.stringify({}),
        {
          status: 400
        }
      );

      apiStub.client.authLoginPost.mockRejectedValue(
        new ResponseError(response)
      );

      await expect(
        service.login(
          'priya.menon',
          'password'
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });

    it('should use the generic error when the response body is not valid JSON', async () => {
      const response = new Response(
        'not valid json',
        {
          status: 400
        }
      );

      apiStub.client.authLoginPost.mockRejectedValue(
        new ResponseError(response)
      );

      await expect(
        service.login(
          'priya.menon',
          'password'
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });

    it('should use the generic error when an unknown error occurs', async () => {
      apiStub.client.authLoginPost.mockRejectedValue(
        new Error('Unexpected error')
      );

      await expect(
        service.login(
          'priya.menon',
          'password'
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });

    it('should show readable message when auth service cannot be reached', async () => {
      apiStub.client.authLoginPost.mockRejectedValue(
        new TypeError('Failed to fetch')
      );

      await expect(
        service.login('priya.menon', 'password')
      ).rejects.toThrow(/Cannot reach Auth service/);
    });
  });

  describe('register', () => {
    it('should register and return the assigned user account', async () => {
      apiStub.client.authRegisterPost.mockResolvedValue(
        mockUser
      );

      const registered = await service.register(
        'priya.menon',
        'password'
      );

      expect(
        apiStub.client.authRegisterPost
      ).toHaveBeenCalledWith({
        registerRequest: {
          username: 'priya.menon',
          password: new PasswordHashService().encodePassword('password')
        }
      });

      expect(apiStub.client.authLoginPost).not.toHaveBeenCalled();

      expect(registered).toEqual(mockUser);
    });

    it('should show a readable error when registration fails', async () => {
      apiStub.client.authRegisterPost.mockRejectedValue(
        new ResponseError(
          new Response(
            JSON.stringify({
              message: 'Username already exists.'
            }),
            {
              status: 409
            }
          )
        )
      );

      await expect(
        service.register(
          'priya.menon',
          'password'
        )
      ).rejects.toThrow(
        'Username already exists.'
      );

      expect(
        apiStub.client.authLoginPost
      ).not.toHaveBeenCalled();
    });

    it('should return a generic error when registration fails unexpectedly', async () => {
      apiStub.client.authRegisterPost.mockRejectedValue(new Error('Unexpected failure'));

      await expect(service.register('priya.menon', 'password')).rejects.toThrow('Unable to sign in right now. Please try again.');
    });

    it('error mapping should be complete for auth contract', () => {
      expect(authMappingComplete).toBe(true);
    });

    it('error mapping should be complete for trade contract', () => {
      expect(tradeMappingComplete).toBe(true);
    });
  });

  describe('loadCurrentUser', () => {
    it('should return null when the user is not authenticated', async () => {
      const result =
        await service.loadCurrentUser();

      expect(result).toBeNull();

      expect(
        service.currentUser()
      ).toBeNull();

      expect(
        apiStub.client.authMeGet
      ).not.toHaveBeenCalled();
    });

    it('should load the current user after successful login', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet.mockResolvedValue(
        mockUser
      );

      await service.login(
        'priya.menon',
        'password'
      );

      const result =
        await service.loadCurrentUser();

      expect(result).toEqual(mockUser);

      expect(
        service.currentUser()
      ).toEqual(mockUser);

      expect(
        apiStub.client.authMeGet
      ).toHaveBeenCalledTimes(1);
    });

    it('should return the cached user without calling the API again', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet.mockResolvedValue(
        mockUser
      );

      await service.login(
        'priya.menon',
        'password'
      );

      const firstResult =
        await service.loadCurrentUser();

      const secondResult =
        await service.loadCurrentUser();

      expect(firstResult).toEqual(mockUser);
      expect(secondResult).toEqual(mockUser);

      expect(
        apiStub.client.authMeGet
      ).toHaveBeenCalledTimes(1);
    });

    it('should share the same promise for concurrent requests', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      let resolveRequest!: (
        value: typeof mockUser
      ) => void;

      apiStub.client.authMeGet.mockReturnValue(
        new Promise<typeof mockUser>((resolve) => {
          resolveRequest = resolve;
        })
      );

      const loginPromise = service.login(
        'priya.menon',
        'password'
      );

      await Promise.resolve();

      expect(
        apiStub.client.authMeGet
      ).toHaveBeenCalledTimes(1);

      const firstRequest =
        service.loadCurrentUser();

      const secondRequest =
        service.loadCurrentUser();

      expect(
        apiStub.client.authMeGet
      ).toHaveBeenCalledTimes(1);

      resolveRequest(mockUser);

      await loginPromise;

      const [
        firstResult,
        secondResult
      ] = await Promise.all([
        firstRequest,
        secondRequest
      ]);

      expect(firstResult).toEqual(mockUser);
      expect(secondResult).toEqual(mockUser);
    });

    it('should clear the session when profile loading returns 401', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet
        .mockResolvedValueOnce(mockUser)
        .mockRejectedValueOnce(
          new ResponseError(
            new Response('{}', {
              status: 401
            })
          )
        );

      await service.login(
        'priya.menon',
        'password'
      );

      service.clearCurrentUserCache();

      const result =
        await service.loadCurrentUser();

      expect(result).toBeNull();

      expect(
        tokenStorage.getAccessToken()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(false);

      expect(
        service.currentUser()
      ).toBeNull();
    });

    it('should clear the session when profile loading returns 403', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet
        .mockResolvedValueOnce(mockUser)
        .mockRejectedValueOnce(
          new ResponseError(
            new Response('{}', {
              status: 403
            })
          )
        );

      await service.login(
        'priya.menon',
        'password'
      );

      service.clearCurrentUserCache();

      await service.loadCurrentUser();

      expect(
        tokenStorage.getAccessToken()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(false);
    });

    it('should keep the session for retryable server errors', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet
        .mockResolvedValueOnce(mockUser)
        .mockRejectedValueOnce(
          new ResponseError(
            new Response('{}', {
              status: 500
            })
          )
        );

      await service.login(
        'priya.menon',
        'password'
      );

      service.clearCurrentUserCache();

      const result =
        await service.loadCurrentUser();

      expect(result).toBeNull();

      expect(
        tokenStorage.getAccessToken()
      ).toBe('token-123');

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });

    it('should apply SYSTEM theme when preferences are unavailable', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(mockLoginResponse);
      apiStub.client.authMeGet.mockResolvedValue(mockUser);
      const themeSpy = vi.spyOn(themeService, 'applyThemePreference');

      await service.login('priya.menon', 'password');

      expect(themeSpy).toHaveBeenCalledWith('SYSTEM');
    });

    it('should keep the session for non-ResponseError failures', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet
        .mockResolvedValueOnce(mockUser)
        .mockRejectedValueOnce(
          new Error('Unexpected failure')
        );

      await service.login(
        'priya.menon',
        'password'
      );

      service.clearCurrentUserCache();

      await service.loadCurrentUser();

      expect(
        tokenStorage.getAccessToken()
      ).toBe('token-123');

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });

    it('should not clear the session when clearTokenOnFailure is false', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet
        .mockResolvedValueOnce(mockUser)
        .mockRejectedValueOnce(
          new ResponseError(
            new Response('{}', {
              status: 401
            })
          )
        );

      await service.login(
        'priya.menon',
        'password'
      );

      service.clearCurrentUserCache();

      await service.loadCurrentUser(false);

      expect(
        tokenStorage.getAccessToken()
      ).toBe('token-123');

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });

    it('should allow a new request after a failed profile request', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet
        .mockResolvedValueOnce(mockUser)
        .mockRejectedValueOnce(
          new ResponseError(
            new Response('{}', {
              status: 500
            })
          )
        )
        .mockResolvedValueOnce(mockUser);

      await service.login(
        'priya.menon',
        'password'
      );

      service.clearCurrentUserCache();

      const firstResult =
        await service.loadCurrentUser();

      service.clearCurrentUserCache();

      const secondResult =
        await service.loadCurrentUser();

      expect(firstResult).toBeNull();
      expect(secondResult).toEqual(mockUser);

      expect(
        apiStub.client.authMeGet
      ).toHaveBeenCalledTimes(3);
    });
  });

  describe('logout', () => {
    it('should clear the session and navigate to login', async () => {
      const navigateSpy = vi
        .spyOn(router, 'navigate')
        .mockResolvedValue(true);
      const themeSpy = vi.spyOn(themeService, 'applyThemePreference');

      await service.logout();

      expect(
        tokenStorage.getAccessToken()
      ).toBeNull();

      expect(
        tokenStorage.getRefreshToken()
      ).toBeNull();

      expect(
        service.currentUser()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(false);

      expect(
        navigateSpy
      ).toHaveBeenCalledWith(['/login']);

      expect(
        themeSpy
      ).toHaveBeenCalledWith('SYSTEM');
    });
  });

  describe('clearCurrentUserCache', () => {
    it('should clear the cached current user', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(
        mockLoginResponse
      );

      apiStub.client.authMeGet.mockResolvedValue(
        mockUser
      );

      await service.login(
        'priya.menon',
        'password'
      );

      expect(
        service.currentUser()
      ).toEqual(mockUser);

      service.clearCurrentUserCache();

      expect(
        service.currentUser()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });
  });

  describe('getAccessToken', () => {
    it('should return the access token from storage', () => {
      tokenStorage.setTokens(
        'my-access-token',
        'my-refresh-token'
      );

      expect(
        service.getAccessToken()
      ).toBe('my-access-token');
    });

    it('should return null when there is no access token', () => {
      expect(
        service.getAccessToken()
      ).toBeNull();
    });
  });

  describe('session countdown timer', () => {
    afterEach(() => {
      service.stopSessionTimer();
      vi.useRealTimers();
    });

    it('should start countdown on successful login with default duration (15 min)', async () => {
      apiStub.client.authLoginPost.mockResolvedValue(mockLoginResponse);
      apiStub.client.authMeGet.mockResolvedValue(mockUser);

      await service.login('priya.menon', 'password');

      expect(service.sessionRemainingSeconds()).toBeGreaterThanOrEqual(899);
      expect(service.sessionRemainingSeconds()).toBeLessThanOrEqual(900);
      expect(service.sessionExpiresIn()).toBe('15:00');
      expect(service.isSessionExpiringSoon()).toBe(false);
    });

    it('should format remaining seconds correctly as MM:SS', () => {
      service.sessionRemainingSeconds.set(900);
      expect(service.sessionExpiresIn()).toBe('15:00');

      service.sessionRemainingSeconds.set(65);
      expect(service.sessionExpiresIn()).toBe('01:05');

      service.sessionRemainingSeconds.set(9);
      expect(service.sessionExpiresIn()).toBe('00:09');

      service.sessionRemainingSeconds.set(0);
      expect(service.sessionExpiresIn()).toBe('00:00');
    });

    it('should flag isSessionExpiringSoon when <= 60 seconds remain', () => {
      service.sessionRemainingSeconds.set(61);
      expect(service.isSessionExpiringSoon()).toBe(false);

      service.sessionRemainingSeconds.set(60);
      expect(service.isSessionExpiringSoon()).toBe(true);

      service.sessionRemainingSeconds.set(10);
      expect(service.isSessionExpiringSoon()).toBe(true);

      service.sessionRemainingSeconds.set(0);
      expect(service.isSessionExpiringSoon()).toBe(false);
    });

    it('should redirect to login with returnUrl and clear session when session expires', async () => {
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      // Simulate viewing the orders page
      Object.defineProperty(router, 'url', { value: '/orders', configurable: true });

      tokenStorage.setTokens('token-123', 'refresh-123');
      expect(service.getAccessToken()).toBe('token-123');

      await service.handleSessionExpiry();

      expect(service.isAuthenticated()).toBe(false);
      expect(service.currentUser()).toBeNull();
      expect(tokenStorage.getAccessToken()).toBeNull();
      expect(service.sessionRemainingSeconds()).toBe(0);
      expect(navigateSpy).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/orders' } });
    });

    it('should not pass login or register as returnUrl on expiry', async () => {
      const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      Object.defineProperty(router, 'url', { value: '/login', configurable: true });

      tokenStorage.setTokens('token-123', 'refresh-123');

      await service.handleSessionExpiry();

      expect(navigateSpy).toHaveBeenCalledWith(['/login'], undefined);
    });

    it('should stop timer and reset remaining seconds on logout', async () => {
      vi.spyOn(router, 'navigate').mockResolvedValue(true);
      service.sessionRemainingSeconds.set(500);

      await service.logout();

      expect(service.sessionRemainingSeconds()).toBe(0);
      expect(tokenStorage.getAccessToken()).toBeNull();
    });

    it('should resume timer from stored expiresAt on refresh', () => {
      // 5 minutes left (300 seconds)
      const expiresAt = Date.now() + 300 * 1000;
      tokenStorage.setTokens('token-123', 'refresh-123');
      tokenStorage.setExpiresAt(expiresAt);

      service.startSessionTimer();

      expect(service.sessionRemainingSeconds()).toBeGreaterThanOrEqual(299);
      expect(service.sessionRemainingSeconds()).toBeLessThanOrEqual(300);
      expect(service.sessionExpiresIn()).toBe('05:00');
    });
  });
});
