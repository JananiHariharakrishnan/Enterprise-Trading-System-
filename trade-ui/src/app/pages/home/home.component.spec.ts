import { ComponentFixture, TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthSessionService } from '../../services/auth-session.service';
import { HomeComponent } from './home.component';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { signal } from '@angular/core';
// Removed raw template/style imports; rely on TestBed to compile component resources.

class AuthSessionServiceStub {
  isAuthenticated = signal(false);
  currentUser = signal(null);
}

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let component: HomeComponent;
  let authSession: AuthSessionServiceStub;
  let router: Router;

  beforeEach(async () => {
    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter(routes),
        { provide: AuthSessionService, useClass: AuthSessionServiceStub }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('shows login and register buttons when not authenticated', () => {
    authSession.isAuthenticated.set(false);
    fixture.detectChanges();

    const loginButton = fixture.nativeElement.querySelector('[data-testid="home-link-login"]') as HTMLElement;
    const registerButton = fixture.nativeElement.querySelector('[data-testid="home-link-register"]') as HTMLElement;

    expect(loginButton).toBeTruthy();
    expect(registerButton).toBeTruthy();
  });

  it('navigates to login when login button is clicked', async () => {
    authSession.isAuthenticated.set(false);
    fixture.detectChanges();

    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await component.navigateToLogin();

    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('navigates to register when register button is clicked', async () => {
    authSession.isAuthenticated.set(false);
    fixture.detectChanges();

    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await component.navigateToRegister();

    expect(navigateSpy).toHaveBeenCalledWith(['/register']);
  });

  it('shows dashboard link and username when authenticated', () => {
    authSession.isAuthenticated.set(true);
    authSession.currentUser.set({ username: 'testuser', accountId: 123, id: 'uuid', roles: ['CUSTOMER'], createdOn: new Date().toISOString() } as any);
    fixture.detectChanges();

    const dashboardButton = fixture.nativeElement.querySelector('[data-testid="home-link-dashboard"]') as HTMLElement;
    const welcomeText = fixture.nativeElement.textContent;

    expect(dashboardButton).toBeTruthy();
    expect(welcomeText).toContain('testuser');
  });

  it('navigates to dashboard when dashboard button is clicked', async () => {
    authSession.isAuthenticated.set(true);
    fixture.detectChanges();

    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await component.navigateToDashboard();

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  });
});
