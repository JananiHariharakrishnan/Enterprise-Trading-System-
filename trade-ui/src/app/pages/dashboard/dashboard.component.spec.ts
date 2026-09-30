import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
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

  beforeEach(async () => {
    authSession = new AuthSessionServiceStub();
    tradeApi = new TradeApiClientServiceStub();

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

    // Allow all promise/microtask chains created by loadDashboard()
    // to complete.
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    fixture.detectChanges();

    const component = fixture.componentInstance;
    const host = fixture.nativeElement as HTMLElement;

    expect(component.isLoading()).toBe(false);
    expect(component.loadError()).toBeNull();

    expect(component.account()).not.toBeNull();
    expect(component.balance()).not.toBeNull();

    expect(component.positions()).toHaveLength(1);
    expect(component.orders()).toHaveLength(1);

    expect(
      host.querySelector('[data-testid="account-summary"]')
    ).toBeTruthy();

    expect(
      host.querySelector('[data-testid="positions-list"]')
    ).toBeTruthy();

    expect(
      host.querySelector('[data-testid="orders-list"]')
    ).toBeTruthy();

    expect(host.textContent).toContain('ACC-001');
    expect(host.textContent).toContain('Priya Menon');
    expect(host.textContent).toContain('AAPL');
    expect(host.textContent).toContain('ORD-1');
    expect(host.textContent).toContain('Tech');
  });

  it('shows dashboard error when session is missing', async () => {
    authSession.loadCurrentUser.mockResolvedValueOnce(null);

    fixture = TestBed.createComponent(DashboardComponent);

    fixture.detectChanges();

    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    fixture.detectChanges();

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

    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

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

    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

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
});
