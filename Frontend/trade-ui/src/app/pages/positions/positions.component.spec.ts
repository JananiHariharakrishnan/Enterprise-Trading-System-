import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PositionsComponent } from './positions.component';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';

class AuthSessionServiceStub {
  loadCurrentUser = vi.fn().mockResolvedValue({
    id: 'u-1',
    username: 'priya',
    accountId: 101
  });
}

class TradeApiClientServiceStub {
  accounts = {
    getPositions: vi.fn().mockResolvedValue([
      { accountId: 101, symbol: 'AAPL', quantity: 3, averageCost: 190 }
    ])
  };
}

describe('PositionsComponent', () => {
  let fixture: ComponentFixture<PositionsComponent>;
  let component: PositionsComponent;
  let authSession: AuthSessionServiceStub;
  let tradeApi: TradeApiClientServiceStub;

  const createComponent = () => {
    fixture = TestBed.createComponent(PositionsComponent);
    component = fixture.componentInstance;
  };

  const renderWithoutInit = () => {
    createComponent();
    vi.spyOn(component, 'ngOnInit').mockImplementation(() => undefined);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    authSession = new AuthSessionServiceStub();
    tradeApi = new TradeApiClientServiceStub();

    await TestBed.configureTestingModule({
      imports: [PositionsComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradeApiClientService, useValue: tradeApi }
      ]
    }).compileComponents();
  });

  it('ngOnInit delegates to loadPositions', () => {
    createComponent();
    const loadSpy = vi.spyOn(component as never as { loadPositions: () => Promise<void> }, 'loadPositions');

    component.ngOnInit();

    expect(loadSpy).toHaveBeenCalled();
  });

  it('refresh loads positions for authenticated user', async () => {
    createComponent();

    await component.refresh();

    expect(authSession.loadCurrentUser).toHaveBeenCalled();
    expect(tradeApi.accounts.getPositions).toHaveBeenCalledWith({ id: 101 });
    expect(component.positions()).toHaveLength(1);
    expect(component.loadError()).toBeNull();
    expect(component.isLoading()).toBe(false);
  });

  it('shows session-expired message when user is missing', async () => {
    authSession.loadCurrentUser.mockResolvedValueOnce(null);

    createComponent();
    await component.refresh();

    expect(tradeApi.accounts.getPositions).not.toHaveBeenCalled();
    expect(component.loadError()).toBe('Your session expired. Please sign in again.');
    expect(component.isLoading()).toBe(false);
  });

  it('shows API error when positions request fails', async () => {
    tradeApi.accounts.getPositions.mockRejectedValueOnce(new Error('backend down'));

    createComponent();
    await component.refresh();

    expect(component.loadError()).toBe('Unable to load positions right now. Check trade-api and try again.');
    expect(component.isLoading()).toBe(false);
  });

  it('refresh re-runs positions fetch', async () => {
    createComponent();
    await component.refresh();

    await component.refresh();

    expect(tradeApi.accounts.getPositions).toHaveBeenCalledTimes(2);
  });

  it('renders loading message in template', () => {
    renderWithoutInit();
    component.isLoading.set(true);
    fixture.detectChanges();

    const loading = fixture.nativeElement.querySelector('[data-testid="positions-loading"]') as HTMLElement;
    expect(loading).toBeTruthy();
    expect(loading.textContent).toContain('Loading positions...');
  });

  it('renders empty state when there are no positions', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.loadError.set(null);
    component.positions.set([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No open positions.');
  });

  it('renders positions table when data exists', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.loadError.set(null);
    component.positions.set([
      { accountId: 101, symbol: 'AAPL', quantity: 3, averageCost: 190 },
      { accountId: 101, symbol: 'MSFT', quantity: 5, averageCost: 320.5 }
    ] as any);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('AAPL');
    expect(fixture.nativeElement.textContent).toContain('MSFT');
  });

  it('renders error state and triggers retry button', () => {
    renderWithoutInit();
    const refreshSpy = vi.spyOn(component, 'refresh').mockResolvedValue();

    component.isLoading.set(false);
    component.loadError.set('Unable to load positions right now. Check trade-api and try again.');
    fixture.detectChanges();

    const errorBox = fixture.nativeElement.querySelector('[data-testid="positions-error"]') as HTMLElement;
    expect(errorBox).toBeTruthy();

    const retryButton = errorBox.querySelector('button') as HTMLButtonElement;
    retryButton.click();

    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });

  it('triggers refresh from header refresh button', () => {
    renderWithoutInit();
    const refreshSpy = vi.spyOn(component, 'refresh').mockResolvedValue();

    const refreshButton = fixture.nativeElement.querySelector('.positions-header button') as HTMLButtonElement;
    refreshButton.click();

    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });
});
