import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { PositionResponse } from '../../generated/trade';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';

@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './positions.component.html',
  styleUrls: ['./positions.component.css']
})
export class PositionsComponent implements OnInit {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeApi = inject(TradeApiClientService);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly positions = signal<PositionResponse[]>([]);

  ngOnInit(): void {
    void this.loadPositions();
  }

  async refresh(): Promise<void> {
    await this.loadPositions();
  }

  private async loadPositions(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);

    try {
      const user = await this.authSession.loadCurrentUser();
      if (!user) {
        this.loadError.set('Your session expired. Please sign in again.');
        return;
      }

      const positions = await this.tradeApi.accounts.getPositions({ id: user.accountId });
      this.positions.set(positions);
    } catch {
      this.loadError.set('Unable to load positions right now. Check trade-api and try again.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
