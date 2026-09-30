import { Inject, Injectable } from '@angular/core';
import { AccountsApi, Configuration, OrdersApi } from '../generated/trade';
import { PLATFORM_AUTH_MIDDLEWARE } from './platform-auth-middleware.provider';
import { Middleware } from '../generated/trade/runtime';

@Injectable({
  providedIn: 'root'
})
export class TradeApiClientService {
  readonly accounts: AccountsApi;
  readonly orders: OrdersApi;

  constructor(@Inject(PLATFORM_AUTH_MIDDLEWARE) platformAuthMiddleware: Middleware) {
    const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
    const configuration = new Configuration({
      basePath: `http://${baseHost}:8888`,
      middleware: [platformAuthMiddleware]
    });

    this.accounts = new AccountsApi(configuration);
    this.orders = new OrdersApi(configuration);
  }
}
