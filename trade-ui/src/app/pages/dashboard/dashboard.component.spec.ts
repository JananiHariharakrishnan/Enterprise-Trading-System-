import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';

class AuthSessionServiceStub {
  currentUser = vi.fn(() => ({
    id: 'u-1',
    username: 'priya',
    accountId: 1
  }));

  loadCurrentUser = vi.fn().mockResolvedValue({
    id: 'u-1',
    username: 'priya',
    accountId: 1
  });
}

class TradeApiClientServiceStub {
  accounts = {
    getAccount: vi.fn().mockResolvedValue({
      id: 1,
      accountId: 'ACC-001',
      holderName: 'Priya Menon',
      cashBalance: 12500,
      status: 'ACTIVE',
      version: 1,
      lastUpdated: new Date('2026-01-01T09:00:00Z')
    }),

    getBalance: vi.fn().mockResolvedValue({
      accountId: 1,
      cashBalance: 12500,
      currency: 'USD',
      asOf: new Date('2026-01-01T09:00:00Z')
    }),

    getPositions: vi.fn().mockResolvedValue([
      {
        accountId: 1,
        symbol: 'AAPL',
        quantity: 10,
        averageCost: 192.5
      }
    ]),

    getOrders: vi.fn().mockResolvedValue([
      {
        orderId: 'ORD-1',
        accountId: 1,
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 10,
        price: 192.5,
        status: 'FILLED',
        createdOn: new Date('2026-01-01T08:00:00Z')
      }
    ])
  };

  // add watchlists stub data
  watchlists = {
    listWatchlists: vi.fn().mockResolvedValue([
      { id: 11, name: 'Tech', entryCount: 3, createdOn: new Date(), updatedOn: new Date() }
    ]),
    getWatchlist: vi.fn().mockResolvedValue({
      id: 11,
      name: 'Tech',
      entryCount: 1,
      createdOn: new Date('2026-01-01T09:00:00Z'),
      updatedOn: new Date('2026-01-01T09:00:00Z'),
      instruments: [
        {
          symbol: 'AAPL',
          addedOn: new Date('2026-01-01T09:00:00Z'),
          latestPrice: { symbol: 'AAPL', price: 232.71, currency: 'USD', marketState: 'OPEN', quotedAt: new Date('2026-01-01T09:00:00Z') }
        }
      ]
    }),
    createWatchlist: vi.fn().mockResolvedValue({
      id: 12,
      name: 'Dividend',
      entryCount: 0,
      createdOn: new Date('2026-01-01T09:00:00Z'),
      updatedOn: new Date('2026-01-01T09:00:00Z')
    }),
    addWatchlistInstrument: vi.fn().mockResolvedValue({
      symbol: 'MSFT',
      addedOn: new Date('2026-01-01T09:00:00Z'),
      latestPrice: null
    }),
    removeWatchlistInstrument: vi.fn().mockResolvedValue(undefined),
    deleteWatchlist: vi.fn().mockResolvedValue(undefined)
  };
}

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let authSession: AuthSessionServiceStub;
  let tradeApi: TradeApiClientServiceStub;
  let httpGet: ReturnType<typeof vi.fn>;

  const settleView = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    authSession = new AuthSessionServiceStub();
    tradeApi = new TradeApiClientServiceStub();
    httpGet = vi.fn().mockImplementation((url: string) => {
      if (url.includes('/coins/markets')) {
        return of([
          { id: 'bitcoin', symbol: 'btc', current_price: 64210.25, price_change_percentage_24h: 2.84 },
          { id: 'ethereum', symbol: 'eth', current_price: 3388.40, price_change_percentage_24h: 2.16 },
          { id: 'solana', symbol: 'sol', current_price: 152.31, price_change_percentage_24h: 1.72 },
          { id: 'ripple', symbol: 'xrp', current_price: 0.62, price_change_percentage_24h: 1.11 },
          { id: 'avalanche-2', symbol: 'avax', current_price: 39.45, price_change_percentage_24h: 0.97 }
        ]);
      }

      return of([
        [1727740800000, 100, 101.5, 99.4, 100.8],
        [1727827200000, 101, 102.4, 100.8, 102.0],
        [1727913600000, 102, 103.2, 101.1, 102.9]
      ]);
    });

    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        {
          provide: AuthSessionService,
          useValue: authSession
        },
        {
          provide: TradeApiClientService,
          useValue: tradeApi
        },
        {
          provide: HttpClient,
          useValue: {
            get: httpGet
          }
        },
        {
          provide: ActivatedRoute,
          useValue: { params: {} }
        },
        {
          provide: Router,
          useValue: { navigate: vi.fn() }
        }
      ]
    }).compileComponents();
  });

  it('renders account summary and data tables after load', async () => {
    fixture = TestBed.createComponent(DashboardComponent);

    fixture.detectChanges();
    await settleView();
    await fixture.componentInstance.refresh();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    expect(component.isLoading()).toBe(false);
    expect(component.loadError()).toBeNull();

    expect(component.account()).not.toBeNull();
    expect(component.balance()).not.toBeNull();

    expect(component.positions()).toHaveLength(1);
    expect(component.orders()).toHaveLength(1);

    // account-summary and positions moved to dedicated routed pages.
    // Verify analytics card remains present on dashboard.
    expect(
      host.querySelector('[data-testid="analytics-charts"]')
    ).toBeTruthy();

    expect(host.textContent).toContain('ACC-001');
    expect(host.textContent).toContain('Priya Menon');
  });

  it('shows dashboard error when session is missing', async () => {
    authSession.loadCurrentUser.mockResolvedValueOnce(null);

    fixture = TestBed.createComponent(DashboardComponent);

    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    expect(component.isLoading()).toBe(false);

    expect(component.loadError()).toBe(
      'Your session expired. Please sign in again.'
    );

    const errorNode = host.querySelector(
      '[data-testid="dashboard-error"]'
    );

    expect(errorNode).toBeTruthy();

    expect(errorNode?.textContent).toContain(
      'Your session expired. Please sign in again.'
    );
  });

  it('creates and opens a watchlist from the dashboard', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    component.createWatchlistForm.setValue({ name: 'Dividend' });

    await component.createWatchlist();
    fixture.detectChanges();

    expect(tradeApi.watchlists.createWatchlist).toHaveBeenCalledWith({
      createWatchlistRequest: { name: 'Dividend' }
    });
    expect(tradeApi.watchlists.getWatchlist).toHaveBeenCalledWith({ watchlistId: 12 });
    expect(component.watchlistMessage()).toBe('Watchlist created successfully.');
  });

  it('adds and removes instruments from the selected watchlist', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    await component.openWatchlist(11);
    component.addInstrumentForm.setValue({ symbol: 'msft' });

    await component.addInstrument();
    await component.removeInstrument('AAPL');

    expect(tradeApi.watchlists.addWatchlistInstrument).toHaveBeenCalledWith({
      watchlistId: 11,
      addWatchlistInstrumentRequest: { symbol: 'MSFT' }
    });
    expect(tradeApi.watchlists.removeWatchlistInstrument).toHaveBeenCalledWith({
      watchlistId: 11,
      symbol: 'AAPL'
    });
  });

  it('opens and closes a symbol details popup for a watchlist instrument', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    await component.openWatchlist(11);

    // Directly open instrument details via component API since watchlist UI
    // was moved to its own routed page.
    const instrument = component.selectedWatchlist()!.instruments[0];
    component.openInstrumentDetails(instrument);

    expect(component.selectedInstrument()?.symbol).toBe('AAPL');

    component.closeInstrumentDetails();

    expect(component.selectedInstrument()).toBeNull();
  });

  it('shows fallback analytics when live market request fails', async () => {
    httpGet.mockImplementation((url: string) => {
      if (url.includes('/coins/markets')) {
        return throwError(() => new Error('market feed down'));
      }
      return of([]);
    });

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    await (component as any).loadTopPerformerAnalytics();

    expect(component.stockBars().length).toBeGreaterThan(0);
    expect(component.selectedCandleSymbol()).toBeTruthy();
    expect(component.portfolioLinePoints().length).toBeGreaterThan(0);
  });

  it('handles invalid create watchlist form without API call', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    component.createWatchlistForm.setValue({ name: '' });
    await component.createWatchlist();

    expect(tradeApi.watchlists.createWatchlist).not.toHaveBeenCalled();
  });

  it('returns error when adding instrument without selected watchlist', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    component.selectedWatchlist.set(null);
    await component.addInstrument();

    expect(component.watchlistError()).toBe('Choose a watchlist before adding an instrument.');
  });

  it('deletes selected watchlist and clears selection', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    component.selectedWatchlist.set({ id: 11 } as any);
    component.selectedInstrument.set({ symbol: 'AAPL' } as any);
    await component.deleteWatchlist(11);

    expect(tradeApi.watchlists.deleteWatchlist).toHaveBeenCalledWith({ watchlistId: 11 });
    expect(component.selectedWatchlist()).toBeNull();
    expect(component.selectedInstrument()).toBeNull();
  });

  it('covers latest price summary helper', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    expect(component.latestPriceSummary(null)).toBe('No quote yet');
    expect(component.latestPriceSummary({ currency: 'USD', price: 10.5 } as any)).toBe('USD 10.50');
  });

  it('keeps selected candle symbol unchanged when symbol has no candle data', async () => {
    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    await settleView();

    const component = fixture.componentInstance;
    const previous = component.selectedCandleSymbol();
    component.selectCandleSymbol('DOES_NOT_EXIST');

    expect(component.selectedCandleSymbol()).toBe(previous);
  });
});
