import { describe, it, expect } from 'vitest';
import { TradeApiClientService } from './trade-api-client.service';

describe('TradeApiClientService', () => {
  it('configures trade APIs with platform middleware and expected base path', () => {
    const middleware = { pre: async (ctx: any) => ({ url: ctx.url, init: ctx.init }) };
    const service = new TradeApiClientService(middleware as any);

    const accountsConfig = (service.accounts as any).configuration;
    const ordersConfig = (service.orders as any).configuration;

    expect(accountsConfig.basePath).toBe('http://localhost:8081');
    expect(accountsConfig.middleware).toEqual([middleware]);
    expect(ordersConfig).toBe(accountsConfig);
  });
});
