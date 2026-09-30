import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountResponse, BalanceResponse, OrderHistoryEntry, PositionResponse } from '../../generated/trade';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { UiEventsService } from '../../services/ui-events.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {
  readonly authSession = inject(AuthSessionService);
  private readonly tradeApi = inject(TradeApiClientService);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly account = signal<AccountResponse | null>(null);
  readonly balance = signal<BalanceResponse | null>(null);
  readonly positions = signal<PositionResponse[]>([]);
  readonly orders = signal<OrderHistoryEntry[]>([]);

  async ngOnInit(): Promise<void> {
    await this.loadDashboard();
    // subscribe to global UI refresh events
    const uiEvents = inject(UiEventsService);
    uiEvents.onRefresh().subscribe(() => void this.refresh());
  }

  async refresh(): Promise<void> {
    await this.loadDashboard();
  }

  logout(): void {
    void this.authSession.logout();
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
    } catch {
      this.loadError.set('Unable to load dashboard data right now. Check trade-api and try again.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
