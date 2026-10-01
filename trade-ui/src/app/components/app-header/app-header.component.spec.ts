import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthSessionService } from '../../services/auth-session.service';
import { ThemeService } from '../../services/theme.service';
import { UiEventsService } from '../../services/ui-events.service';
import { AppHeaderComponent } from './app-header.component';

class AuthSessionServiceStub {
  isAuthenticated = vi.fn(() => true);
  currentUser = signal({ username: 'testuser' } as any);
  logout = vi.fn().mockResolvedValue(undefined);
}

class ThemeServiceStub {
  currentTheme = signal<'dark' | 'light'>('dark');
  toggleTheme = vi.fn(() => {
    this.currentTheme.set(this.currentTheme() === 'dark' ? 'light' : 'dark');
  });
}

class UiEventsServiceStub {
  emitRefresh = vi.fn();
}

describe('AppHeaderComponent', () => {
  let fixture: ComponentFixture<AppHeaderComponent>;
  let authSession: AuthSessionServiceStub;
  let themeService: ThemeServiceStub;
  let uiEvents: UiEventsServiceStub;
  let router: any;

  const openProfileMenu = () => {
    const profileButton = fixture.nativeElement.querySelector('.profile-button') as HTMLButtonElement;
    profileButton.click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    router = {
      navigate: vi.fn().mockResolvedValue(true),
      // RouterLinkActive and other directives subscribe to `events`
      events: of(null)
    };

    await TestBed.configureTestingModule({
      imports: [AppHeaderComponent],
      providers: [
        { provide: AuthSessionService, useClass: AuthSessionServiceStub },
        { provide: ThemeService, useClass: ThemeServiceStub },
        { provide: UiEventsService, useClass: UiEventsServiceStub },
        { provide: Router, useValue: router }
        , { provide: ActivatedRoute, useValue: { queryParams: of({}) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AppHeaderComponent);
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
    themeService = TestBed.inject(ThemeService) as unknown as ThemeServiceStub;
    uiEvents = TestBed.inject(UiEventsService) as unknown as UiEventsServiceStub;
    fixture.detectChanges();
  });

  it('signing out clears the session via auth service', async () => {
    openProfileMenu();

    const buttons = fixture.nativeElement.querySelectorAll('.profile-dropdown button') as NodeListOf<HTMLButtonElement>;
    const button = buttons[2];

    button.click();
    await fixture.whenStable();

    expect(authSession.logout).toHaveBeenCalledTimes(1);
  });

  it('toggles the theme via the header button', () => {
    const button = fixture.nativeElement.querySelector('[data-testid="theme-toggle-button"]') as HTMLButtonElement;

    button.click();

    expect(themeService.toggleTheme).toHaveBeenCalledTimes(1);
  });

  it('navigates to profile from the dropdown', async () => {
    openProfileMenu();

    const buttons = fixture.nativeElement.querySelectorAll('.profile-dropdown button') as NodeListOf<HTMLButtonElement>;
    buttons[0].click();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/profile']);
    expect(fixture.componentInstance.menuOpen).toBe(false);
  });

  it('emits refresh from the dropdown', () => {
    openProfileMenu();

    const buttons = fixture.nativeElement.querySelectorAll('.profile-dropdown button') as NodeListOf<HTMLButtonElement>;
    buttons[1].click();

    expect(uiEvents.emitRefresh).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.menuOpen).toBe(false);
  });
});