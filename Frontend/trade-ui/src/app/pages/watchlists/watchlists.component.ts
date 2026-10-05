import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import type { WatchlistSummaryResponse, WatchlistDetailResponse } from '../../generated/trade';
import { InstrumentSearchComponent, type InstrumentListItem } from '../../components/instrument-search/instrument-search.component';

type WatchlistInstrument = NonNullable<WatchlistDetailResponse['instruments']>[number];
type TradeSide = 'buy' | 'sell';
type ToastType = 'success' | 'error';

interface Toast {
  id: number;
  type: ToastType;
  text: string;
}

/** How long a toast stays on screen (no countdown is shown). */
const TOAST_SUCCESS_MS = 3500;
const TOAST_ERROR_MS = 5000;
/** Newest toasts win if several pile up. */
const TOAST_MAX_VISIBLE = 3;

/** Number of colour themes the watchlist cards cycle through (see .tone-N in the CSS). */
const TONE_COUNT = 6;

@Component({
  selector: 'app-watchlists',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, InstrumentSearchComponent],
  templateUrl: './watchlists.component.html',
  styleUrls: ['./watchlists.component.css']
})
export class WatchlistsComponent implements OnInit, OnDestroy {
  private readonly api = inject(TradeApiClientService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly toasts = signal<Toast[]>([]);
  private readonly toastTimers = new Map<number, ReturnType<typeof setTimeout>>();
  private toastSeq = 0;
  readonly watchlists = signal<WatchlistSummaryResponse[]>([]);
  readonly selectedWatchlist = signal<WatchlistDetailResponse | null>(null);
  readonly isSavingWatchlist = signal(false);
  readonly isRefreshing = signal(false);
  readonly lastRefreshedAt = signal<Date | null>(null);

  /**
   * The popup is driven by the symbol, not the object, so it keeps working
   * after the watchlist is reloaded from the API (new objects, same symbol).
   */
  readonly selectedSymbol = signal<string | null>(null);
  readonly selectedInstrument = computed<WatchlistInstrument | null>(() => {
    const symbol = this.selectedSymbol();
    if (!symbol) return null;
    return this.selectedWatchlist()?.instruments?.find((i) => i.symbol === symbol) ?? null;
  });

  readonly existingSymbols = computed(() =>
    (this.selectedWatchlist()?.instruments ?? []).map((i) => i.symbol)
  );

  /** Performance summary for the open watchlist, shown inside the watchlist card. */
  readonly watchlistStats = computed(() => {
    const instruments = this.selectedWatchlist()?.instruments ?? [];

    const moves = instruments.flatMap((i) => {
      const pct = i.latestPrice?.changePercent;
      return pct == null ? [] : [{ symbol: i.symbol, pct }];
    });

    const gainers = moves.filter((m) => m.pct > 0).length;
    const losers = moves.filter((m) => m.pct < 0).length;
    const flat = moves.length - gainers - losers;
    const pricedTotal = moves.length;

    return {
      total: instruments.length,
      gainers,
      losers,
      flat,
      pricedTotal,
      gainersPct: pricedTotal ? (gainers / pricedTotal) * 100 : 0,
      flatPct: pricedTotal ? (flat / pricedTotal) * 100 : 0,
      losersPct: pricedTotal ? (losers / pricedTotal) * 100 : 0,
      avg: pricedTotal ? moves.reduce((sum, m) => sum + m.pct, 0) / pricedTotal : null,
      best: pricedTotal ? moves.reduce((a, b) => (b.pct > a.pct ? b : a)) : null,
      worst: pricedTotal ? moves.reduce((a, b) => (b.pct < a.pct ? b : a)) : null,
      stale: instruments.filter((i) => i.latestPrice?.stale).length
    };
  });

  @ViewChild(InstrumentSearchComponent) instrumentSearchComponent?: InstrumentSearchComponent;

  readonly createWatchlistForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(100)]]
  });

  ngOnInit(): void {
    void this.load();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.selectedSymbol()) {
      this.selectedSymbol.set(null);
    }
  }

  ngOnDestroy(): void {
    this.toastTimers.forEach((timer) => clearTimeout(timer));
    this.toastTimers.clear();
  }

  // ---------- Toasts ----------

  private showToast(type: ToastType, text: string): void {
    const id = ++this.toastSeq;
    this.toasts.update((list) => [...list.slice(-(TOAST_MAX_VISIBLE - 1)), { id, type, text }]);
    this.toastTimers.set(
      id,
      setTimeout(() => this.dismissToast(id), type === 'error' ? TOAST_ERROR_MS : TOAST_SUCCESS_MS)
    );
  }

  dismissToast(id: number): void {
    const timer = this.toastTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.toastTimers.delete(id);
    }
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private async load(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);
    try {
      const items = await this.api.watchlists.listWatchlists();
      // Sort by creation date (newest first) for consistent display
      this.watchlists.set(items.slice(0, 50));
    } catch (error) {
      this.loadError.set('Unable to load watchlists. Please refresh and try again.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async createWatchlist(): Promise<void> {
    this.loadError.set(null);

    if (this.createWatchlistForm.invalid) {
      this.createWatchlistForm.markAllAsTouched();
      return;
    }

    this.isSavingWatchlist.set(true);
    try {
      const name = this.createWatchlistForm.getRawValue().name.trim();
      const created = await this.api.watchlists.createWatchlist({ createWatchlistRequest: { name } });
      await this.load();
      await this.openWatchlist(created.id);
      this.createWatchlistForm.reset({ name: '' });
      this.showToast('success', 'Watchlist created.');
    } catch {
      this.showToast('error', 'Unable to create watchlist right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async openWatchlist(watchlistId: number): Promise<void> {
    this.loadError.set(null);
    this.isLoading.set(true);
    try {
      const detail = await this.api.watchlists.getWatchlist({ watchlistId });
      // sort instruments by symbol for consistent display
      if (detail.instruments && Array.isArray(detail.instruments)) {
        detail.instruments.sort((a, b) => a.symbol.localeCompare(b.symbol));
      }
      // Switching to a different watchlist closes any open instrument popup
      if (this.selectedWatchlist()?.id !== watchlistId) {
        this.selectedSymbol.set(null);
      }
      this.selectedWatchlist.set(detail);
      this.lastRefreshedAt.set(new Date());
    } catch {
      this.showToast('error', 'Unable to load watchlist');
    } finally {
      this.isLoading.set(false);
    }
  }

  /** Re-fetches the open watchlist so prices update without reloading the page. */
  async refreshWatchlist(): Promise<void> {
    const current = this.selectedWatchlist();
    if (!current || this.isRefreshing()) return;

    this.isRefreshing.set(true);
    try {
      const detail = await this.api.watchlists.getWatchlist({ watchlistId: current.id });
      detail.instruments?.sort((a, b) => a.symbol.localeCompare(b.symbol));
      // The user may have opened a different watchlist while this was loading
      if (this.selectedWatchlist()?.id === current.id) {
        this.selectedWatchlist.set(detail);
        this.lastRefreshedAt.set(new Date());
      }
    } catch {
      this.showToast('error', 'Unable to refresh prices right now.');
    } finally {
      this.isRefreshing.set(false);
    }
  }

  async addInstrument(instrument: InstrumentListItem): Promise<void> {
    const watchlist = this.selectedWatchlist();
    if (!watchlist) return;

    this.isSavingWatchlist.set(true);
    try {
      const symbol = instrument.symbol.trim().toUpperCase();
      await this.api.watchlists.addWatchlistInstrument({ watchlistId: watchlist.id, addWatchlistInstrumentRequest: { symbol } });
      await this.openWatchlist(watchlist.id);
      await this.load();
      this.instrumentSearchComponent?.resetSearch();
      this.showToast('success', `Added ${symbol} to ${watchlist.name}.`);
    } catch {
      this.showToast('error', 'Unable to add instrument right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async removeInstrument(symbol: string): Promise<void> {
    const watchlist = this.selectedWatchlist();
    if (!watchlist) return;
    this.isSavingWatchlist.set(true);
    try {
      await this.api.watchlists.removeWatchlistInstrument({ watchlistId: watchlist.id, symbol });
      if (this.selectedSymbol() === symbol) {
        this.selectedSymbol.set(null);
      }
      await this.openWatchlist(watchlist.id);
      await this.load();
      this.showToast('success', `Removed ${symbol} from ${watchlist.name}.`);
    } catch {
      this.showToast('error', 'Unable to remove instrument right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async deleteWatchlist(watchlistId: number): Promise<void> {
    this.loadError.set(null);
    if (!confirm('Delete this watchlist? This cannot be undone.')) return;
    this.isSavingWatchlist.set(true);
    try {
      await this.api.watchlists.deleteWatchlist({ watchlistId });
      this.selectedSymbol.set(null);
      this.selectedWatchlist.set(null);
      await this.load();
      this.showToast('success', 'Watchlist deleted.');
    } catch {
      this.showToast('error', 'Unable to delete watchlist right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  /** Opens the instrument popup (click the same instrument again to close it). */
  selectInstrument(instrument: WatchlistInstrument | null): void {
    const symbol = instrument?.symbol ?? null;
    this.selectedSymbol.set(symbol && symbol !== this.selectedSymbol() ? symbol : null);
  }

  /** Sends the user to the orders page with the instrument and side prefilled in the URL. */
  trade(instrument: WatchlistInstrument, side: TradeSide): void {
    void this.router.navigate(['/orders'], {
      queryParams: { symbol: instrument.symbol, side }
    });
  }

  // ---------- Formatting helpers ----------

  formatPrice(price?: number | null): string {
    if (price == null) return 'N/A';
    return Math.abs(price) >= 1 ? price.toFixed(2) : price.toFixed(4);
  }

  getPriceClass(price?: number | null): string {
    return price != null ? 'price-available' : 'price-unavailable';
  }

  getChangeClass(change?: number | null): string {
    if (change == null) return 'neutral';
    return change > 0 ? 'positive' : change < 0 ? 'negative' : 'neutral';
  }

  changeArrow(change?: number | null): string {
    if (change == null || change === 0) return '–';
    return change > 0 ? '▲' : '▼';
  }

  formatChange(change?: number | null): string {
    if (change == null) return 'N/A';
    return change > 0 ? `+${change.toFixed(2)}` : change.toFixed(2);
  }

  formatPercent(percent?: number | null): string {
    if (percent == null) return 'N/A';
    return `${percent > 0 ? '+' : ''}${percent.toFixed(2)}%`;
  }

  // ---------- Visual helpers ----------

  /** Stable colour theme per watchlist, so each one keeps its colour between visits. */
  toneClass(id: number): string {
    return `tone-${Math.abs(id) % TONE_COUNT}`;
  }

  watchlistInitial(name: string): string {
    return name.trim().charAt(0).toUpperCase() || '?';
  }

  /** Stable hue (0-359) per symbol, used to tint the symbol badges. */
  symbolHue(symbol: string): number {
    let hash = 0;
    for (const char of symbol) {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }

  trackById(_: number, item: { id: number }): number {
    return item.id;
  }

  trackBySymbol(_: number, item: { symbol: string }): string {
    return item.symbol;
  }
}
