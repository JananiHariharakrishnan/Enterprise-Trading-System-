import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthSessionService } from '../../services/auth-session.service';
import { PortfolioApiClientService } from '../../services/portfolio-api-client.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { PnlComponent } from './pnl.component';

describe('PnlComponent', () => {
  let fixture: ComponentFixture<PnlComponent>;
  let component: PnlComponent;

  let mockAuthSession: {
    loadCurrentUser: ReturnType<typeof vi.fn>;
  };

  let mockPortfolio: {
    getSummary: ReturnType<typeof vi.fn>;
    getPositions: ReturnType<typeof vi.fn>;
    getPnl: ReturnType<typeof vi.fn>;
    getMovers: ReturnType<typeof vi.fn>;
  };

  let mockTradeApi: {
    accounts: {
      getPositions: ReturnType<typeof vi.fn>;
    };
  };

  const sampleSummary = {
    accountId: 42,
    baseCurrency: 'USD',
    cashBalance: 25000,
    marketValue: 15000,
    costBasis: 12000,
    totalValue: 40000,
    unrealisedPnl: 3000,
    unrealisedPnlPercent: 25,
    realisedPnl: 1500,
    realisedPnlPercent: 10
  };

  const samplePositions = [
    {
      accountId: 42,
      symbol: 'AAPL',
      quantity: 50,
      averageCost: 150,
      costBasis: 7500,
      lastPrice: 180,
      marketValue: 9000,
      unrealisedPnl: 1500,
      unrealisedPnlPercent: 20,
      currency: 'USD',
      priceAsOf: new Date().toISOString(),
      stale: false
    }
  ];

  const samplePnl = {
    accountId: 42,
    baseCurrency: 'USD',
    realisedPnl: 1500,
    unrealisedPnl: 3000,
    totalPnl: 4500,
    bySymbol: [
      {
        symbol: 'AAPL',
        realisedPnl: 500,
        unrealisedPnl: 1500,
        totalPnl: 2000
      }
    ]
  };

  const sampleMovers = {
    accountId: 42,
    baseCurrency: 'USD',
    basis: 'total' as const,
    gainers: [
      { symbol: 'AAPL', pnl: 1500, pnlPercent: 20, stale: false }
    ],
    losers: []
  };

  beforeEach(async () => {
    mockAuthSession = {
      loadCurrentUser: vi.fn().mockResolvedValue({ accountId: 42, username: 'testtrader' })
    };

    mockPortfolio = {
      getSummary: vi.fn().mockResolvedValue(sampleSummary),
      getPositions: vi.fn().mockResolvedValue(samplePositions),
      getPnl: vi.fn().mockResolvedValue(samplePnl),
      getMovers: vi.fn().mockResolvedValue(sampleMovers)
    };

    mockTradeApi = {
      accounts: {
        getPositions: vi.fn().mockResolvedValue([])
      }
    };

    await TestBed.configureTestingModule({
      imports: [PnlComponent],
      providers: [
        { provide: AuthSessionService, useValue: mockAuthSession },
        { provide: PortfolioApiClientService, useValue: mockPortfolio },
        { provide: TradeApiClientService, useValue: mockTradeApi }
      ]
    }).compileComponents();

    vi.spyOn(PnlComponent.prototype, 'ngOnInit').mockImplementation(() => undefined);
    fixture = TestBed.createComponent(PnlComponent);
    component = fixture.componentInstance;
  });

  it('creates component and loads portfolio data on init', async () => {
    await component.refresh();
    fixture.detectChanges();

    expect(component).toBeTruthy();
    expect(mockAuthSession.loadCurrentUser).toHaveBeenCalled();
    expect(mockPortfolio.getSummary).toHaveBeenCalledWith(42);
    expect(mockPortfolio.getPositions).toHaveBeenCalledWith(42);
    expect(mockPortfolio.getPnl).toHaveBeenCalledWith(42, expect.any(Object));
    expect(mockPortfolio.getMovers).toHaveBeenCalledWith(42, 'total', 5);

    expect(component.summary()).toEqual(sampleSummary);
    expect(component.positions()).toEqual(samplePositions);
    expect(component.pnl()).toEqual(samplePnl);
    expect(component.movers()).toEqual(sampleMovers);
    expect(component.totalPnl()).toBe(4500);
    expect(component.isLoading()).toBe(false);
  });

  it('renders summary cards and holdings correctly', async () => {
    await component.refresh();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const summaryCard = compiled.querySelector('[data-testid="pnl-summary"]');
    expect(summaryCard).toBeTruthy();
    expect(summaryCard?.textContent).toContain('Total Portfolio Value');
    expect(summaryCard?.textContent).toContain('Available Cash');

    const holdingsSection = compiled.querySelector('[data-testid="pnl-holdings"]');
    expect(holdingsSection).toBeTruthy();
    expect(holdingsSection?.textContent).toContain('AAPL');
  });

  it('switches movers basis between total and day', async () => {
    await component.refresh();
    fixture.detectChanges();

    await component.setBasis('day');
    expect(component.moversBasis()).toBe('day');
    expect(mockPortfolio.getMovers).toHaveBeenCalledWith(42, 'day', 5);
  });

  it('formats monetary values and tones properly', () => {
    expect(component.money(1234.56, 'USD')).toContain('1,234.56');
    expect(component.money(null)).toBe('—');
    expect(component.signedMoney(500, 'USD')).toContain('+');
    expect(component.signedMoney(-500, 'USD')).toContain('500');
    expect(component.pct(12.345)).toBe('+12.35%');
    expect(component.pct(-4.2)).toBe('-4.20%');
    expect(component.pct(null)).toBe('—');
    expect(component.tone(10)).toBe('gain');
    expect(component.tone(-5)).toBe('loss');
    expect(component.tone(0)).toBe('flat');
    expect(component.tone(null)).toBe('flat');
  });

  it('handles range errors when fromDate is after toDate', async () => {
    await component.refresh();
    fixture.detectChanges();

    const fromEvent = { target: { value: '2026-05-10' } } as unknown as Event;
    const toEvent = { target: { value: '2026-05-01' } } as unknown as Event;

    await component.onFromChange(fromEvent);
    await component.onToChange(toEvent);

    expect(component.rangeError()).toBe('"From" must not be after "To".');
  });

  it('clears date range filter and reloads pnl', async () => {
    await component.refresh();
    fixture.detectChanges();

    await component.clearRange();
    expect(component.fromDate()).toBe('');
    expect(component.toDate()).toBe('');
    expect(component.rangeError()).toBeNull();
  });

  it('handles session expiry when loadCurrentUser returns null', async () => {
    mockAuthSession.loadCurrentUser.mockResolvedValueOnce(null);

    await component.refresh();
    fixture.detectChanges();

    expect(component.loadError()).toContain('session expired');
  });
});
