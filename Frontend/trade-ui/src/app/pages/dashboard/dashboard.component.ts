import { CommonModule } from '@angular/common';
import { Component, inject, computed, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { RouterLink } from '@angular/router';
import { avgCostInUsd, quoteCurrency } from '../../utils/currency.util';
import {
  AccountResponse,
  BalanceResponse,
  LatestPriceResponse,
  OrderHistoryEntry,
  PositionResponse,
  WatchlistDetailResponse,
  WatchlistInstrumentResponse,
  WatchlistSummaryResponse,
} from '../../generated/trade';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { UiEventsService } from '../../services/ui-events.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly authSession = inject(AuthSessionService);
  private readonly tradeApi = inject(TradeApiClientService);
  private readonly uiEvents = inject(UiEventsService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly http = inject(HttpClient);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly account = signal<AccountResponse | null>(null);
  readonly balance = signal<BalanceResponse | null>(null);
  readonly positions = signal<PositionResponse[]>([]);
  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly watchlists = signal<WatchlistSummaryResponse[]>([]);
  readonly selectedWatchlist = signal<WatchlistDetailResponse | null>(null);
  readonly selectedInstrument = signal<WatchlistInstrumentResponse | null>(null);
  readonly watchlistMessage = signal<string | null>(null);
  readonly watchlistError = signal<string | null>(null);
  readonly isLoadingWatchlist = signal(false);
  readonly isSavingWatchlist = signal(false);
  readonly analyticsLoading = signal(false);
  readonly analyticsError = signal<string | null>(null);
  readonly analyticsNotice = signal<string | null>(null);
  readonly portfolioLinePoints = signal('');
  readonly portfolioTrendMin = signal(0);
  readonly portfolioTrendMax = signal(0);
  readonly stockBars = signal<Array<{ symbol: string; close: number; changePct: number; heightPct: number }>>([]);
  readonly topPerformers = signal<Array<{ symbol: string; price: number; changePct: number }>>([]);
  readonly selectedCandleSymbol = signal('');
  readonly candleBars = signal<Array<{ x: number; wickTop: number; wickBottom: number; bodyY: number; bodyHeight: number; up: boolean }>>([]);
  readonly candleMin = signal(0);
  readonly candleMax = signal(0);
  readonly lastAnalyticsUpdated = signal<string>('');
  readonly refreshSeconds = signal(30);

  private candleSeriesBySymbol = new Map<string, CandlePoint[]>();
  private analyticsIntervalId: ReturnType<typeof setInterval> | null = null;

  readonly createWatchlistForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]]
  });

  readonly addInstrumentForm = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required, Validators.maxLength(32)]]
  });
 readonly today = new Date();

 readonly greeting = computed(() => {
   const hour = new Date().getHours();
   return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
 });

 readonly currency = computed(() =>
   String((this.balance() as any)?.currency ?? (this.account() as any)?.currency ?? 'USD')
 );

 readonly isActive = computed(() => String(this.account()?.status ?? '').toUpperCase() === 'ACTIVE');

 readonly statusLabel = computed(() => {
   const s = String(this.account()?.status ?? '').toLowerCase();
   return s ? s.charAt(0).toUpperCase() + s.slice(1) : 'Unknown';
 });

 // ---- portfolio snapshot: same USD conversion as the Positions page ----
 /** Cost of one position in USD. .NS / .BO positions are stored in INR, so they are converted. */
 private positionValueUsd(p: PositionResponse): number {
   const quantity = Number(p.quantity) || 0;
   return quantity * avgCostInUsd(Number(p.averageCost) || 0, String(p.symbol ?? ''));
 }

 readonly cash = computed(() => Number(this.account()?.cashBalance) || 0);

 readonly costBasis = computed(() =>
   this.positions().reduce((sum, p) => sum + this.positionValueUsd(p), 0)
 );

 readonly totalAtCost = computed(() => this.cash() + this.costBasis());

 readonly cashPct = computed(() => (this.totalAtCost() > 0 ? this.cash() / this.totalAtCost() : 1));

 readonly largestPosition = computed(() => {
   const total = this.costBasis();
   const rows = this.positions()
     .map(p => ({ symbol: String(p.symbol ?? ''), value: this.positionValueUsd(p) }))
     .sort((a, b) => b.value - a.value);
   return rows.length && total > 0 ? { symbol: rows[0].symbol, pct: rows[0].value / total } : null;
 });

 // ---- recent orders: INR symbols show their price in INR (₹), not USD ----
 readonly recentOrders = computed(() =>
   this.orders().slice(0, 3).map(o => {
     const a = o as any;
     const symbol = String(a.symbol ?? '—');
     return {
       symbol,
       side: String(a.side ?? '').toUpperCase(),
       quantity: a.quantity,
       price: a.executedPrice ?? a.price ?? a.limitPrice ?? null,
       currency: quoteCurrency(symbol, this.currency()),
       status: String(a.status ?? '').toUpperCase(),
       time: a.createdOn ?? a.createdAt ?? a.placedAt ?? a.timestamp ?? null
     };
   })
 );

 orderStatusClass(status: string): string {
   if (status === 'FILLED') return 'ok';
   if (status === 'REJECTED' || status === 'CANCELLED') return 'bad';
   if (status === 'NEW' || status === 'PENDING') return 'pending';
   return '';
 }

  ngOnInit(): void {
    void this.loadDashboard();
    this.uiEvents.onRefresh().subscribe(() => void this.refresh());
    this.startAnalyticsLiveFeed();
  }

  ngOnDestroy(): void {
    if (this.analyticsIntervalId) {
      clearInterval(this.analyticsIntervalId);
      this.analyticsIntervalId = null;
    }
  }

  async refresh(): Promise<void> {
    await this.loadDashboard();
  }

  private startAnalyticsLiveFeed(): void {
    if (this.analyticsIntervalId) {
      clearInterval(this.analyticsIntervalId);
    }

    this.analyticsIntervalId = setInterval(() => {
      void this.refreshLiveAnalytics();
    }, this.refreshSeconds() * 1000);
  }

  onRefreshFrequencyChange(event: Event): void {
    const target = event.target as HTMLSelectElement;
    const seconds = Number.parseInt(target.value, 10);
    if (![15, 30, 60].includes(seconds)) {
      return;
    }

    this.refreshSeconds.set(seconds);
    this.startAnalyticsLiveFeed();
  }

  private async refreshLiveAnalytics(): Promise<void> {
    if (this.analyticsLoading()) {
      return;
    }

    await this.loadTopPerformerAnalytics();
  }

  logout(): void {
    void this.authSession.logout();
  }

  async createWatchlist(): Promise<void> {
    this.watchlistMessage.set(null);
    this.watchlistError.set(null);

    if (this.createWatchlistForm.invalid) {
      this.createWatchlistForm.markAllAsTouched();
      return;
    }

    this.isSavingWatchlist.set(true);

    try {
      const name = this.createWatchlistForm.getRawValue().name.trim();
      const created = await this.tradeApi.watchlists.createWatchlist({
        createWatchlistRequest: { name }
      });

      await this.loadWatchlists();
      this.createWatchlistForm.reset({ name: '' });
      await this.openWatchlist(created.id);
      this.watchlistMessage.set('Watchlist created successfully.');
    } catch (error) {
      this.watchlistError.set(this.toWatchlistMessage(error, 'Unable to create watchlist right now.'));
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async openWatchlist(watchlistId: number): Promise<void> {
    this.watchlistMessage.set(null);
    this.watchlistError.set(null);
    this.isLoadingWatchlist.set(true);

    try {
      const detail = await this.tradeApi.watchlists.getWatchlist({ watchlistId });
      this.selectedWatchlist.set(detail);
    } catch (error) {
      this.watchlistError.set(this.toWatchlistMessage(error, 'Unable to load watchlist details right now.'));
    } finally {
      this.isLoadingWatchlist.set(false);
    }
  }

  async addInstrument(): Promise<void> {
    this.watchlistMessage.set(null);
    this.watchlistError.set(null);

    const watchlist = this.selectedWatchlist();
    if (!watchlist) {
      this.watchlistError.set('Choose a watchlist before adding an instrument.');
      return;
    }

    if (this.addInstrumentForm.invalid) {
      this.addInstrumentForm.markAllAsTouched();
      return;
    }

    this.isSavingWatchlist.set(true);

    try {
      const symbol = this.addInstrumentForm.getRawValue().symbol.trim().toUpperCase();
      await this.tradeApi.watchlists.addWatchlistInstrument({
        watchlistId: watchlist.id,
        addWatchlistInstrumentRequest: { symbol }
      });

      this.addInstrumentForm.reset({ symbol: '' });
      this.watchlistMessage.set('Instrument added to watchlist.');
      await this.loadWatchlists();
      await this.openWatchlist(watchlist.id);
    } catch (error) {
      this.watchlistError.set(this.toWatchlistMessage(error, 'Unable to add instrument right now.'));
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async removeInstrument(symbol: string): Promise<void> {
    this.watchlistMessage.set(null);
    this.watchlistError.set(null);

    const watchlist = this.selectedWatchlist();
    if (!watchlist) {
      return;
    }

    this.isSavingWatchlist.set(true);

    try {
      await this.tradeApi.watchlists.removeWatchlistInstrument({
        watchlistId: watchlist.id,
        symbol
      });

      this.watchlistMessage.set('Instrument removed from watchlist.');
      await this.loadWatchlists();
      await this.openWatchlist(watchlist.id);
    } catch (error) {
      this.watchlistError.set(this.toWatchlistMessage(error, 'Unable to remove instrument right now.'));
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async deleteWatchlist(watchlistId: number): Promise<void> {
    this.watchlistMessage.set(null);
    this.watchlistError.set(null);
    this.isSavingWatchlist.set(true);

    try {
      await this.tradeApi.watchlists.deleteWatchlist({ watchlistId });
      await this.loadWatchlists();

      if (this.selectedWatchlist()?.id === watchlistId) {
        this.selectedWatchlist.set(null);
        this.selectedInstrument.set(null);
      }

      this.watchlistMessage.set('Watchlist deleted.');
    } catch (error) {
      this.watchlistError.set(this.toWatchlistMessage(error, 'Unable to delete watchlist right now.'));
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  openInstrumentDetails(instrument: WatchlistInstrumentResponse): void {
    this.selectedInstrument.set(instrument);
  }

  closeInstrumentDetails(): void {
    this.selectedInstrument.set(null);
  }

  latestPriceSummary(latestPrice: LatestPriceResponse | null | undefined): string {
    if (!latestPrice) {
      return 'No quote yet';
    }

    return `${latestPrice.currency} ${latestPrice.price.toFixed(2)}`;
  }

  private async loadDashboard(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);

    try {
      const user = await this.authSession.loadCurrentUser();
      if (!user) {
        this.loadError.set('Your session expired. Please sign in again.');
        return;
      }

      const accountId = user.accountId;
      const [account, balance, positions, orders] = await Promise.all([
        this.tradeApi.accounts.getAccount({ id: accountId }),
        this.tradeApi.accounts.getBalance({ id: accountId }),
        this.tradeApi.accounts.getPositions({ id: accountId }),
        this.tradeApi.accounts.getOrders({ id: accountId })
      ]);

      this.account.set(account);
      this.balance.set(balance);
      this.positions.set(positions);
      this.orders.set(orders.slice(0, 10));
      await this.loadWatchlists();
      await this.loadTopPerformerAnalytics();
    } catch {
      this.loadError.set('Unable to load dashboard data right now. Check trade-api and try again.');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadTopPerformerAnalytics(): Promise<void> {
    this.analyticsLoading.set(true);
    this.analyticsError.set(null);
    this.analyticsNotice.set(null);
    this.candleSeriesBySymbol.clear();

    try {
      const performers = await this.fetchTopMovers();
      if (performers.length === 0) {
        throw new Error('No top movers available.');
      }

      await this.hydrateAnalyticsFromPerformers(performers);
      this.analyticsNotice.set('Top five daily movers loaded from live market feed.');
      this.lastAnalyticsUpdated.set(new Date().toLocaleTimeString());
    } catch {
      await this.loadFallbackAnalytics();
    } finally {
      this.analyticsLoading.set(false);
    }
  }

  private async fetchTopMovers(): Promise<MarketPerformer[]> {
    const url = 'https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=50&page=1&sparkline=false&price_change_percentage=24h';
    const response = await firstValueFrom(this.http.get<CoinGeckoMarketEntry[]>(url));

    return response
      .map((entry) => ({
        id: entry.id,
        symbol: (entry.symbol ?? '').toUpperCase(),
        price: entry.current_price,
        changePct: entry.price_change_percentage_24h
      }))
      .filter((entry) => {
        return entry.id.trim().length > 0
          && entry.symbol.length > 0
          && Number.isFinite(entry.price)
          && Number.isFinite(entry.changePct);
      })
      .sort((a, b) => b.changePct - a.changePct)
      .slice(0, 5);
  }

  private async hydrateAnalyticsFromPerformers(performers: MarketPerformer[]): Promise<void> {
    const seriesResults = await Promise.allSettled(performers.map((performer) => this.fetchCoinSeries(performer.id)));

    const seriesBySymbol = new Map<string, MarketSeriesResult>();
    seriesResults.forEach((result, index) => {
      if (result.status === 'fulfilled') {
        seriesBySymbol.set(performers[index].symbol, result.value);
      }
    });

    const trendSeries = Array.from(seriesBySymbol.values())
      .map((entry) => entry.closes)
      .filter((closes) => closes.length >= 2);

    if (trendSeries.length === 0) {
      throw new Error('No trend data available for top movers.');
    }

    const shortestLength = Math.min(...trendSeries.map((closes) => closes.length));
    const aligned = trendSeries.map((closes) => closes.slice(closes.length - shortestLength));
    const combinedTrend = Array.from({ length: shortestLength }, (_, index) => {
      const total = aligned.reduce((sum, closes) => sum + closes[index], 0);
      return +(total / aligned.length).toFixed(2);
    });

    this.portfolioTrendMin.set(Math.min(...combinedTrend));
    this.portfolioTrendMax.set(Math.max(...combinedTrend));
    this.portfolioLinePoints.set(this.toLinePoints(combinedTrend, 680, 220));

    const maxMove = Math.max(...performers.map((performer) => Math.abs(performer.changePct)), 1);
    const bars = performers.map((performer) => {
      const latest = seriesBySymbol.get(performer.symbol)?.closes.at(-1);
      const close = typeof latest === 'number' ? latest : performer.price;
      const heightPct = Math.max(8, (Math.abs(performer.changePct) / maxMove) * 100);
      return {
        symbol: performer.symbol,
        close: +close.toFixed(2),
        changePct: performer.changePct,
        heightPct: +heightPct.toFixed(2)
      };
    });

    this.topPerformers.set(performers.map((performer) => ({
      symbol: performer.symbol,
      price: performer.price,
      changePct: performer.changePct
    })));
    this.stockBars.set(bars);

    this.candleSeriesBySymbol = new Map<string, CandlePoint[]>();
    performers.forEach((performer) => {
      const candles = seriesBySymbol.get(performer.symbol)?.candles ?? [];
      if (candles.length > 0) {
        this.candleSeriesBySymbol.set(performer.symbol, candles);
      }
    });

    const initialSymbol = performers.find((performer) => this.candleSeriesBySymbol.has(performer.symbol))?.symbol ?? performers[0].symbol;
    this.selectCandleSymbol(initialSymbol);
  }

  selectCandleSymbol(symbol: string): void {
    const candles = this.candleSeriesBySymbol.get(symbol) ?? [];
    if (candles.length === 0) {
      return;
    }

    this.selectedCandleSymbol.set(symbol);

    const recent = candles.slice(-12);
    const lows = recent.map((candle) => candle.low);
    const highs = recent.map((candle) => candle.high);
    const min = Math.min(...lows);
    const max = Math.max(...highs);
    const range = max - min || 1;

    this.candleMin.set(min);
    this.candleMax.set(max);

    const width = 640;
    const height = 240;
    const padding = 22;
    const slot = (width - padding * 2) / recent.length;
    const bodyWidth = Math.max(6, Math.min(16, slot * 0.5));

    const y = (price: number): number => {
      return padding + ((max - price) / range) * (height - padding * 2);
    };

    const bars = recent.map((candle, index) => {
      const x = padding + slot * index + slot / 2;
      const openY = y(candle.open);
      const closeY = y(candle.close);
      return {
        x,
        wickTop: y(candle.high),
        wickBottom: y(candle.low),
        bodyY: Math.min(openY, closeY),
        bodyHeight: Math.max(2, Math.abs(closeY - openY)),
        up: candle.close >= candle.open,
        bodyWidth
      };
    });

    this.candleBars.set(bars.map((bar) => ({
      x: bar.x,
      wickTop: bar.wickTop,
      wickBottom: bar.wickBottom,
      bodyY: bar.bodyY,
      bodyHeight: bar.bodyHeight,
      up: bar.up
    })));
  }

  private async loadFallbackAnalytics(): Promise<void> {
    const fallback = [
      { symbol: 'BTC', price: 64210.25, changePct: 2.84 },
      { symbol: 'ETH', price: 3388.40, changePct: 2.16 },
      { symbol: 'SOL', price: 152.31, changePct: 1.72 },
      { symbol: 'XRP', price: 0.62, changePct: 1.11 },
      { symbol: 'AVAX', price: 39.45, changePct: 0.97 }
    ];

    this.analyticsError.set(null);
    this.analyticsNotice.set('Live market feed unavailable. Showing fallback top-performer snapshot.');
    this.lastAnalyticsUpdated.set(new Date().toLocaleTimeString());

    const maxMove = Math.max(...fallback.map((entry) => Math.abs(entry.changePct)), 1);
    this.topPerformers.set(fallback);
    this.stockBars.set(fallback.map((entry) => ({
      symbol: entry.symbol,
      close: entry.price,
      changePct: entry.changePct,
      heightPct: Math.max(8, (Math.abs(entry.changePct) / maxMove) * 100)
    })));

    const syntheticTrend = [100, 101.2, 101.8, 102.1, 103.3, 102.9, 103.8, 104.2, 104.6, 105.1, 105.9, 106.4];
    this.portfolioTrendMin.set(Math.min(...syntheticTrend));
    this.portfolioTrendMax.set(Math.max(...syntheticTrend));
    this.portfolioLinePoints.set(this.toLinePoints(syntheticTrend, 680, 220));

    this.candleSeriesBySymbol = new Map<string, CandlePoint[]>();
    fallback.forEach((entry) => {
      this.candleSeriesBySymbol.set(entry.symbol, this.createSyntheticCandles(entry.price));
    });
    this.selectCandleSymbol(fallback[0].symbol);
  }

  private createSyntheticCandles(basePrice: number): CandlePoint[] {
    const candles: CandlePoint[] = [];
    let previousClose = basePrice * 0.95;
    for (let day = 0; day < 12; day++) {
      const drift = (Math.sin(day * 0.8) + 1) * 0.7;
      const open = +(previousClose + drift * 0.4).toFixed(2);
      const close = +(open + (Math.cos(day * 0.6) * 1.5 + 0.2)).toFixed(2);
      const high = +(Math.max(open, close) + 0.9).toFixed(2);
      const low = +(Math.min(open, close) - 0.9).toFixed(2);
      candles.push({ open, high, low, close, timestamp: Date.now() - (12 - day) * 86400000 });
      previousClose = close;
    }
    return candles;
  }

  private async fetchCoinSeries(coinId: string): Promise<MarketSeriesResult> {
    let candles = await this.fetchCoinOhlc(coinId, 1);
    if (candles.length < 2) {
      candles = await this.fetchCoinOhlc(coinId, 7);
    }

    const closes = candles.map((candle) => candle.close);
    return { closes, candles };
  }

  private async fetchCoinOhlc(coinId: string, days: 1 | 7): Promise<CandlePoint[]> {
    const url = `https://api.coingecko.com/api/v3/coins/${encodeURIComponent(coinId)}/ohlc?vs_currency=usd&days=${days}`;
    const response = await firstValueFrom(this.http.get<CoinGeckoOhlcResponse>(url));

    return response
      .filter((row) => Array.isArray(row) && row.length >= 5)
      .map((row) => {
        const [timestamp, open, high, low, close] = row;
        return { timestamp, open, high, low, close };
      })
      .filter((candle) => {
        return [
          candle.timestamp,
          candle.open,
          candle.high,
          candle.low,
          candle.close
        ].every((value) => Number.isFinite(value));
      });
  }

  private toLinePoints(values: number[], width: number, height: number): string {
    if (values.length === 0) {
      return '';
    }

    const padding = 16;
    const min = Math.min(...values);
    const max = Math.max(...values);
    const valueRange = max - min || 1;

    return values
      .map((value, index) => {
        const x = padding + (index / Math.max(values.length - 1, 1)) * (width - padding * 2);
        const y = height - padding - ((value - min) / valueRange) * (height - padding * 2);
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
  }

  private async loadWatchlists(): Promise<void> {
    const watchlists = await this.tradeApi.watchlists.listWatchlists();
    this.watchlists.set(watchlists.slice(0, 5));
  }

  private toWatchlistMessage(error: unknown, fallback: string): string {
    if (error instanceof Error && error.message.trim().length > 0) {
      return error.message;
    }

    return fallback;
  }
}

type MarketSeriesResult = {
  closes: number[];
  candles: CandlePoint[];
};

type MarketPerformer = {
  id: string;
  symbol: string;
  price: number;
  changePct: number;
};

type CandlePoint = {
  open: number;
  high: number;
  low: number;
  close: number;
  timestamp: number;
};

type CoinGeckoMarketEntry = {
  id: string;
  symbol: string;
  current_price: number;
  price_change_percentage_24h: number;
};

type CoinGeckoOhlcResponse = Array<[number, number, number, number, number]>;
