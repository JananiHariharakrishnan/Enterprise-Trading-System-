import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AuthLayoutComponent } from './auth-layout.component';

describe('AuthLayoutComponent', () => {
  let component: AuthLayoutComponent;
  let fixture: ComponentFixture<AuthLayoutComponent>;
  let mockRouter: Partial<Router>;
  let mockActivatedRoute: Partial<ActivatedRoute>;

  beforeEach(async () => {
    mockRouter = {
      events: of(),
      routerState: {
        snapshot: {
          url: '/login'
        }
      }
    } as any;

    mockActivatedRoute = {
      snapshot: {
        url: [],
        params: {},
        queryParams: {}
      }
    } as any;

    await TestBed.configureTestingModule({
      imports: [AuthLayoutComponent],
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: mockActivatedRoute }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AuthLayoutComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display login tab as active when on /login route', () => {
    fixture.detectChanges();

    const loginTab = fixture.nativeElement.querySelector('[data-testid="auth-tab-login"]');
    const registerTab = fixture.nativeElement.querySelector('[data-testid="auth-tab-register"]');

    expect(loginTab.classList.contains('active')).toBe(true);
    expect(registerTab.classList.contains('active')).toBe(false);
  });

  it('should display register tab as active when on /register route', async () => {
    mockRouter!.routerState!.snapshot!.url = '/register';

    fixture = TestBed.createComponent(AuthLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isRegister()).toBe(true);
  });

  it('should set aria-current on active tab', () => {
    fixture.detectChanges();

    const loginTab = fixture.nativeElement.querySelector('[data-testid="auth-tab-login"]');
    const registerTab = fixture.nativeElement.querySelector('[data-testid="auth-tab-register"]');

    expect(loginTab.getAttribute('aria-current')).toBe('page');
    expect(registerTab.getAttribute('aria-current')).toBeNull();
  });

  it('should update highlight position based on isRegister signal', () => {
    fixture.detectChanges();

    const highlight = fixture.nativeElement.querySelector('.auth-switch__highlight');
    expect(highlight.classList.contains('right')).toBe(false);
  });

  it('should handle register URL with query parameters', () => {
    mockRouter!.routerState!.snapshot!.url = '/register?returnUrl=/dashboard';

    fixture = TestBed.createComponent(AuthLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isRegister()).toBe(true);
  });

  it('should handle login URL with hash fragment', () => {
    mockRouter!.routerState!.snapshot!.url = '/login#section';

    fixture = TestBed.createComponent(AuthLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isRegister()).toBe(false);
  });

  it('should render router-outlet for child components', () => {
    fixture.detectChanges();

    const outlet = fixture.nativeElement.querySelector('router-outlet');
    expect(outlet).toBeTruthy();
  });

  it('should have proper navigation structure with links', () => {
    fixture.detectChanges();

    const navArea = fixture.nativeElement.querySelector('.auth-switch');
    const tabs = navArea.querySelectorAll('.auth-switch__tab');

    expect(tabs.length).toBe(2);
    expect(tabs[0].textContent).toContain('Sign in');
    expect(tabs[1].textContent).toContain('Sign up');
  });
});


