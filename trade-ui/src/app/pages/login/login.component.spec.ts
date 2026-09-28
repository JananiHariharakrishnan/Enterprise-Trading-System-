import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthSessionService } from '../../services/auth-session.service';
import { LoginComponent } from './login.component';

class AuthSessionServiceStub {
  login = vi.fn();
}

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSession: AuthSessionServiceStub;
  let router: Router;

  beforeEach(async () => {
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

    await component.onSubmit();

    expect(authSession.login).toHaveBeenCalledWith('priya.menon', 'correct horse battery staple');
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
