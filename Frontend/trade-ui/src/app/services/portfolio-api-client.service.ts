import { Inject, Injectable } from '@angular/core';
import { BaseAPI, Configuration, HTTPQuery, Middleware } from '../generated/trade';
import { PLATFORM_AUTH_MIDDLEWARE } from './platform-auth-middleware.provider';
import type {
  MoversBasis,
  MoversResponse,
  PnlResponse,
  PortfolioSummary,
  PricedPosition
} from './portfolio.models';

function buildConfiguration(middleware: Middleware): Configuration {
  const host = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
  // Same host and port as TradeApiClientService: the portfolio routes live in the Trade REST API.
  return new Configuration({ basePath: `http://${host}:8081`, middleware: [middleware] });
}

/**
 * Hand-written client for portfolio-api.yaml. It reuses the generated runtime (BaseAPI) so the
 * token interceptor and ResponseError behave exactly as they do for the generated clients.
 * A non-2xx answer throws ResponseError; read error.response.status (503 means MKT-503).
 */
@Injectable({ providedIn: 'root' })
export class PortfolioApiClientService extends BaseAPI {
  constructor(@Inject(PLATFORM_AUTH_MIDDLEWARE) middleware: Middleware) {
    super(buildConfiguration(middleware));
  }

  getSummary(accountId: number): Promise<PortfolioSummary> {
    return this.get(`/api/v1/portfolio/${accountId}`);
  }

  getPositions(accountId: number): Promise<PricedPosition[]> {
    return this.get(`/api/v1/portfolio/${accountId}/positions`);
  }

  getPnl(accountId: number, opts: { from?: string; to?: string; bySymbol?: boolean } = {}): Promise<PnlResponse> {
    const query: HTTPQuery = {};
    if (opts.from) query['from'] = opts.from;
    if (opts.to) query['to'] = opts.to;
    if (opts.bySymbol) query['bySymbol'] = true;
    return this.get(`/api/v1/portfolio/${accountId}/pnl`, query);
  }

  /** Extension beyond the contract: top gainers and losers. */
  getMovers(accountId: number, basis: MoversBasis = 'total', limit = 5): Promise<MoversResponse> {
    return this.get(`/api/v1/portfolio/${accountId}/movers`, { basis, limit });
  }

  private async get<T>(path: string, query: HTTPQuery = {}): Promise<T> {
    const response = await this.request({ path, method: 'GET', headers: {}, query });
    return (await response.json()) as T;
  }
}
