import { Inject, Injectable } from '@angular/core';
import { AccountsApi, Configuration, OrdersApi, WatchlistsApi, MarketDataApi, PriceAlertsApi } from '../generated/trade';
import { PLATFORM_AUTH_MIDDLEWARE } from './platform-auth-middleware.provider';
import { Middleware } from '../generated/trade/runtime';

@Injectable({
  providedIn: 'root'
})
export class TradeApiClientService {
  readonly accounts: AccountsApi;
  readonly orders: OrdersApi;
  readonly watchlists: WatchlistsApi;
  readonly marketData: MarketDataApi;
  readonly priceAlerts: PriceAlertsApi;

  constructor(@Inject(PLATFORM_AUTH_MIDDLEWARE) platformAuthMiddleware: Middleware) {
    const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
    const configuration = new Configuration({
      basePath: `http://${baseHost}:8081`,
      middleware: [platformAuthMiddleware]
    });

    this.accounts = new AccountsApi(configuration);
    this.orders = new OrdersApi(configuration);
    this.watchlists = new WatchlistsApi(configuration);
    this.marketData = new MarketDataApi(configuration);
    this.priceAlerts = new PriceAlertsApi(configuration);
  }
}
