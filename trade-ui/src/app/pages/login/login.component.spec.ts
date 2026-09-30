import { ComponentFixture, TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
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

  beforeEach(async () => {
    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter(routes),
        { provide: AuthSessionService, useClass: AuthSessionServiceStub }
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
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.setValue({
      username: 'priya.menon',
      password: 'correct horse battery staple'
    });

    // Simulate a returnUrl present via ActivatedRoute snapshot
    (component as any).route = { snapshot: { queryParamMap: { get: (k: string) => (k === 'returnUrl' ? '/instruments' : null) } } } as any;

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

    // Off-origin returnUrl should be rejected
    (component as any).route = { snapshot: { queryParamMap: { get: (k: string) => (k === 'returnUrl' ? 'https://evil.example.com/phish' : null) } } } as any;

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
