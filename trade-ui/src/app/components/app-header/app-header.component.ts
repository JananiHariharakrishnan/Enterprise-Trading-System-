import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthSessionService } from '../../services/auth-session.service';
import { ThemeService } from '../../services/theme.service';
import { Router } from '@angular/router';
import { UiEventsService } from '../../services/ui-events.service';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from '../../services/token-storage.service';

@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css'
})
export class AppHeaderComponent implements OnInit, OnDestroy {
  readonly authSession = inject(AuthSessionService);
  readonly themeService = inject(ThemeService);
  readonly router = inject(Router);
  readonly uiEvents = inject(UiEventsService);
  readonly tokenStorage = inject(TokenStorageService);
  private readonly http = inject(HttpClient);
  private readonly marketEndpointUrl = 'https://query1.finance.yahoo.com/v7/finance/quote?symbols=%5EGSPC,%5EIXIC,%5EDJI,%5EVIX';
  private readonly instrumentLimit = 75;

  readonly marketItems = signal<MarketStripItem[]>([
    { label: 'S&P 500', value: 6230.42, changePct: 0.34 },
    { label: 'NASDAQ', value: 20184.11, changePct: 0.58 },
    { label: 'DOW', value: 42932.18, changePct: 0.21 },
    { label: 'VIX', value: 14.79, changePct: -1.52 }
  ]);
  readonly marketFeedLabel = signal('LOADING LIVE DATA');
  readonly isAssetSearchOpen = signal(false);
  readonly isLoadingInstruments = signal(false);
  readonly instrumentSearchText = signal('');
  readonly instrumentSearchError = signal('');
  readonly instrumentResults = signal<InstrumentListItem[]>([]);
  readonly selectedInstrument = signal<InstrumentListItem | null>(null);

  private marketPollId: ReturnType<typeof setInterval> | null = null;
  private instrumentSearchDebounceId: ReturnType<typeof setTimeout> | null = null;
  private readonly marketRefreshMs = 15000;

  menuOpen = false;

  ngOnInit(): void {
    void this.refreshMarketSnapshot();
    this.marketPollId = setInterval(() => {
      void this.refreshMarketSnapshot();
    }, this.marketRefreshMs);
  }

  ngOnDestroy(): void {
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

  emitRefresh(): void {
    this.uiEvents.emitRefresh();
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

  private async refreshMarketSnapshot(): Promise<void> {
    try {
      const response = await firstValueFrom(this.http.get<YahooQuoteResponse>(this.marketEndpointUrl));
      const entries = (response.quoteResponse?.result ?? [])
        .map((entry) => ({
          label: this.toIndexLabel(entry.symbol),
          value: entry.regularMarketPrice,
          changePct: entry.regularMarketChangePercent
        }))
        .filter((entry) => {
          return entry.label.length > 0
            && Number.isFinite(entry.value)
            && Number.isFinite(entry.changePct);
        });

      if (entries.length >= 4) {
        this.marketItems.set(this.orderIndices(entries));
        this.marketFeedLabel.set('LIVE YAHOO FINANCE FEED');
      } else {
        this.marketFeedLabel.set('FALLBACK SNAPSHOT');
      }
    } catch {
      this.marketFeedLabel.set('FALLBACK SNAPSHOT');
    }
  }

  private async fetchInstrumentResults(query: string): Promise<void> {
    this.isLoadingInstruments.set(true);
    this.instrumentSearchError.set('');

    try {
      const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
      const trimmedQuery = query.trim();
      const url = new URL(`http://${baseHost}:8888/api/v1/instruments`);

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

  private orderIndices(entries: MarketStripItem[]): MarketStripItem[] {
    const order = ['S&P 500', 'NASDAQ', 'DOW', 'VIX'];
    return order
      .map((label) => entries.find((entry) => entry.label === label))
      .filter((entry): entry is MarketStripItem => entry !== undefined);
  }

  private toIndexLabel(symbol: string): string {
    switch (symbol) {
      case '^GSPC':
        return 'S&P 500';
      case '^IXIC':
        return 'NASDAQ';
      case '^DJI':
        return 'DOW';
      case '^VIX':
        return 'VIX';
      default:
        return '';
    }
  }
}

type MarketStripItem = {
  label: string;
  value: number;
  changePct: number;
};

type YahooQuoteResponse = {
  quoteResponse?: {
    result?: YahooQuoteEntry[];
  };
};

type YahooQuoteEntry = {
  symbol: string;
  regularMarketPrice: number;
  regularMarketChangePercent: number;
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
