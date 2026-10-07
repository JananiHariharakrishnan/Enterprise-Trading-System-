// Types for portfolio-api.yaml (and the /movers extension). Field names match the contract exactly.

export interface PortfolioSummary {
  accountId: number;
  baseCurrency: string;
  cashBalance: number;
  marketValue: number;
  costBasis: number;
  unrealisedPnl: number;
  unrealisedPnlPercent: number | null;
  realisedPnl: number;
  totalValue: number;
  positionCount: number;
  /** True when at least one holding could not be priced. */
  partial: boolean;
  asOf: string;
}

export interface PricedPosition {
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
  costBasis: number;
  lastPrice: number | null;
  marketValue: number | null;
  unrealisedPnl: number | null;
  unrealisedPnlPercent: number | null;
  /** The instrument's own currency. */
  currency: string;
  priceAsOf: string | null;
  stale: boolean;
}

export interface SymbolPnl {
  symbol: string;
  realisedPnl: number;
  unrealisedPnl: number;
  totalPnl: number;
}

export interface PnlResponse {
  accountId: number;
  baseCurrency: string;
  from: string | null;
  to: string | null;
  realisedPnl: number;
  unrealisedPnl: number;
  totalPnl: number;
  bySymbol?: SymbolPnl[] | null;
  asOf: string;
}

export type MoversBasis = 'total' | 'day';

export interface MoverEntry {
  symbol: string;
  /** In the base currency. */
  pnl: number;
  pnlPercent: number | null;
  stale: boolean;
}

export interface MoversResponse {
  accountId: number;
  baseCurrency: string;
  basis: MoversBasis;
  gainers: MoverEntry[];
  losers: MoverEntry[];
  partial: boolean;
  asOf: string;
}
