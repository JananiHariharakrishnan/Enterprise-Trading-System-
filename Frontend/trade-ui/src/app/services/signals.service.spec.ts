import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SignalsService, TradeSignalResponse } from './signals.service';
import { TokenStorageService } from './token-storage.service';

describe('SignalsService', () => {
  let service: SignalsService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const signal: TradeSignalResponse = {
    symbol: 'AAPL',
    action: 'BUY',
    confidence: 0.82,
    reasoning: 'RSI recovered',
    generatedAt: '2026-01-01T00:00:00.000Z'
  };

  const baseUrl = () => {
    const host = window.location.hostname;
    return window.location.port !== '8081'
      ? `http://${host}:8081/api/v1/signals`
      : '/api/v1/signals';
  };

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue('token-1') };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        SignalsService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(SignalsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads every signal with a bearer token', () => {
    let rows: TradeSignalResponse[] | undefined;
    service.getAll().subscribe((value) => {
      rows = value;
    });

    const req = http.expectOne(baseUrl());
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    req.flush([signal]);
    expect(rows).toEqual([signal]);
  });

  it('encodes the symbol when loading or generating one signal', () => {
    service.getBySymbol('BRK/B').subscribe();
    service.generate('BRK/B').subscribe();

    const getReq = http.expectOne(`${baseUrl()}/BRK%2FB`);
    const postReq = http.expectOne(`${baseUrl()}/generate/BRK%2FB`);

    expect(getReq.request.method).toBe('GET');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({});
    getReq.flush(signal);
    postReq.flush(signal);
  });

  it('refreshes the default portfolio without a token', () => {
    tokenStorage.getAccessToken.mockReturnValue(null);

    service.refreshAll().subscribe();
    const req = http.expectOne(`${baseUrl()}/refresh`);

    expect(req.request.method).toBe('POST');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([signal]);
  });
});
