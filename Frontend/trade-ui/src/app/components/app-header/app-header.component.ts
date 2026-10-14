import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChild, ViewChildren, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthSessionService } from '../../services/auth-session.service';
import { ThemeService } from '../../services/theme.service';
import { Router } from '@angular/router';
import { UiEventsService } from '../../services/ui-events.service';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from '../../services/token-storage.service';
import { NotificationPanelComponent } from '../notification-panel/notification-panel.component';
import { CustomerPreferencesService, type BaseCurrency } from '../../services/customer-preferences.service';
import { convertAmount, currencySymbol, quoteCurrency } from '../../utils/currency.util';
import { getTradeApiV1Url } from '../../config/api.config';

@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [CommonModule, RouterModule, NotificationPanelComponent],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css'
})
export class AppHeaderComponent implements OnInit, AfterViewInit, OnDestroy {
  readonly authSession = inject(AuthSessionService);
  readonly themeService = inject(ThemeService);
  readonly router = inject(Router);
  readonly uiEvents = inject(UiEventsService);
  readonly tokenStorage = inject(TokenStorageService);
  private readonly http = inject(HttpClient);
  private readonly preferencesApi = inject(CustomerPreferencesService);
  private readonly instrumentLimit = 75;

  // Market strip removed: no polling signals
  readonly marketItems = signal<MarketStripItem[]>([]);
  readonly marketFeedLabel = signal('WAITING FOR CACHE');
  readonly displayCurrency = signal<BaseCurrency>('INR');
  readonly isAssetSearchOpen = signal(false);
  readonly isLoadingInstruments = signal(false);
  readonly instrumentSearchText = signal('');
  readonly instrumentSearchError = signal('');
  readonly instrumentResults = signal<InstrumentListItem[]>([]);
  readonly selectedInstrument = signal<InstrumentListItem | null>(null);

  @ViewChild('tickerTrack') private tickerTrack?: ElementRef<HTMLElement>;
  @ViewChildren('tickerSet') private tickerSets?: QueryList<ElementRef<HTMLElement>>;

  readonly tickerCopies = [0, 1, 2];

  private marketPollId: ReturnType<typeof setInterval> | null = null;
  private tickerMotion: Animation | null = null;
  private tickerDistance = 0;
  private priceRequestId = 0;
  private instrumentSearchDebounceId: ReturnType<typeof setTimeout> | null = null;
  private readonly marketRefreshMs = 15000;

  menuOpen = false;

  priceSymbol(): string {
    return currencySymbol(this.displayCurrency());
  }

  displayPrice(item: MarketStripItem): number {
    return convertAmount(item.value, item.quoteCurrency, this.displayCurrency());
  }

  ngOnInit(): void {
    void this.refreshCachedPrices();
    if (this.marketPollId) {
      clearInterval(this.marketPollId);
    }
    this.marketPollId = setInterval(() => {
      void this.refreshCachedPrices();
    }, this.marketRefreshMs);
  }

  ngAfterViewInit(): void {
    this.scheduleTickerMotion();
  }

  ngOnDestroy(): void {
    this.tickerMotion?.cancel();
    this.tickerMotion = null;
    if (this.marketPollId) {
      clearInterval(this.marketPollId);
      this.marketPollId = null;
    }

    if (this.instrumentSearchDebounceId) {
      clearTimeout(this.instrumentSearchDebounceId);
      this.instrumentSearchDebounceId = null;
    }
  }

  async onSignOut(): Promise<void> {
    await this.authSession.logout();
  }

  isAdminTabActive(tab: string): boolean {
    const url = this.router?.url || '';
    if (!url.startsWith('/admin')) {
      return false;
    }
    if (tab === 'dashboard') {
      return url === '/admin' || url.includes('tab=dashboard');
    }
    return url.includes(`tab=${tab}`);
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  toggleProfileMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  goToProfile(): void {
    void this.router.navigate(['/profile']);
    this.menuOpen = false;
  }

  async openAssetSearch(): Promise<void> {
    this.isAssetSearchOpen.set(true);
    this.instrumentSearchText.set('');
    await this.fetchInstrumentResults('');
  }

  closeAssetSearch(): void {
    this.isAssetSearchOpen.set(false);
    this.instrumentSearchError.set('');
    this.selectedInstrument.set(null);
  }

  onAssetSearchInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    const query = target.value;
    this.instrumentSearchText.set(query);

    if (this.instrumentSearchDebounceId) {
      clearTimeout(this.instrumentSearchDebounceId);
    }

    this.instrumentSearchDebounceId = setTimeout(() => {
      void this.fetchInstrumentResults(query);
    }, 180);
  }

  getInstrumentInitial(symbol: string): string {
    return (symbol || '?').charAt(0).toUpperCase();
  }

  selectInstrument(item: InstrumentListItem): void {
    this.selectedInstrument.set(item);
  }

  clearSelectedInstrument(): void {
    this.selectedInstrument.set(null);
  }

  private async refreshCachedPrices(): Promise<void> {
    const requestId = ++this.priceRequestId;
    try {
      const url = `${getTradeApiV1Url()}/market-data`;
      const token = this.tokenStorage.getAccessToken();
      const headers: Record<string, string | string[]> = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await firstValueFrom(
        this.http.get<Record<string, CachedQuote>>(url, { headers })
      );
      await this.refreshDisplayCurrency();
      if (requestId !== this.priceRequestId) {
        return;
      }
      const entries = this.toTickerItems(response);
      this.marketItems.set(entries);
      this.marketFeedLabel.set(entries.length > 0 ? 'POSITIONS & WATCHLIST' : 'WAITING FOR CACHE');
      this.scheduleTickerMotion();
    } catch {
      if (requestId !== this.priceRequestId) {
        return;
      }
      this.marketItems.set([]);
      this.marketFeedLabel.set('CACHE UNAVAILABLE');
      this.tickerMotion?.cancel();
      this.tickerMotion = null;
      this.tickerDistance = 0;
    }
  }

  private scheduleTickerMotion(): void {
    requestAnimationFrame(() => requestAnimationFrame(() => this.syncTickerMotion()));
  }

  private syncTickerMotion(): void {
    const track = this.tickerTrack?.nativeElement;
    const set = this.tickerSets?.first?.nativeElement;
    if (!track || !set || typeof track.animate !== 'function' || this.marketItems().length === 0) {
      return;
    }

    const distance = Math.round(set.getBoundingClientRect().width);
    if (distance <= 0) {
      return;
    }

    if (this.tickerMotion?.playState === 'running' && Math.abs(distance - this.tickerDistance) < 8) {
      return;
    }

    this.tickerDistance = distance;
    this.tickerMotion?.cancel();
    const duration = Math.max(26000, this.marketItems().length * 5200);
    this.tickerMotion = track.animate(
      [
        { transform: 'translate3d(0, 0, 0)' },
        { transform: `translate3d(${-distance}px, 0, 0)` }
      ],
      { duration, iterations: Infinity, easing: 'linear' }
    );
  }

  private toTickerItems(response: Record<string, CachedQuote> | null | undefined): MarketStripItem[] {
    if (!response || Array.isArray(response) || typeof response !== 'object') {
      return [];
    }

    return Object.entries(response)
      .map(([symbol, quote]) => ({
        label: symbol,
        value: Number(quote?.price),
        changePct: Number(quote?.changePercent ?? 0),
        quoteCurrency: (quote?.currency || quoteCurrency(symbol)).toUpperCase()
      }))
      .filter((entry) => entry.label.trim().length > 0 && Number.isFinite(entry.value))
      .sort((left, right) => left.label.localeCompare(right.label));
  }

  private async refreshDisplayCurrency(): Promise<void> {
    const accountId = this.authSession.currentUser()?.accountId;
    if (!accountId) {
      this.displayCurrency.set('INR');
      return;
    }

    try {
      const preferences = await this.preferencesApi.getPreferences(accountId);
      this.displayCurrency.set(preferences?.display?.baseCurrency === 'USD' ? 'USD' : 'INR');
    } catch {
      this.displayCurrency.set('INR');
    }
  }

  private async fetchInstrumentResults(query: string): Promise<void> {
    this.isLoadingInstruments.set(true);
    this.instrumentSearchError.set('');

    try {
      const trimmedQuery = query.trim();
      const url = new URL(`${getTradeApiV1Url()}/instruments`);

      if (trimmedQuery.length > 0) {
        url.searchParams.set('query', trimmedQuery);
      }

      url.searchParams.set('limit', String(this.instrumentLimit));

      const token = this.tokenStorage.getAccessToken();
      const headers: Record<string, string | string[]> = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const response = await firstValueFrom(
        this.http.get<InstrumentLookupApiItem[]>(url.toString(), { headers })
      );

      const rows = response
        .map((item) => ({
          symbol: item.symbol,
          name: item.name,
          assetClass: item.assetClass,
          currency: item.currency
        }))
        .filter((item) => item.symbol && item.name);

      this.instrumentResults.set(rows);
      if (this.selectedInstrument()) {
        const selectedSymbol = this.selectedInstrument()?.symbol;
        const updatedSelection = rows.find((row) => row.symbol === selectedSymbol) ?? null;
        this.selectedInstrument.set(updatedSelection);
      }
    } catch {
      this.instrumentResults.set([]);
      this.instrumentSearchError.set('Unable to load instruments right now.');
      this.selectedInstrument.set(null);
    } finally {
      this.isLoadingInstruments.set(false);
    }
  }

}

type MarketStripItem = {
  label: string;
  value: number;
  changePct: number;
  quoteCurrency: string;
};

type CachedQuote = {
  price?: number | string | null;
  changePercent?: number | string | null;
  currency?: string | null;
};

type InstrumentLookupApiItem = {
  symbol: string;
  name: string;
  assetClass: string | null;
  currency: string | null;
};

type InstrumentListItem = {
  symbol: string;
  name: string;
  assetClass: string | null;
  currency: string | null;
};
