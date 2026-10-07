import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertsComponent } from './alerts.component';
import { ReactiveFormsModule } from '@angular/forms';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { vi } from 'vitest';

describe('AlertsComponent', () => {
  let component: AlertsComponent;
  let fixture: ComponentFixture<AlertsComponent>;
  let mockApiClient: any;

  beforeEach(async () => {
    mockApiClient = {
      priceAlerts: {
        listAlerts: vi.fn().mockResolvedValue([
          { id: 1, symbol: 'AAPL', direction: 'ABOVE', threshold: 150, status: 'ACTIVE', createdOn: new Date() },
          { id: 2, symbol: 'MSFT', direction: 'BELOW', threshold: 250, status: 'TRIGGERED', createdOn: new Date() },
          { id: 3, symbol: 'TSLA', direction: 'ABOVE', threshold: 800, status: 'CANCELLED', createdOn: new Date() }
        ]),
        createAlert: vi.fn().mockResolvedValue({}),
        cancelAlert: vi.fn().mockResolvedValue({})
      }
    };

    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, AlertsComponent],
      providers: [
        { provide: TradeApiClientService, useValue: mockApiClient }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AlertsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load alerts on init', async () => {
    expect(component).toBeTruthy();
    await fixture.whenStable();
    expect(mockApiClient.priceAlerts.listAlerts).toHaveBeenCalled();
    expect(component.alerts().length).toBe(3);
  });

  it('should reject invalid form submission', () => {
    component.createAlertForm.setValue({ direction: 'ABOVE', threshold: null });
    component.createAlert();
    expect(mockApiClient.priceAlerts.createAlert).not.toHaveBeenCalled();
    expect(component.createAlertForm.valid).toBe(false);
  });

  it('should reject missing symbol', async () => {
    component.selectedInstrument.set(null);
    component.createAlertForm.setValue({ direction: 'ABOVE', threshold: 100 });
    await component.createAlert();
    expect(mockApiClient.priceAlerts.createAlert).not.toHaveBeenCalled();
  });

  it('should reject non-positive threshold', () => {
    component.selectedInstrument.set({ symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD' });
    component.createAlertForm.setValue({ direction: 'ABOVE', threshold: -5 });
    expect(component.createAlertForm.valid).toBe(false);
  });

  it('should call Create Alert API and refresh list on valid submit', async () => {
    component.selectedInstrument.set({ symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD' });
    component.createAlertForm.setValue({ direction: 'ABOVE', threshold: 160 });
    await component.createAlert();
    expect(mockApiClient.priceAlerts.createAlert).toHaveBeenCalledWith({
      createAlertRequest: { symbol: 'AAPL', direction: 'ABOVE', threshold: 160 }
    });
    expect(mockApiClient.priceAlerts.listAlerts).toHaveBeenCalledTimes(2); // once on init, once on create
    expect(component.createAlertForm.value).toEqual({ direction: 'ABOVE', threshold: null });
    expect(component.selectedInstrument()).toBeNull();
  });

  it('refreshes the alert list from the refresh button', async () => {
    await fixture.whenStable();
    mockApiClient.priceAlerts.listAlerts.mockClear();

    await component.refresh();

    expect(mockApiClient.priceAlerts.listAlerts).toHaveBeenCalledTimes(1);
    expect(component.alerts().length).toBe(3);
    expect(component.isRefreshing()).toBe(false);
  });

  it('should call Cancel API for ACTIVE alert', async () => {
    await component.cancelAlert(1);
    expect(mockApiClient.priceAlerts.cancelAlert).toHaveBeenCalledWith({ alertId: 1 });
    expect(mockApiClient.priceAlerts.listAlerts).toHaveBeenCalledTimes(2);
  });
  
  it('should display Cancel for ACTIVE alerts only', async () => {
    await fixture.whenStable();
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(3);
    
    // Active alert
    expect(rows[0].querySelector('button')).toBeTruthy();
    // Triggered alert
    expect(rows[1].querySelector('button')).toBeFalsy();
    // Cancelled alert
    expect(rows[2].querySelector('button')).toBeFalsy();
  });
  
  it('should handle API failure correctly on create', async () => {
    mockApiClient.priceAlerts.createAlert.mockRejectedValue('API Error');
    component.selectedInstrument.set({ symbol: 'AAPL', name: 'Apple', assetClass: 'EQUITY', currency: 'USD' });
    component.createAlertForm.setValue({ direction: 'ABOVE', threshold: 160 });
    await component.createAlert();
    
    // Should show error toast
    expect(component.toasts().length).toBeGreaterThan(0);
    expect(component.toasts()[0].type).toBe('error');
    
    // Form should remain usable (not cleared)
    expect(component.selectedInstrument()?.symbol).toBe('AAPL');
  });

  it('renders the selected symbol, empty list, load error, and toasts', () => {
    component.selectedInstrument.set({
      symbol: 'AAPL',
      name: 'Apple',
      assetClass: 'EQUITY',
      currency: ''
    });
    component.createAlertForm.patchValue({ direction: 'BELOW' });
    component.isRefreshing.set(true);
    component.isSaving.set(true);
    component.alerts.set([]);
    component.isLoading.set(false);
    component.loadError.set('Alerts are unavailable');
    component.toasts.set([
      { id: 1, type: 'success', text: 'Alert saved' },
      { id: 2, type: 'error', text: 'Alert failed' }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AAPL');
    expect(fixture.nativeElement.textContent).toContain('Refreshing');
    expect(fixture.nativeElement.textContent).toContain('Creating...');
    expect(fixture.nativeElement.textContent).toContain('Alerts are unavailable');
    expect(fixture.nativeElement.textContent).toContain('Alert saved');
    expect(fixture.nativeElement.textContent).toContain('Alert failed');

    component.loadError.set(null);
    component.isLoading.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No alerts found');

    component.dismissToast(1);
    expect(component.toasts().map((toast) => toast.id)).toEqual([2]);
  });
});
