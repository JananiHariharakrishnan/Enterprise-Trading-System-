import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { OrderHistoryComponent } from './order-history.component';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { AuthSessionService } from '../../services/auth-session.service';

class TradeApiClientServiceStub {
  accounts = {
    getOrders: vi.fn()
  };
}

class AuthSessionServiceStub {
  loadCurrentUser = vi.fn().mockResolvedValue({
    id: 'u-1',
    username: 'priya',
    accountId: 101
  });
}

describe('OrderHistoryComponent', () => {
  let fixture: ComponentFixture<OrderHistoryComponent>;
  let component: OrderHistoryComponent;
  let tradeApi: TradeApiClientServiceStub;
  let authSession: AuthSessionServiceStub;
  let queryParams$: Subject<Record<string, string>>;
  let router: { navigate: ReturnType<typeof vi.fn> };

  const createOrder = (overrides: Partial<any> = {}) => ({
    orderId: 'ORD-1',
    symbol: 'AAPL',
    side: 'BUY',
    status: 'FILLED',
    quantity: 10,
    price: 123.45,
    createdOn: new Date('2026-01-01T10:00:00Z'),
    ...overrides
  });

  beforeEach(async () => {
    queryParams$ = new Subject<Record<string, string>>();
    tradeApi = new TradeApiClientServiceStub();
    authSession = new AuthSessionServiceStub();
    router = { navigate: vi.fn().mockResolvedValue(true) };

    tradeApi.accounts.getOrders.mockResolvedValue([createOrder()]);

    await TestBed.configureTestingModule({
      imports: [OrderHistoryComponent],
      providers: [
        { provide: TradeApiClientService, useValue: tradeApi },
        { provide: AuthSessionService, useValue: authSession },
        {
          provide: ActivatedRoute,
          useValue: { queryParams: queryParams$.asObservable() }
        },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderHistoryComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders table rows from loaded orders', async () => {
    fixture.detectChanges();
    queryParams$.next({ status: 'FILLED', from: '2026-01-01', to: '2026-01-31' });
    await fixture.whenStable();
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('ORD-1');
    expect(fixture.nativeElement.textContent).toContain('AAPL');
    expect(fixture.nativeElement.textContent).toContain('FILLED');
  });

  it('shows empty state when no orders are returned', async () => {
    tradeApi.accounts.getOrders.mockResolvedValueOnce([]);

    fixture.detectChanges();
    queryParams$.next({});
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No orders found');
  });

  it('shows loading and disables refresh while loading', () => {
    component.isLoading.set(true);
    fixture.detectChanges();

    const refreshButton = fixture.nativeElement.querySelector('[data-testid="refresh-orders-button"]') as HTMLButtonElement;
    expect(fixture.nativeElement.textContent).toContain('Loading orders...');
    expect(refreshButton.disabled).toBe(true);
  });

  it('shows polling status when polling is active', () => {
    component.isPolling.set(true);
    component.pollAttempts.set(3);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Still working... (3/12 checks)');
  });

  it('submits filter form through DOM and updates query params', async () => {
    fixture.detectChanges();

    component.filterForm.setValue({
      status: 'NEW',
      fromDate: '2026-01-01',
      toDate: '2026-01-31'
    });
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form.filter-form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));

    expect(router.navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {
        status: 'NEW',
        from: '2026-01-01',
        to: '2026-01-31'
      },
      queryParamsHandling: 'merge'
    });
  });

  it('clear button resets form and applies default filters', async () => {
    const applySpy = vi.spyOn(component, 'applyFilters');

    fixture.detectChanges();

    component.filterForm.setValue({
      status: 'REJECTED',
      fromDate: '2025-01-01',
      toDate: '2025-01-31'
    });

    const clearButton = fixture.nativeElement.querySelector('.btn-ghost') as HTMLButtonElement;
    clearButton.click();

    expect(component.filterForm.getRawValue().status).toBe('ALL');
    expect(component.filterForm.getRawValue().fromDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(component.filterForm.getRawValue().toDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(applySpy).toHaveBeenCalled();
  });

  it('renders active filters badge when status is not ALL', () => {
    component.filterForm.controls.status.setValue('NEW');
    fixture.detectChanges();

    const activeStatus = fixture.nativeElement.querySelector('.seg-btn.active') as HTMLButtonElement;
    expect(activeStatus?.textContent).toContain('NEW');
    expect(activeStatus?.getAttribute('aria-pressed')).toBe('true');
  });

  it('shows load error when request fails', async () => {
    tradeApi.accounts.getOrders.mockRejectedValueOnce(new Error('boom'));

    fixture.detectChanges();
    queryParams$.next({});
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unable to load order history. Please check your filters.');
  });

  it('refresh button calls refresh method', () => {
    const refreshSpy = vi.spyOn(component, 'refresh');

    component.isLoading.set(false);
    fixture.detectChanges();

    const refreshButton = fixture.nativeElement.querySelector('[data-testid="refresh-orders-button"]') as HTMLButtonElement;
    expect(refreshButton.disabled).toBe(false);
    refreshButton.click();

    expect(refreshSpy).toHaveBeenCalled();
  });

  it('resets polling state on destroy', () => {
    component.isPolling.set(true);
    component.ngOnDestroy();

    expect(component.isPolling()).toBe(false);
  });
});
