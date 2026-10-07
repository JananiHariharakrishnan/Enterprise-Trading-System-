import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ResponseError } from '../../generated/trade';
import { AuthSessionService } from '../../services/auth-session.service';
import { PortfolioApiClientService } from '../../services/portfolio-api-client.service';
import type {
  MoversBasis,
  MoversResponse,
  PnlResponse,
  PortfolioSummary,
  PricedPosition
} from '../../services/portfolio.models';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { quoteCurrency } from '../../utils/currency.util';

type Tone = 'gain' | 'loss' | 'flat';

@Component({
  selector: 'app-pnl',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pnl.component.html',
  styleUrls: ['./pnl.component.css']
})
export class PnlComponent implements OnInit {
  private readonly authSession = inject(AuthSessionService);
  private readonly portfolio = inject(PortfolioApiClientService);
  private readonly tradeApi = inject(TradeApiClientService);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  /** True when the contract answered 503 / MKT-503: show unpriced holdings instead of nothing. */
  readonly pricingDown = signal(false);

  readonly summary = signal<PortfolioSummary | null>(null);
  readonly positions = signal<PricedPosition[]>([]);
  readonly pnl = signal<PnlResponse | null>(null);
  readonly movers = signal<MoversResponse | null>(null);
  readonly moversBasis = signal<MoversBasis>('total');
  readonly lastUpdated = signal<Date | null>(null);

  // Realised-P&L date range (the contract applies it to the realised figure only)
  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly rangeError = signal<string | null>(null);

  private accountId: number | null = null;

  readonly baseCurrency = computed(() => this.summary()?.baseCurrency ?? 'USD');

  readonly staleSymbols = computed(() =>
    this.positions().filter(p => p.stale && p.lastPrice != null).map(p => p.symbol));
  readonly unpricedSymbols = computed(() =>
    this.positions().filter(p => p.lastPrice == null).map(p => p.symbol));

  /** Show the marker whenever any figure on screen is not live. */
  readonly showStaleBanner = computed(() =>
    !this.pricingDown() && (this.staleSymbols().length > 0 || this.unpricedSymbols().length > 0));

  readonly totalPnl = computed(() => {
    const s = this.summary();
    return s ? s.realisedPnl + s.unrealisedPnl : null;
  });

  ngOnInit(): void {
    void this.load();
  }

  async refresh(): Promise<void> {
    await this.load();
  }

  async setBasis(basis: MoversBasis): Promise<void> {
    this.moversBasis.set(basis);
    await this.loadMovers();
  }

  async onFromChange(event: Event): Promise<void> {
    this.fromDate.set((event.target as HTMLInputElement).value);
    await this.applyRange();
  }

  async onToChange(event: Event): Promise<void> {
    this.toDate.set((event.target as HTMLInputElement).value);
    await this.applyRange();
  }

  async clearRange(): Promise<void> {
    this.fromDate.set('');
    this.toDate.set('');
    await this.applyRange();
  }

  // ---------- loading ----------

  private async load(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.pricingDown.set(false);

    try {
      const user = await this.authSession.loadCurrentUser();
      if (!user) {
        this.loadError.set('Your session expired. Please sign in again.');
        return;
      }
      const id = user.accountId;
      this.accountId = id;

      const [summary, positions, pnl] = await Promise.all([
        this.portfolio.getSummary(id),
        this.portfolio.getPositions(id),
        this.portfolio.getPnl(id, this.pnlOptions())
      ]);
      this.summary.set(summary);
      this.positions.set(positions);
      this.pnl.set(pnl);
      await this.loadMovers();
      this.lastUpdated.set(new Date());
    } catch (error) {
      await this.handleLoadError(error);
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadMovers(): Promise<void> {
    if (this.accountId == null) return;
    try {
      this.movers.set(await this.portfolio.getMovers(this.accountId, this.moversBasis(), 5));
    } catch {
      this.movers.set(null); // an extension: never let it break the page
    }
  }

  private async applyRange(): Promise<void> {
    const from = this.fromDate();
    const to = this.toDate();
    if (from && to && from > to) {
      this.rangeError.set('"From" must not be after "To".');
      return;
    }
    this.rangeError.set(null);
    if (this.accountId == null) return;
    try {
      this.pnl.set(await this.portfolio.getPnl(this.accountId, this.pnlOptions()));
    } catch (error) {
      await this.handleLoadError(error);
    }
  }

  private pnlOptions() {
    return { bySymbol: true, from: this.fromDate() || undefined, to: this.toDate() || undefined };
  }

  private async handleLoadError(error: unknown): Promise<void> {
    const status = error instanceof ResponseError ? error.response.status : null;

    if (status === 503) {
      await this.degradeToUnpriced();
    } else if (status === 401) {
      this.loadError.set('Your session expired. Please sign in again.');
    } else if (status === 403) {
      this.loadError.set('You do not have access to this portfolio.');
    } else {
      this.loadError.set('Unable to load your portfolio right now. Check trade-api and try again.');
    }
  }

  /** MKT-503: no price for any holding. Keep the holdings and cost basis visible, unpriced. */
  private async degradeToUnpriced(): Promise<void> {
    this.pricingDown.set(true);
    this.summary.set(null);
    this.pnl.set(null);
    this.movers.set(null);
    try {
      if (this.accountId == null) return;
      const plain = await this.tradeApi.accounts.getPositions({ id: this.accountId });
      this.positions.set(plain.map(p => {
        const quantity = Number(p.quantity) || 0;
        const averageCost = Number(p.averageCost) || 0;
        return {
          accountId: this.accountId as number,
          symbol: String(p.symbol ?? ''),
          quantity,
          averageCost,
          costBasis: quantity * averageCost,
          lastPrice: null,
          marketValue: null,
          unrealisedPnl: null,
          unrealisedPnlPercent: null,
          currency: quoteCurrency(String(p.symbol ?? '')),
          priceAsOf: null,
          stale: true
        } satisfies PricedPosition;
      }));
      this.lastUpdated.set(new Date());
    } catch {
      this.loadError.set('Unable to load your holdings right now.');
    }
  }

  // ---------- formatting helpers used by the template ----------

  money(value: number | null | undefined, currency = 'USD'): string {
    if (value == null) return '—';
    try {
      return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(value);
    } catch {
      return value.toFixed(2);
    }
  }

  signedMoney(value: number | null | undefined, currency = 'USD'): string {
    if (value == null) return '—';
    return (value > 0 ? '+' : '') + this.money(value, currency);
  }

  pct(value: number | null | undefined): string {
    if (value == null) return '—';
    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
  }

  tone(value: number | null | undefined): Tone {
    if (value == null || value === 0) return 'flat';
    return value > 0 ? 'gain' : 'loss';
  }

  /** "2 min ago" for a price timestamp. */
  ago(iso: string | null | undefined): string {
    if (!iso) return 'no price';
    const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (seconds < 90) return `${seconds}s ago`;
    const minutes = Math.round(seconds / 60);
    if (minutes < 90) return `${minutes} min ago`;
    const hours = Math.round(minutes / 60);
    if (hours < 36) return `${hours} h ago`;
    return `${Math.round(hours / 24)} d ago`;
  }
}
