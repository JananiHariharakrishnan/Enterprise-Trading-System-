import { ComponentFixture, TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthSessionService } from '../../services/auth-session.service';
import { RegisterComponent } from './register.component';
import { describe, it, expect, beforeEach, vi } from 'vitest';
// Removed raw template/style imports; rely on TestBed to compile component resources.

class AuthSessionServiceStub {
  register = vi.fn();
}

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let authSession: AuthSessionServiceStub;
  let router: Router;

  beforeEach(async () => {
    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter(routes),
        { provide: AuthSessionService, useClass: AuthSessionServiceStub }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('empty fields are blocked by validation', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await component.onSubmit();
    fixture.detectChanges();

    expect(authSession.register).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();

    const error = fixture.nativeElement.querySelector('[data-testid="register-username-error"]') as HTMLElement;
    expect(error).toBeTruthy();
  });

  it('password mismatch is blocked by validation', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.patchValue({
      username: 'newuser',
      password: 'correct horse battery staple',
      confirmPassword: 'different password'
    });

    await component.onSubmit();
    fixture.detectChanges();

    expect(authSession.register).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('valid registration data creates account and redirects', async () => {
    authSession.register.mockResolvedValue({ accountId: 123 });
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.form.setValue({
      username: 'newuser',
      password: 'correct horse battery staple',
      confirmPassword: 'correct horse battery staple'
    });

    expect(component.form.invalid).toBe(false);
    await component.onSubmit();
    fixture.detectChanges();

    expect(authSession.register).toHaveBeenCalledWith('newuser', 'correct horse battery staple');
    expect(navigateSpy).not.toHaveBeenCalled();

    const success = fixture.nativeElement.querySelector('[data-testid="register-success"]') as HTMLElement;
    expect(success?.textContent).toContain('account ID is 123');
  });

  it('registration error shows a readable error', async () => {
    authSession.register.mockRejectedValue(new Error('That username is already taken.'));

    component.form.setValue({
      username: 'taken.user',
      password: 'correct horse battery staple',
      confirmPassword: 'correct horse battery staple'
    });

    await component.onSubmit();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="register-error"]') as HTMLElement;
    expect(error?.textContent).toContain('That username is already taken.');
  });

  it('username must be at least 3 characters', async () => {
    component.form.patchValue({ username: 'ab' });
    component.form.get('username')?.markAsTouched();
    fixture.detectChanges();

    const error = component.getUsernameErrorMessage();
    expect(error).toContain('at least 3 characters');
  });

  it('password must be at least 12 characters', async () => {
    component.form.patchValue({ password: 'short' });
    component.form.get('password')?.markAsTouched();
    fixture.detectChanges();

    const error = component.getPasswordErrorMessage();
    expect(error).toContain('at least 12 characters');
  });

  it('username can only contain allowed characters', async () => {
    component.form.patchValue({ username: 'invalid@user' });
    component.form.get('username')?.markAsTouched();
    fixture.detectChanges();

    const error = component.getUsernameErrorMessage();
    expect(error).toContain('letters, numbers, dots, dashes, and underscores');
  });

  it('link to login is present', () => {
    const link = fixture.nativeElement.querySelector('[data-testid="link-to-login"]') as HTMLElement;
    expect(link).toBeTruthy();
    expect(link?.textContent).toContain('Sign in');
  });
});
