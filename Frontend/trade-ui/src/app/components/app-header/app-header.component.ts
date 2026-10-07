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

  // Market strip removed: no polling signals
  readonly isAssetSearchOpen = signal(false);
  readonly isLoadingInstruments = signal(false);
  readonly instrumentSearchText = signal('');
  readonly instrumentSearchError = signal('');
  readonly instrumentResults = signal<InstrumentListItem[]>([]);
  readonly selectedInstrument = signal<InstrumentListItem | null>(null);

  private instrumentSearchDebounceId: ReturnType<typeof setTimeout> | null = null;
  private readonly marketRefreshMs = 15000;

  menuOpen = false;

  ngOnInit(): void {
    // Market polling disabled
  }

  ngOnDestroy(): void {
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

  // Market snapshot polling removed

  private async fetchInstrumentResults(query: string): Promise<void> {
    this.isLoadingInstruments.set(true);
    this.instrumentSearchError.set('');

    try {
      const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
      const trimmedQuery = query.trim();
      const url = new URL(`http://${baseHost}:8081/api/v1/instruments`);

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

  // Helper methods for market snapshot removed
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
