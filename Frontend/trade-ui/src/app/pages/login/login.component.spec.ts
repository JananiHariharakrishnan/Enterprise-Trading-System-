import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthSessionService } from '../../services/auth-session.service';
import { LoginComponent } from './login.component';
// Removed raw template/style imports; rely on TestBed to compile component resources.

class AuthSessionServiceStub {
  login = vi.fn();
  isAdmin = vi.fn().mockReturnValue(false);
}

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSession: AuthSessionServiceStub;
  let router: Router;
  let activatedRoute: { snapshot: { queryParamMap: { get: ReturnType<typeof vi.fn> } } };

  beforeEach(async () => {
    activatedRoute = {
      snapshot: {
        queryParamMap: {
          get: vi.fn().mockReturnValue(null)
        }
      }
    };

    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter(routes),
        { provide: AuthSessionService, useClass: AuthSessionServiceStub },
        { provide: ActivatedRoute, useValue: activatedRoute }
      ]
    }).compileComponents();

    localStorage.clear();
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
    fixture.destroy();
  });

  it('empty fields are blocked by validation', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    // Touch the fields to trigger validation display
    component.form.get('username')?.markAsTouched();
    component.form.get('password')?.markAsTouched();
    
    fixture.detectChanges();

    // Try to submit with empty fields (should not actually call login)
    await component.onSubmit();

    expect(authSession.login).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();

    // Check that validation errors are displayed
    const usernameError = fixture.nativeElement.querySelector('[data-testid="auth-error-username"]') as HTMLElement;
    const passwordError = fixture.nativeElement.querySelector('[data-testid="auth-error-password"]') as HTMLElement;
    
    expect(usernameError?.textContent).toContain('Username is required.');
    expect(passwordError?.textContent).toContain('Password is required.');
  });

  it('valid credentials sign in and redirect', async () => {
    authSession.login.mockResolvedValue(undefined);

    component.form.setValue({
      username: 'priya.menon',
      password: 'correct horse battery staple'
    });

    activatedRoute.snapshot.queryParamMap.get.mockImplementation((key: string) =>
      key === 'returnUrl' ? '/instruments' : null
    );

    const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    await component.onSubmit();

    expect(authSession.login).toHaveBeenCalledWith('priya.menon', 'correct horse battery staple');
    expect(navigateByUrlSpy).toHaveBeenCalledWith('/instruments');
  });

  it('off-origin returnUrl is refused and navigates to dashboard', async () => {
    authSession.login.mockResolvedValue(undefined);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.setValue({
      username: 'priya.menon',
      password: 'correct horse battery staple'
    });

    activatedRoute.snapshot.queryParamMap.get.mockImplementation((key: string) =>
      key === 'returnUrl' ? 'https://evil.example.com/phish' : null
    );

    await component.onSubmit();

    expect(authSession.login).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  });

  it('protocol-relative returnUrl is refused and navigates to dashboard', async () => {
    authSession.login.mockResolvedValue(undefined);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.setValue({
      username: 'priya.menon',
      password: 'correct horse battery staple'
    });

    activatedRoute.snapshot.queryParamMap.get.mockImplementation((key: string) =>
      key === 'returnUrl' ? '//evil.example.com/phish' : null
    );

    await component.onSubmit();

    expect(authSession.login).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  });

  it('invalid credentials show a readable error', async () => {
    authSession.login.mockRejectedValue(new Error('Invalid username or password.'));

    component.form.setValue({
      username: 'invalid',
      password: 'invalid'
    });

    await component.onSubmit();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="auth-error-invalid-credentials"]') as HTMLElement;
    expect(error?.textContent).toContain('Wrong username or password!');
  });

  it('locks after three wrong passwords and unlocks when the timer ends', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    authSession.login.mockRejectedValue(new Error('Invalid password'));
    component.form.setValue({ username: 'ada', password: 'wrong-password' });

    await component.onSubmit();
    await component.onSubmit();
    expect(component.isLocked()).toBe(false);

    await component.onSubmit();
    fixture.detectChanges();
    expect(component.isLocked()).toBe(true);
    expect(component.progressPercent()).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('[data-testid="lockout-dialog"]')).toBeTruthy();
    expect(component.form.disabled).toBe(true);

    const attempts = authSession.login.mock.calls.length;
    await component.onSubmit();
    expect(authSession.login).toHaveBeenCalledTimes(attempts);

    vi.advanceTimersByTime(10_000);
    expect(component.remainingSeconds()).toBe(0);
    expect(component.form.enabled).toBe(true);
    vi.useRealTimers();
  });

  it('does not lock when the account does not exist or the service is down', async () => {
    component.form.setValue({ username: 'missing', password: 'password123456' });
    authSession.login.mockRejectedValueOnce(new Error('User not found'));
    await component.onSubmit();
    expect(component.submitError()).toContain('User not found');
    expect(component.isLocked()).toBe(false);

    authSession.login.mockRejectedValueOnce(new Error('Service unavailable'));
    await component.onSubmit();
    expect(component.submitError()).toContain('Service unavailable');
    expect(component.isLocked()).toBe(false);

    authSession.login.mockRejectedValueOnce('offline');
    await component.onSubmit();
    expect(component.submitError()).toContain('Unable to sign in');
  });

  it('sends an administrator to the admin page and clears a previous lockout', async () => {
    authSession.login.mockRejectedValueOnce(new Error('wrong password'));
    component.form.setValue({ username: 'admin', password: 'password123456' });
    await component.onSubmit();
    expect(localStorage.getItem('login.lockout.v1')).toContain('failedAttempts');

    authSession.isAdmin.mockReturnValue(true);
    authSession.login.mockResolvedValue(undefined);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await component.onSubmit();
    expect(navigateSpy).toHaveBeenCalledWith(['/admin']);
    expect(localStorage.getItem('login.lockout.v1')).toBeNull();
  });

  it('restores an active lock, ignores an expired lock, and survives storage failures', async () => {
    localStorage.setItem('login.lockout.v1', JSON.stringify({
      failedAttempts: 0,
      lockCount: 1,
      lockedUntil: Date.now() + 8_000,
      lockDuration: 10
    }));
    const locked = TestBed.createComponent(LoginComponent);
    expect(locked.componentInstance.isLocked()).toBe(true);
    locked.destroy();

    localStorage.setItem('login.lockout.v1', JSON.stringify({
      failedAttempts: 1,
      lockCount: 1,
      lockedUntil: Date.now() - 1_000,
      lockDuration: 10
    }));
    const expired = TestBed.createComponent(LoginComponent);
    expect(expired.componentInstance.isLocked()).toBe(false);
    expired.destroy();

    localStorage.setItem('login.lockout.v1', '{');
    const corrupt = TestBed.createComponent(LoginComponent);
    expect(corrupt.componentInstance.isLocked()).toBe(false);
    corrupt.destroy();

    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('full');
    });
    authSession.login.mockRejectedValueOnce(new Error('incorrect password'));
    component.form.setValue({ username: 'ada', password: 'wrong-password' });
    await expect(component.onSubmit()).resolves.toBeUndefined();
    expect(component.submitError()).toContain('Wrong username');
    vi.mocked(Storage.prototype.setItem).mockRestore();
  });
});
