import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { routes } from '../app.routes';
import { AuthApiClientService } from './auth-api-client.service';
import { AuthSessionService } from './auth-session.service';
import { TokenStorageService } from './token-storage.service';
import { ResponseError } from '../generated/auth';

class AuthApiClientServiceStub {
  client = {
    authLoginPost: vi.fn(),
    authMeGet: vi.fn()
  };
}

describe('AuthSessionService', () => {
  let service: AuthSessionService;
  let apiStub: AuthApiClientServiceStub;
  let tokenStorage: TokenStorageService;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();

    await TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        AuthSessionService,
        TokenStorageService,
        { provide: AuthApiClientService, useClass: AuthApiClientServiceStub }
      ]
    }).compileComponents();

    service = TestBed.inject(AuthSessionService);
    apiStub = TestBed.inject(AuthApiClientService) as unknown as AuthApiClientServiceStub;
    tokenStorage = TestBed.inject(TokenStorageService);
    router = TestBed.inject(Router);
  });

  it('valid credentials sign in and persist token', async () => {
    apiStub.client.authLoginPost.mockResolvedValue({
      accessToken: 'token-123',
      refreshToken: 'refresh-123',
      tokenType: 'Bearer',
      expiresIn: 900
    });
    apiStub.client.authMeGet.mockResolvedValue({
      id: 'u-1',
      username: 'priya.menon',
      accountId: 1,
      roles: ['CUSTOMER'],
      createdAt: new Date()
    });

    await service.login('priya.menon', 'correct horse battery staple');

    expect(tokenStorage.getAccessToken()).toBe('token-123');
    expect(service.currentUser()?.username).toBe('priya.menon');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('invalid credentials show readable error', async () => {
    apiStub.client.authLoginPost.mockRejectedValue(
      new ResponseError(new Response('{}', { status: 401 }))
    );

    await expect(service.login('bad', 'bad')).rejects.toThrow('Invalid username or password.');
  });

  it('signing out clears the session', async () => {
    apiStub.client.authLoginPost.mockResolvedValue({
      accessToken: 'token-123',
      refreshToken: 'refresh-123',
      tokenType: 'Bearer',
      expiresIn: 900
    });
    apiStub.client.authMeGet.mockResolvedValue({
      id: 'u-1',
      username: 'priya.menon',
      accountId: 1,
      roles: ['CUSTOMER'],
      createdAt: new Date()
    });

    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await service.login('priya.menon', 'correct horse battery staple');
    await service.logout();

    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(tokenStorage.getRefreshToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
