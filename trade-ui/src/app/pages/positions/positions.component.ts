import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PositionResponse } from '../../generated/trade';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';

type SortKey = 'symbol' | 'quantity' | 'averageCost' | 'value' | 'pnl' | 'weight';

interface PositionRow {
  symbol: string;
  quantity: number;
  averageCost: number;
  value: number;            // cost basis = quantity x average cost
  weight: number;           // share of total cost basis (0..1)
  pnl: number | null;       // unrealized P&L, only when the API provides price/P&L
  pnlPct: number | null;    // pnl / cost basis
}

interface Column { key: SortKey; label: string; numeric: boolean; }

// Gains / neutral = greens, losses = soft red shades
const GAIN_COLORS = ['#1f7a4d', '#2fa56a', '#6cc79a', '#a5dfc2', '#cfeedd'];
const LOSS_COLORS = ['#d4575d', '#e58a8f', '#f0b4b7', '#f6d3d5', '#fae8e9'];
const OTHER_COLOR = '#c9d1cd';

@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './positions.component.html',
  styleUrls: ['./positions.component.css']
})
export class PositionsComponent implements OnInit {
  private readonly authSession = inject(AuthSessionService);
  private readonly tradeApi = inject(TradeApiClientService);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly positions = signal<PositionResponse[]>([]);
  readonly lastUpdated = signal<Date | null>(null);

  readonly search = signal('');
  readonly sortKey = signal<SortKey>('value');
  readonly sortDir = signal<'asc' | 'desc'>('desc');

  /** Every position with cost basis, weight and (when available) P&L */
  readonly rows = computed<PositionRow[]>(() => {
    const base = this.positions().map(p => {
      const a = p as any;
      const quantity = Number(p.quantity) || 0;
      const averageCost = Number(p.averageCost) || 0;
      const value = quantity * averageCost;
      // P&L is only known if the API sends a price or a P&L figure
      const price = this.num(a.currentPrice ?? a.marketPrice ?? a.lastPrice);
      const pnl = this.num(a.unrealizedPnl ?? a.unrealizedPnL) ?? (price !== null ? (price - averageCost) * quantity : null);
      return { symbol: String(p.symbol ?? ''), quantity, averageCost, value, pnl, pnlPct: pnl !== null && value > 0 ? pnl / value : null };
    });
    const total = base.reduce((sum, r) => sum + r.value, 0);
    return base.map(r => ({ ...r, weight: total > 0 ? r.value / total : 0 }));
  });

  // ---- basic analysis (works from cost data alone) ----
  readonly totalValue = computed(() => this.rows().reduce((s, r) => s + r.value, 0));
  readonly totalShares = computed(() => this.rows().reduce((s, r) => s + r.quantity, 0));
  readonly largest = computed<PositionRow | null>(() => [...this.rows()].sort((a, b) => b.value - a.value)[0] ?? null);
  readonly smallest = computed<PositionRow | null>(() => [...this.rows()].sort((a, b) => a.value - b.value)[0] ?? null);
  readonly avgCostPerShare = computed(() => (this.totalShares() > 0 ? this.totalValue() / this.totalShares() : 0));

  // ---- performance (only when price / P&L data exists) ----
  readonly hasPnl = computed(() => this.rows().length > 0 && this.rows().every(r => r.pnl !== null));
  readonly totalPnl = computed(() => this.rows().reduce((s, r) => s + (r.pnl ?? 0), 0));
  readonly totalPnlPct = computed(() => (this.totalValue() > 0 ? this.totalPnl() / this.totalValue() : 0));
  readonly anyLoss = computed(() => this.rows().some(r => (r.pnl ?? 0) < 0));

  readonly columns = computed<Column[]>(() => {
    const cols: Column[] = [
      { key: 'symbol', label: 'Symbol', numeric: false },
      { key: 'quantity', label: 'Quantity', numeric: true },
      { key: 'averageCost', label: 'Average cost', numeric: true },
      { key: 'value', label: 'Cost basis', numeric: true },
    ];
    if (this.hasPnl()) cols.push({ key: 'pnl', label: 'P&L', numeric: true });
    cols.push({ key: 'weight', label: 'Allocation', numeric: false });
    return cols;
  });

  /** Rows after search + sort, used by the table */
  readonly visibleRows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const key = this.sortKey();
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    return this.rows()
      .filter(r => !q || r.symbol.toLowerCase().includes(q))
      .sort((a, b) => {
        const av: any = a[key] ?? 0, bv: any = b[key] ?? 0;
        return (typeof av === 'string' ? av.localeCompare(bv) : av - bv) * dir;
      });
  });

  /** Top 5 holdings + "Other". Positions at a loss get red shades. */
  readonly segments = computed(() => {
    const sorted = [...this.rows()].filter(r => r.weight > 0).sort((a, b) => b.weight - a.weight);
    let g = 0, l = 0;
    const top = sorted.slice(0, 5).map(r => {
      const loss = (r.pnl ?? 0) < 0;
      return { label: r.symbol, weight: r.weight, loss, color: loss ? LOSS_COLORS[l++] : GAIN_COLORS[g++] };
    });
    const rest = sorted.slice(5).reduce((s, r) => s + r.weight, 0);
    if (rest > 0) top.push({ label: 'Other', weight: rest, loss: false, color: OTHER_COLOR });
    return top;
  });

  ngOnInit(): void {
    void this.loadPositions();
  }

  async refresh(): Promise<void> {
    await this.loadPositions();
  }

  setSort(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set(key === 'symbol' ? 'asc' : 'desc');
    }
  }

  ariaSort(key: SortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey() !== key) return 'none';
    return this.sortDir() === 'asc' ? 'ascending' : 'descending';
  }

  onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  private num(v: unknown): number | null {
    if (v === null || v === undefined || v === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
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
      this.lastUpdated.set(new Date());
    } catch {
      this.loadError.set('Unable to load positions right now. Check trade-api and try again.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
