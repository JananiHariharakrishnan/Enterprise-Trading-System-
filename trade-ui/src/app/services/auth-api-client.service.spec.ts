import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import {
  ResponseError,
  UserResponse
} from '../generated/auth';

import { AuthApiClientService } from './auth-api-client.service';
import { AuthSessionService } from './auth-session.service';
import { TokenStorageService } from './token-storage.service';

describe('AuthSessionService', () => {
  let service: AuthSessionService;

  let authApi: {
    client: {
      authLoginPost: ReturnType<typeof vi.fn>;
      authRegisterPost: ReturnType<typeof vi.fn>;
      authMeGet: ReturnType<typeof vi.fn>;
    };
  };

  let tokenStorage: {
    getAccessToken: ReturnType<typeof vi.fn>;
    setTokens: ReturnType<typeof vi.fn>;
    clear: ReturnType<typeof vi.fn>;
  };

  let router: {
    navigate: ReturnType<typeof vi.fn>;
  };

  const user: UserResponse = {
    id: 'user-1',
    username: 'testuser',
    accountId: 123
  } as UserResponse;

  beforeEach(() => {
    authApi = {
      client: {
        authLoginPost: vi.fn(),
        authRegisterPost: vi.fn(),
        authMeGet: vi.fn()
      }
    };

    tokenStorage = {
      getAccessToken: vi.fn().mockReturnValue(null),
      setTokens: vi.fn(),
      clear: vi.fn()
    };

    router = {
      navigate: vi.fn().mockResolvedValue(true)
    };

    TestBed.configureTestingModule({
      providers: [
        AuthSessionService,
        {
          provide: AuthApiClientService,
          useValue: authApi
        },
        {
          provide: TokenStorageService,
          useValue: tokenStorage
        },
        {
          provide: Router,
          useValue: router
        }
      ]
    });

    service = TestBed.inject(AuthSessionService);
  });

  describe('initial state', () => {
    it('should start unauthenticated when there is no access token', () => {
      expect(service.isAuthenticated()).toBe(false);
      expect(service.currentUser()).toBeNull();
    });

    it('should start authenticated when an access token exists', () => {
      TestBed.resetTestingModule();

      tokenStorage = {
        getAccessToken: vi.fn().mockReturnValue(
          'existing-token'
        ),
        setTokens: vi.fn(),
        clear: vi.fn()
      };

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      const authenticatedService =
        TestBed.inject(AuthSessionService);

      expect(
        authenticatedService.isAuthenticated()
      ).toBe(true);
    });
  });

  describe('login', () => {
    it('should login, store tokens and load the current user', async () => {
      authApi.client.authLoginPost.mockResolvedValue({
        accessToken: 'access-token',
        refreshToken: 'refresh-token'
      });

      authApi.client.authMeGet.mockResolvedValue(user);

      await service.login(
        'testuser',
        'password'
      );

      expect(
        authApi.client.authLoginPost
      ).toHaveBeenCalledWith({
        loginRequest: {
          username: 'testuser',
          password: 'password'
        }
      });

      expect(
        tokenStorage.setTokens
      ).toHaveBeenCalledWith(
        'access-token',
        'refresh-token'
      );

      expect(
        service.isAuthenticated()
      ).toBe(true);

      expect(
        service.currentUser()
      ).toEqual(user);
    });

    it('should keep the session authenticated when profile loading fails', async () => {
      authApi.client.authLoginPost.mockResolvedValue({
        accessToken: 'access-token',
        refreshToken: 'refresh-token'
      });

      authApi.client.authMeGet.mockRejectedValue(
        new ResponseError(
          new Response(
            JSON.stringify({
              message: 'Server error'
            }),
            {
              status: 500
            }
          )
        )
      );

      await service.login(
        'testuser',
        'password'
      );

      expect(
        service.isAuthenticated()
      ).toBe(true);

      expect(
        tokenStorage.clear
      ).not.toHaveBeenCalled();

      expect(
        service.currentUser()
      ).toBeNull();
    });

    it('should throw a readable error when login fails with invalid credentials', async () => {
      authApi.client.authLoginPost.mockRejectedValue(
        new ResponseError(
          new Response(
            JSON.stringify({
              message: 'Unauthorized'
            }),
            {
              status: 401
            }
          )
        )
      );

      await expect(
        service.login(
          'testuser',
          'wrong-password'
        )
      ).rejects.toThrow(
        'Invalid username or password.'
      );

      expect(
        tokenStorage.setTokens
      ).not.toHaveBeenCalled();

      expect(
        service.isAuthenticated()
      ).toBe(false);
    });

    it('should return a connection error when Auth service cannot be reached', async () => {
      authApi.client.authLoginPost.mockRejectedValue(
        new TypeError('Failed to fetch')
      );

      await expect(
        service.login(
          'testuser',
          'password'
        )
      ).rejects.toThrow(
        /Cannot reach Auth service/
      );

      expect(
        tokenStorage.setTokens
      ).not.toHaveBeenCalled();
    });

    it('should return the generic error when an unknown login error occurs', async () => {
      authApi.client.authLoginPost.mockRejectedValue(
        new Error('Unexpected error')
      );

      await expect(
        service.login(
          'testuser',
          'password'
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });

    it('should use the server message when a non-401 ResponseError contains one', async () => {
      authApi.client.authLoginPost.mockRejectedValue(
        new ResponseError(
          new Response(
            JSON.stringify({
              message: 'Account is locked.'
            }),
            {
              status: 400
            }
          )
        )
      );

      await expect(
        service.login(
          'testuser',
          'password'
        )
      ).rejects.toThrow(
        'Account is locked.'
      );
    });

    it('should use the generic error when the response body is invalid', async () => {
      authApi.client.authLoginPost.mockRejectedValue(
        new ResponseError(
          new Response(
            'not-json',
            {
              status: 400
            }
          )
        )
      );

      await expect(
        service.login(
          'testuser',
          'password'
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });

    it('should use the generic error when the response message is empty', async () => {
      authApi.client.authLoginPost.mockRejectedValue(
        new ResponseError(
          new Response(
            JSON.stringify({
              message: '   '
            }),
            {
              status: 400
            }
          )
        )
      );

      await expect(
        service.login(
          'testuser',
          'password'
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });
  });

  describe('register', () => {
    it('should register and automatically login', async () => {
      authApi.client.authRegisterPost.mockResolvedValue(
        undefined
      );

      authApi.client.authLoginPost.mockResolvedValue({
        accessToken: 'access-token',
        refreshToken: 'refresh-token'
      });

      authApi.client.authMeGet.mockResolvedValue(user);

      await service.register(
        'testuser',
        'password',
        123
      );

      expect(
        authApi.client.authRegisterPost
      ).toHaveBeenCalledWith({
        registerRequest: {
          username: 'testuser',
          password: 'password',
          accountId: 123
        }
      });

      expect(
        authApi.client.authLoginPost
      ).toHaveBeenCalledWith({
        loginRequest: {
          username: 'testuser',
          password: 'password'
        }
      });

      expect(
        service.isAuthenticated()
      ).toBe(true);

      expect(
        service.currentUser()
      ).toEqual(user);
    });

    it('should throw a readable error when registration fails', async () => {
      authApi.client.authRegisterPost.mockRejectedValue(
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
          'testuser',
          'password',
          123
        )
      ).rejects.toThrow(
        'Username already exists.'
      );

      expect(
        authApi.client.authLoginPost
      ).not.toHaveBeenCalled();
    });

    it('should return the generic error when automatic login fails', async () => {
      authApi.client.authRegisterPost.mockResolvedValue(
        undefined
      );

      authApi.client.authLoginPost.mockRejectedValue(
        new ResponseError(
          new Response(
            JSON.stringify({
              message: 'Login failed.'
            }),
            {
              status: 400
            }
          )
        )
      );

      await expect(
        service.register(
          'testuser',
          'password',
          123
        )
      ).rejects.toThrow(
        'Unable to sign in right now. Please try again.'
      );
    });
  });

  describe('loadCurrentUser', () => {
    it('should return null when the user is not authenticated', async () => {
      const result =
        await service.loadCurrentUser();

      expect(result).toBeNull();

      expect(
        authApi.client.authMeGet
      ).not.toHaveBeenCalled();

      expect(
        service.currentUser()
      ).toBeNull();
    });

    it('should load and cache the current user', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockResolvedValue(
        user
      );

      const result =
        await service.loadCurrentUser();

      expect(result).toEqual(user);

      expect(
        service.currentUser()
      ).toEqual(user);

      expect(
        authApi.client.authMeGet
      ).toHaveBeenCalledTimes(1);
    });

    it('should return the cached user without calling the API again', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockResolvedValue(
        user
      );

      await service.loadCurrentUser();

      const result =
        await service.loadCurrentUser();

      expect(result).toEqual(user);

      expect(
        authApi.client.authMeGet
      ).toHaveBeenCalledTimes(1);
    });

    it('should share the same promise for concurrent requests', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      let resolveRequest!: (
        value: UserResponse
      ) => void;

      authApi.client.authMeGet.mockReturnValue(
        new Promise<UserResponse>(
          (resolve) => {
            resolveRequest = resolve;
          }
        )
      );

      const firstRequest =
        service.loadCurrentUser();

      const secondRequest =
        service.loadCurrentUser();

      expect(
        authApi.client.authMeGet
      ).toHaveBeenCalledTimes(1);

      resolveRequest(user);

      const [
        first,
        second
      ] = await Promise.all([
        firstRequest,
        secondRequest
      ]);

      expect(first).toEqual(user);
      expect(second).toEqual(user);
    });

    it('should clear the session on 401 profile failure by default', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockRejectedValue(
        new ResponseError(
          new Response(null, {
            status: 401
          })
        )
      );

      const result =
        await service.loadCurrentUser();

      expect(result).toBeNull();

      expect(
        tokenStorage.clear
      ).toHaveBeenCalled();

      expect(
        service.isAuthenticated()
      ).toBe(false);

      expect(
        service.currentUser()
      ).toBeNull();
    });

    it('should clear the session on 403 profile failure by default', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockRejectedValue(
        new ResponseError(
          new Response(null, {
            status: 403
          })
        )
      );

      await service.loadCurrentUser();

      expect(
        tokenStorage.clear
      ).toHaveBeenCalled();

      expect(
        service.isAuthenticated()
      ).toBe(false);
    });

    it('should keep the session for retryable profile failures', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockRejectedValue(
        new ResponseError(
          new Response(null, {
            status: 500
          })
        )
      );

      await service.loadCurrentUser();

      expect(
        tokenStorage.clear
      ).not.toHaveBeenCalled();

      expect(
        service.isAuthenticated()
      ).toBe(true);

      expect(
        service.currentUser()
      ).toBeNull();
    });

    it('should keep the session when a non-ResponseError occurs', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockRejectedValue(
        new Error('Unexpected failure')
      );

      await service.loadCurrentUser();

      expect(
        tokenStorage.clear
      ).not.toHaveBeenCalled();

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });

    it('should not clear the token when clearTokenOnFailure is false', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockRejectedValue(
        new ResponseError(
          new Response(null, {
            status: 401
          })
        )
      );

      await service.loadCurrentUser(false);

      expect(
        tokenStorage.clear
      ).not.toHaveBeenCalled();

      expect(
        service.isAuthenticated()
      ).toBe(true);
    });

    it('should reset the in-flight promise after a failed request', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet
        .mockRejectedValueOnce(
          new ResponseError(
            new Response(null, {
              status: 500
            })
          )
        )
        .mockResolvedValueOnce(user);

      await service.loadCurrentUser();

      const result =
        await service.loadCurrentUser();

      expect(result).toEqual(user);

      expect(
        authApi.client.authMeGet
      ).toHaveBeenCalledTimes(2);
    });
  });

  describe('logout', () => {
    it('should clear the session and navigate to login', async () => {
      await service.logout();

      expect(
        tokenStorage.clear
      ).toHaveBeenCalled();

      expect(
        service.currentUser()
      ).toBeNull();

      expect(
        service.isAuthenticated()
      ).toBe(false);

      expect(
        router.navigate
      ).toHaveBeenCalledWith([
        '/login'
      ]);
    });
  });

  describe('clearCurrentUserCache', () => {
    it('should clear the cached current user', async () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'token'
      );

      TestBed.resetTestingModule();

      TestBed.configureTestingModule({
        providers: [
          AuthSessionService,
          {
            provide: AuthApiClientService,
            useValue: authApi
          },
          {
            provide: TokenStorageService,
            useValue: tokenStorage
          },
          {
            provide: Router,
            useValue: router
          }
        ]
      });

      service =
        TestBed.inject(AuthSessionService);

      authApi.client.authMeGet.mockResolvedValue(
        user
      );

      await service.loadCurrentUser();

      expect(
        service.currentUser()
      ).toEqual(user);

      service.clearCurrentUserCache();

      expect(
        service.currentUser()
      ).toBeNull();
    });
  });

  describe('getAccessToken', () => {
    it('should return the access token from storage', () => {
      tokenStorage.getAccessToken.mockReturnValue(
        'my-token'
      );

      expect(
        service.getAccessToken()
      ).toBe('my-token');

      expect(
        tokenStorage.getAccessToken
      ).toHaveBeenCalled();
    });

    it('should return null when there is no access token', () => {
      tokenStorage.getAccessToken.mockReturnValue(
        null
      );

      expect(
        service.getAccessToken()
      ).toBeNull();
    });
  });
});