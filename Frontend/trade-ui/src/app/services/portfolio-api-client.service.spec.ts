import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PLATFORM_AUTH_MIDDLEWARE } from './platform-auth-middleware.provider';
import { PortfolioApiClientService } from './portfolio-api-client.service';

describe('PortfolioApiClientService', () => {
  let service: PortfolioApiClientService;
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    TestBed.configureTestingModule({
      providers: [
        PortfolioApiClientService,
        { provide: PLATFORM_AUTH_MIDDLEWARE, useValue: {} }
      ]
    });
    service = TestBed.inject(PortfolioApiClientService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function ok(body: unknown): Response {
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }

  it('loads the portfolio summary and positions', async () => {
    fetchMock.mockResolvedValueOnce(ok({ cashBalance: 10 }));
    await expect(service.getSummary(4)).resolves.toEqual({ cashBalance: 10 });
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/v1/portfolio/4');

    fetchMock.mockResolvedValueOnce(ok([]));
    await expect(service.getPositions(4)).resolves.toEqual([]);
    expect(String(fetchMock.mock.calls[1][0])).toContain('/positions');
  });

  it('adds pnl and movers query parameters only when they are set', async () => {
    fetchMock.mockResolvedValueOnce(ok({ totalPnl: 1 }));
    await service.getPnl(4);
    expect(String(fetchMock.mock.calls[0][0])).not.toContain('?');

    fetchMock.mockResolvedValueOnce(ok({ totalPnl: 2 }));
    await service.getPnl(4, { from: '2026-01-01', to: '2026-02-01', bySymbol: true });
    const pnlUrl = String(fetchMock.mock.calls[1][0]);
    expect(pnlUrl).toContain('from=2026-01-01');
    expect(pnlUrl).toContain('to=2026-02-01');
    expect(pnlUrl).toContain('bySymbol=true');

    fetchMock.mockResolvedValueOnce(ok({ gainers: [] }));
    await service.getMovers(4, 'day', 3);
    const moversUrl = String(fetchMock.mock.calls[2][0]);
    expect(moversUrl).toContain('basis=day');
    expect(moversUrl).toContain('limit=3');
  });
});
