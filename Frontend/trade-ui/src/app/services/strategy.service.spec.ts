import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrategyResponse, StrategyService } from './strategy.service';
import { TokenStorageService } from './token-storage.service';

describe('StrategyService', () => {
  let service: StrategyService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const strategy: StrategyResponse = {
    strategyId: 'strat-1',
    accountId: 9,
    symbol: 'AAPL',
    type: 'MA_CROSSOVER',
    quantity: 10,
    enabled: true,
    currentSpend: 0,
    maxSpend: 50000,
    maxPositionSize: 200,
    consecutiveFailures: 0,
    maxConsecutiveFailures: 3
  };

  const baseUrl = () => `http://${window.location.hostname}:8081/api/v1/strategies`;

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue('token-1') };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        StrategyService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(StrategyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('lists strategies with json and bearer headers', () => {
    let rows: StrategyResponse[] | undefined;
    service.getStrategies().subscribe((value) => {
      rows = value;
    });

    const req = http.expectOne(baseUrl());
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    expect(req.request.headers.get('Content-Type')).toBe('application/json');
    req.flush([strategy]);
    expect(rows).toEqual([strategy]);
  });

  it('creates, enables, disables, deletes, and simulates a tick', () => {
    const request = {
      symbol: 'MSFT',
      type: 'BOLLINGER_BANDS' as const,
      quantity: 4,
      maxSpend: 1000,
      maxPositionSize: 20
    };

    service.createStrategy(request).subscribe();
    service.enableStrategy('strat-1').subscribe();
    service.disableStrategy('strat-1').subscribe();
    service.deleteStrategy('strat-1').subscribe();
    service.simulateTick('msft', 410.5).subscribe();

    const createReq = http.expectOne(baseUrl());
    const enableReq = http.expectOne(`${baseUrl()}/strat-1/enable`);
    const disableReq = http.expectOne(`${baseUrl()}/strat-1/disable`);
    const deleteReq = http.expectOne(`${baseUrl()}/strat-1`);
    const tickReq = http.expectOne(`${baseUrl()}/simulate-tick`);

    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body).toEqual(request);
    expect(enableReq.request.method).toBe('POST');
    expect(enableReq.request.body).toEqual({});
    expect(disableReq.request.method).toBe('POST');
    expect(deleteReq.request.method).toBe('DELETE');
    expect(tickReq.request.method).toBe('POST');
    expect(tickReq.request.body).toEqual({ symbol: 'msft', price: 410.5 });

    createReq.flush(strategy);
    enableReq.flush(strategy);
    disableReq.flush({ ...strategy, enabled: false });
    deleteReq.flush(null);
    tickReq.flush({ results: ['BUY MSFT'] });
  });

  it('still sends the json content type when no token is stored', () => {
    tokenStorage.getAccessToken.mockReturnValue(null);

    service.getStrategies().subscribe();
    const req = http.expectOne(baseUrl());

    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.headers.get('Content-Type')).toBe('application/json');
    req.flush([]);
  });
});
