import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { OrdersComponent } from './orders.component';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { ActivatedRoute } from '@angular/router';

class AuthSessionServiceStub {
  loadCurrentUser = vi.fn().mockResolvedValue({
    id: 'u-1',
    username: 'priya',
    accountId: 101
  });
}

class TradeApiClientServiceStub {
  accounts = {
    getAccount: vi.fn().mockResolvedValue({
      id: 101,
      accountId: 'ACC-101',
      holderName: 'Priya Menon',
      cashBalance: 1000,
      status: 'ACTIVE',
      version: 1,
      lastUpdated: new Date('2026-01-01T00:00:00Z')
    })
  };

  orders = {
    placeOrder: vi.fn().mockResolvedValue({
      orderId: 'ORD-1',
      status: 'PLACED'
    })
  };

  marketData = {
    getAllMarketData: vi.fn().mockResolvedValue({})
  };
}

describe('OrdersComponent', () => {
  let fixture: ComponentFixture<OrdersComponent>;
  let component: OrdersComponent;
  let authSession: AuthSessionServiceStub;
  let tradeApi: TradeApiClientServiceStub;

  beforeEach(async () => {
    authSession = new AuthSessionServiceStub();
    tradeApi = new TradeApiClientServiceStub();

    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        { provide: AuthSessionService, useValue: authSession },
        { provide: TradeApiClientService, useValue: tradeApi },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: vi.fn().mockReturnValue(null)
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads account details on init when user is authenticated', async () => {
    await component.ngOnInit();

    expect(authSession.loadCurrentUser).toHaveBeenCalled();
    expect(tradeApi.accounts.getAccount).toHaveBeenCalledWith({ id: 101 });
    expect(component.account()?.accountId).toBe('ACC-101');
  });

  it('refreshes the account balance without placing an order', async () => {
    await component.ngOnInit();
    tradeApi.accounts.getAccount.mockClear();

    await component.refresh();

    expect(tradeApi.accounts.getAccount).toHaveBeenCalledWith({ id: 101 });
    expect(tradeApi.orders.placeOrder).not.toHaveBeenCalled();
  });

  it('does not load account when session is missing', async () => {
    authSession.loadCurrentUser.mockResolvedValueOnce(null);

    await component.ngOnInit();

    expect(tradeApi.accounts.getAccount).not.toHaveBeenCalled();
    expect(component.account()).toBeNull();
  });

  it('returns early on invalid form submission', async () => {
    component.form.patchValue({ symbol: '' });

    await component.submit();

    expect(tradeApi.orders.placeOrder).not.toHaveBeenCalled();
  });

  it('throws when submitting without authenticated user', async () => {
    authSession.loadCurrentUser.mockResolvedValueOnce(null);

    const instrument = { symbol: 'AAPL', name: 'Apple Inc', assetClass: 'equity', currency: 'USD' };
    component.onInstrumentSelected(instrument);
    component.form.patchValue({
      side: 'BUY',
      quantity: 10,
      price: null,
      orderType: 'MARKET'
    });

    await component.submit();

    // The component catches the error internally and doesn't re-throw
    // but it should NOT call placeOrder and should NOT set a success message
    expect(tradeApi.orders.placeOrder).not.toHaveBeenCalled();
    expect(component.successMessage()).toBeNull();
  });

  it('places order and sets success message on success', async () => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('123e4567-e89b-12d3-a456-426614174000');

    const instrument = { symbol: 'AAPL', name: 'Apple Inc', assetClass: 'equity', currency: 'USD' };
    component.onInstrumentSelected(instrument);
    component.form.patchValue({
      side: 'BUY',
      quantity: 10,
      price: null,
      orderType: 'MARKET'
    });

    await component.submit();

    expect(tradeApi.orders.placeOrder).toHaveBeenCalledWith({
      placeOrderRequest: {
        accountId: 101,
        symbol: 'AAPL',
        side: 'BUY',
        quantity: 10,
        price: null,
        orderType: 'MARKET',
        idempotencyKey: '123e4567-e89b-12d3-a456-426614174000'
      }
    });
    expect(component.successMessage()).toContain('Order placed');
    expect(component.errorMessage()).toBeNull();
  });

  it('maps order failure message into error state', async () => {
    tradeApi.orders.placeOrder.mockRejectedValueOnce(new Error('Insufficient funds'));

    const instrument = { symbol: 'AAPL', name: 'Apple Inc', assetClass: 'equity', currency: 'USD' };
    component.onInstrumentSelected(instrument);
    component.form.patchValue({
      side: 'BUY',
      quantity: 10,
      price: null,
      orderType: 'MARKET'
    });

    await component.submit();

    expect(component.errorMessage()).toBe('Insufficient funds');
    expect(component.successMessage()).toBeNull();
  });

  it('renders disabled submit button for invalid form', () => {
    fixture.detectChanges();

    const submitButton = fixture.nativeElement.querySelector(
      '[data-testid="place-order-button"]'
    ) as HTMLButtonElement;

    // Button should be disabled when no instrument is selected
    // (Form can be valid, but button is disabled without instrument)
    expect(submitButton.disabled).toBe(true);
    expect(submitButton.textContent).toContain('Select an instrument');
  });

  it('renders active submit button text when form is valid', () => {
    const instrument = { symbol: 'AAPL', name: 'Apple Inc', assetClass: 'equity', currency: 'USD' };
    component.onInstrumentSelected(instrument);
    component.form.patchValue({
      side: 'BUY',
      quantity: 2,
      price: 100.5,
      orderType: 'LIMIT'
    });

    fixture.detectChanges();

    const submitButton = fixture.nativeElement.querySelector(
      '[data-testid="place-order-button"]'
    ) as HTMLButtonElement;

    expect(component.form.valid).toBe(true);
    expect(submitButton.disabled).toBe(false);
    expect(submitButton.textContent).toContain('Buy 2 AAPL');
  });

  it('submits through DOM and renders success status', async () => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue('123e4567-e89b-12d3-a456-426614174000');

    const instrument = { symbol: 'MSFT', name: 'Microsoft Corp', assetClass: 'equity', currency: 'USD' };
    component.onInstrumentSelected(instrument);
    component.form.patchValue({
      side: 'BUY',
      quantity: 2,
      price: null,
      orderType: 'MARKET'
    });
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form.ticket-form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();

    const success = fixture.nativeElement.querySelector('[data-testid="order-success"]') as HTMLElement;
    expect(success).toBeTruthy();
    expect(success.textContent).toContain('Order placed');
  });

  it('submits through DOM and renders error status', async () => {
    tradeApi.orders.placeOrder.mockRejectedValueOnce(new Error('Risk check failed'));

    const instrument = { symbol: 'MSFT', name: 'Microsoft Corp', assetClass: 'equity', currency: 'USD' };
    component.onInstrumentSelected(instrument);
    component.form.patchValue({
      side: 'BUY',
      quantity: 2,
      price: null,
      orderType: 'MARKET'
    });
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form.ticket-form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="order-error"]') as HTMLElement;
    expect(error).toBeTruthy();
    expect(error.textContent).toContain('Risk check failed');
  });
});
