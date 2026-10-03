import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthSessionService } from '../../services/auth-session.service';
import { LoginComponent } from './login.component';
// Removed raw template/style imports; rely on TestBed to compile component resources.

class AuthSessionServiceStub {
  login = vi.fn();
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

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('empty fields are blocked by validation', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await component.onSubmit();
    fixture.detectChanges();

    expect(authSession.login).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();

    const error = fixture.nativeElement.querySelector('[data-testid="auth-error-empty-field"]') as HTMLElement;
    expect(error?.textContent).toContain('Username and password are required.');
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
    expect(error?.textContent).toContain('Invalid username or password.');
  });
});
