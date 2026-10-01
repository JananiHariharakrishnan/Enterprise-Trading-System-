import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import type { WatchlistSummaryResponse, WatchlistDetailResponse } from '../../generated/trade';

@Component({
  selector: 'app-watchlists',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './watchlists.component.html',
  styleUrls: ['./watchlists.component.css']
})
export class WatchlistsComponent implements OnInit {
  private readonly api = inject(TradeApiClientService);
  private readonly fb = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly watchlists = signal<WatchlistSummaryResponse[]>([]);
  readonly selectedWatchlist = signal<WatchlistDetailResponse | null>(null);
  readonly selectedInstrument = signal<any>(null);
  readonly isSavingWatchlist = signal(false);

  readonly createWatchlistForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(100)]]
  });

  readonly addInstrumentForm = this.fb.nonNullable.group({
    symbol: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(32)]]
  });

  ngOnInit(): void {
    void this.load();
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
    this.message.set(null);
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
      this.message.set('Watchlist created.');
    } catch {
      this.loadError.set('Unable to create watchlist right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async openWatchlist(watchlistId: number): Promise<void> {
    this.message.set(null);
    this.loadError.set(null);
    this.isLoading.set(true);
    try {
      const detail = await this.api.watchlists.getWatchlist({ watchlistId });
      // sort instruments by symbol for consistent display
      if (detail.instruments && Array.isArray(detail.instruments)) {
        detail.instruments.sort((a, b) => a.symbol.localeCompare(b.symbol));
      }
      this.selectedWatchlist.set(detail);
    } catch {
      this.loadError.set('Unable to load watchlist');
    } finally {
      this.isLoading.set(false);
    }
  }

  async addInstrument(): Promise<void> {
    const watchlist = this.selectedWatchlist();
    if (!watchlist) return;
    if (this.addInstrumentForm.invalid) {
      this.addInstrumentForm.markAllAsTouched();
      return;
    }

    this.isSavingWatchlist.set(true);
    try {
      const symbol = this.addInstrumentForm.getRawValue().symbol.trim().toUpperCase();
      await this.api.watchlists.addWatchlistInstrument({ watchlistId: watchlist.id, addWatchlistInstrumentRequest: { symbol } });
      await this.openWatchlist(watchlist.id);
      await this.load();
      this.addInstrumentForm.reset({ symbol: '' });
      this.message.set(`Added ${symbol} to ${watchlist.name}.`);
    } catch {
      this.loadError.set('Unable to add instrument right now.');
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
      await this.openWatchlist(watchlist.id);
      await this.load();
      this.message.set(`Removed ${symbol} from ${watchlist.name}.`);
    } catch {
      this.loadError.set('Unable to remove instrument right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }

  async deleteWatchlist(watchlistId: number): Promise<void> {
    this.message.set(null);
    this.loadError.set(null);
    if (!confirm('Delete this watchlist? This cannot be undone.')) return;
    this.isSavingWatchlist.set(true);
    try {
      await this.api.watchlists.deleteWatchlist({ watchlistId });
      this.selectedWatchlist.set(null);
      await this.load();
      this.message.set('Watchlist deleted.');
    } catch {
      this.loadError.set('Unable to delete watchlist right now.');
    } finally {
      this.isSavingWatchlist.set(false);
    }
  }
  
  formatPrice(price?: number | null): string {
    if (price == null) return 'N/A';
    return Math.abs(price) >= 1 ? price.toFixed(2) : price.toFixed(4);
  }
  
  getPriceClass(price?: number | null): string {
    return price != null ? 'price-available' : 'price-unavailable';
  }

  selectInstrument(instrument: any): void {
    this.selectedInstrument.set(this.selectedInstrument() === instrument ? null : instrument);
  }

  getChangeClass(change?: number | null): string {
    if (change == null) return 'neutral';
    return change > 0 ? 'positive' : change < 0 ? 'negative' : 'neutral';
  }

  formatChange(change?: number | null): string {
    if (change == null) return 'N/A';
    return change > 0 ? `+${change.toFixed(2)}` : change.toFixed(2);
  }
}
