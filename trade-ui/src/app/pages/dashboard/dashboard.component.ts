import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  AccountResponse,
  BalanceResponse,
  OrderHistoryEntry,
  PositionResponse,
  WatchlistDetailResponse,
  WatchlistSummaryResponse,
} from '../../generated/trade';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { UiEventsService } from '../../services/ui-events.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, ReactiveFormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  readonly authSession = inject(AuthSessionService);
  private readonly tradeApi = inject(TradeApiClientService);
  private readonly uiEvents = inject(UiEventsService);
  private readonly formBuilder = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly account = signal<AccountResponse | null>(null);
  readonly balance = signal<BalanceResponse | null>(null);
  readonly positions = signal<PositionResponse[]>([]);
  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly watchlists = signal<WatchlistSummaryResponse[]>([]);
  readonly selectedWatchlist = signal<WatchlistDetailResponse | null>(null);
  readonly watchlistMessage = signal<string | null>(null);
  readonly watchlistError = signal<string | null>(null);
  readonly isLoadingWatchlist = signal(false);
  readonly isSavingWatchlist = signal(false);

  readonly createWatchlistForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]]
  });

  readonly addInstrumentForm = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required, Validators.maxLength(32)]]
  });

  ngOnInit(): void {
    void this.loadDashboard();
    this.uiEvents.onRefresh().subscribe(() => void this.refresh());
  }

  async refresh(): Promise<void> {
    await this.loadDashboard();
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
      }

      this.watchlistMessage.set('Watchlist deleted.');
    } catch (error) {
      this.watchlistError.set(this.toWatchlistMessage(error, 'Unable to delete watchlist right now.'));
    } finally {
      this.isSavingWatchlist.set(false);
    }
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
    } catch {
      this.loadError.set('Unable to load dashboard data right now. Check trade-api and try again.');
    } finally {
      this.isLoading.set(false);
    }
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
