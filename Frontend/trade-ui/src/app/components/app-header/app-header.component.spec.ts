import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { Router, ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AuthSessionService } from '../../services/auth-session.service';
import { ThemeService } from '../../services/theme.service';
import { UiEventsService } from '../../services/ui-events.service';
import { TokenStorageService } from '../../services/token-storage.service';
import { AppHeaderComponent } from './app-header.component';

class AuthSessionServiceStub {
  isAuthenticated = vi.fn(() => true);
  currentUser = signal({ username: 'testuser' } as any);
  sessionRemainingSeconds = signal(900);
  sessionExpiresIn = signal('15:00');
  isSessionExpiringSoon = signal(false);
  isAdmin = signal(false);
  logout = vi.fn().mockResolvedValue(undefined);
}

class ThemeServiceStub {
  currentTheme = signal<'dark' | 'light'>('dark');
  toggleTheme = vi.fn(() => {
    this.currentTheme.set(this.currentTheme() === 'dark' ? 'light' : 'dark');
  });
}

class UiEventsServiceStub {
}

describe('AppHeaderComponent', () => {
  let fixture: ComponentFixture<AppHeaderComponent>;
  let authSession: AuthSessionServiceStub;
  let themeService: ThemeServiceStub;
  let uiEvents: UiEventsServiceStub;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };
  let httpGet: ReturnType<typeof vi.fn>;
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

    tokenStorage = {
      getAccessToken: vi.fn().mockReturnValue('token-1')
    };

    httpGet = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/v1/market-data')) {
        return of({
          'HDFCBANK.NS': { price: 1650.25, changePercent: 0.42, currency: 'INR' },
          AAPL: { price: 189.1, changePercent: -0.2, currency: 'USD' }
        });
      }

      return of([
        { symbol: 'AAPL', name: 'Apple Inc.', assetClass: 'EQUITY', currency: 'USD' },
        { symbol: 'MSFT', name: 'Microsoft Corp.', assetClass: 'EQUITY', currency: 'USD' }
      ]);
    });

    await TestBed.configureTestingModule({
      imports: [AppHeaderComponent],
      providers: [
        { provide: AuthSessionService, useClass: AuthSessionServiceStub },
        { provide: ThemeService, useClass: ThemeServiceStub },
        { provide: UiEventsService, useClass: UiEventsServiceStub },
        {
          provide: HttpClient,
          useValue: {
            get: httpGet
          }
        },
        { provide: TokenStorageService, useValue: tokenStorage },
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
    const button = buttons[1];

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

    expect(authSession.logout).toHaveBeenCalledTimes(1);
  });

  it('navigates to profile from sidebar user card', async () => {
    const userCard = fixture.nativeElement.querySelector('.user-card') as HTMLButtonElement;
    userCard.click();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/profile']);
  });

  it('shows fallback user name and avatar initial when no user exists', () => {
    authSession.currentUser.set(null as any);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Trader');
    expect(fixture.nativeElement.textContent).toContain('U');
  });

  it('toggles the profile menu from the topbar button', () => {
    const profileButton = fixture.nativeElement.querySelector('.profile-button') as HTMLButtonElement;

    profileButton.click();

    expect(fixture.componentInstance.menuOpen).toBe(true);
  });

  it('opens asset search modal and renders instrument rows', async () => {
    const trigger = fixture.nativeElement.querySelector('.asset-search-trigger') as HTMLButtonElement;
    trigger.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.isAssetSearchOpen()).toBe(true);
    expect(fixture.nativeElement.querySelector('.asset-search-modal')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('.asset-search-row').length).toBeGreaterThan(0);
  });

  it('shows no-result state when instrument search returns empty', async () => {
    httpGet.mockImplementation((url: string) => {
      if (url.includes('/api/v1/market-data')) {
        return of({});
      }
      return of([]);
    });

    await fixture.componentInstance.openAssetSearch();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No matching instruments found.');
  });

  it('shows instrument error message when lookup fails', async () => {
    httpGet.mockImplementation((url: string) => {
      if (url.includes('/api/v1/market-data')) {
        return of({});
      }
      return throwError(() => new Error('network'));
    });

    await fixture.componentInstance.openAssetSearch();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unable to load instruments right now.');
  });

  it('selects an instrument row and can clear details', async () => {
    await fixture.componentInstance.openAssetSearch();
    fixture.detectChanges();

    const firstRow = fixture.nativeElement.querySelector('.asset-search-row') as HTMLDivElement;
    firstRow.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedInstrument()).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Instrument details');

    const closeDetails = fixture.nativeElement.querySelector('.asset-detail-head button') as HTMLButtonElement;
    closeDetails.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedInstrument()).toBeNull();
  });

  it('closes asset search from close button and backdrop', async () => {
    await fixture.componentInstance.openAssetSearch();
    fixture.detectChanges();

    const close = fixture.nativeElement.querySelector('.asset-search-close') as HTMLButtonElement;
    close.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.isAssetSearchOpen()).toBe(false);

    await fixture.componentInstance.openAssetSearch();
    fixture.detectChanges();
    const backdrop = fixture.nativeElement.querySelector('.asset-search-modal') as HTMLDivElement;
    backdrop.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.isAssetSearchOpen()).toBe(false);
  });

  it('shows cached instrument prices and a waiting state when the cache is empty', async () => {
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('HDFCBANK.NS');
    expect(fixture.nativeElement.textContent).toContain('AAPL');
    expect(fixture.componentInstance.marketFeedLabel()).toBe('POSITIONS & WATCHLIST');

    httpGet.mockImplementation((url: string) => {
      if (url.includes('/api/v1/market-data')) {
        return of({});
      }
      return of([]);
    });
    await (fixture.componentInstance as unknown as { refreshCachedPrices: () => Promise<void> }).refreshCachedPrices();
    fixture.detectChanges();

    expect(fixture.componentInstance.marketItems().length).toBe(0);
    expect(fixture.componentInstance.marketFeedLabel()).toBe('WAITING FOR CACHE');
    expect(fixture.nativeElement.textContent).toContain('Waiting for cached prices');
  });

  it('returns uppercase initial for instrument symbols', () => {
    expect(fixture.componentInstance.getInstrumentInitial('msft')).toBe('M');
    expect(fixture.componentInstance.getInstrumentInitial('')).toBe('?');
  });

  it('renders session expires countdown in the header', () => {
    fixture.detectChanges();
    const timerEl = fixture.nativeElement.querySelector('[data-testid="session-timer"]');
    expect(timerEl).toBeTruthy();
    expect(timerEl.textContent).toContain('Session expires in:');
    expect(timerEl.textContent).toContain('15:00');
  });
});
