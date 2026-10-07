import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SignalsComponent } from './signals.component';
import { SignalsService, type TradeSignalResponse } from '../../services/signals.service';

const buy: TradeSignalResponse = {
  symbol: 'AAPL',
  action: 'BUY',
  confidence: 0.9,
  reasoning: 'strong trend',
  generatedAt: '2026-10-07T01:00:00Z'
};
const sell: TradeSignalResponse = {
  symbol: 'MSFT',
  action: 'SELL',
  confidence: 0.4,
  reasoning: 'weak',
  generatedAt: '2026-10-07T02:00:00Z'
};

describe('SignalsComponent', () => {
  let fixture: ComponentFixture<SignalsComponent>;
  let component: SignalsComponent;
  let api: {
    getAll: ReturnType<typeof vi.fn>;
    refreshAll: ReturnType<typeof vi.fn>;
    generate: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(async () => {
    api = {
      getAll: vi.fn().mockReturnValue(of([sell, buy])),
      refreshAll: vi.fn().mockReturnValue(of([buy])),
      generate: vi.fn().mockReturnValue(of({ ...buy, symbol: 'NVDA', action: 'HOLD', confidence: 0.5 }))
    };
    await TestBed.configureTestingModule({
      imports: [SignalsComponent],
      providers: [
        provideRouter([]),
        { provide: SignalsService, useValue: api }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(SignalsComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('loads signals and computes counts', () => {
    expect(component.totalCount()).toBe(2);
    expect(component.buyCount()).toBe(1);
    expect(component.sellCount()).toBe(1);
    expect(component.avgConfidence()).toBe(65);
    expect(component.filteredSignals()[0].symbol).toBe('AAPL');
    expect(component.isLoading()).toBe(false);
  });

  it('filters, searches, and resets', () => {
    component.setFilter('SELL');
    expect(component.filteredSignals().map((row) => row.symbol)).toEqual(['MSFT']);
    component.setFilter('ALL');
    component.onSearch({ target: { value: 'trend' } } as unknown as Event);
    expect(component.filteredSignals().map((row) => row.symbol)).toEqual(['AAPL']);
    component.resetFilters();
    expect(component.searchQuery()).toBe('');
    component.setViewMode('card');
    expect(component.viewMode()).toBe('card');
  });

  it('records a fetch error and refreshes with a toast', () => {
    api.getAll.mockReturnValueOnce(throwError(() => new Error('down')));
    component.fetchSignals(false);
    expect(component.error()).toBe('down');
    expect(component.isRefreshing()).toBe(false);

    component.refreshAllSignals();
    expect(component.signals()).toEqual([buy]);
    expect(component.toastType()).toBe('success');

    api.refreshAll.mockReturnValueOnce(throwError(() => new Error('no')));
    component.refreshAllSignals();
    expect(component.toastType()).toBe('error');
  });

  it('analyzes a new symbol and updates an existing one', () => {
    component.analyzeSymbol('nvda');
    expect(component.signals().some((row) => row.symbol === 'NVDA')).toBe(true);
    expect(component.analyzeInput()).toBe('');

    api.generate.mockReturnValueOnce(of({ ...buy, confidence: 0.2 }));
    component.analyzeInput.set('aapl');
    component.analyzeSymbol();
    expect(component.signals().find((row) => row.symbol === 'AAPL')?.confidence).toBe(0.2);

    component.analyzeSymbol('   ');
    api.generate.mockReturnValueOnce(throwError(() => new Error('bad')));
    component.analyzeSymbol('ZZZ');
    expect(component.toastMessage()).toContain('ZZZ');
    expect(component.isAnalyzing()).toBe(false);
  });

  it('navigates to the order page and formats display helpers', () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.tradeSymbol('AAPL', 'SELL');
    expect(navigate).toHaveBeenCalledWith(['/orders'], { queryParams: { symbol: 'AAPL', side: 'SELL' } });
    component.tradeSymbol('MSFT', 'HOLD');
    expect(navigate).toHaveBeenLastCalledWith(['/orders'], { queryParams: { symbol: 'MSFT', side: 'BUY' } });
    expect(component.formatConfidence(0.25)).toBe(25);
    expect(component.formatConfidence(0)).toBe(0);
    expect(component.symbolHue('AAPL')).toBeGreaterThanOrEqual(0);
    expect(component.trackBySymbol(0, buy)).toBe('AAPL');
    component.signals.set([]);
    expect(component.avgConfidence()).toBe(0);
  });

  it('clears a toast after four seconds', () => {
    vi.useFakeTimers();
    component.showToast('success', 'done');
    expect(component.toastMessage()).toBe('done');
    vi.advanceTimersByTime(4000);
    expect(component.toastMessage()).toBeNull();
    vi.useRealTimers();
  });

  it('renders table, card, empty, and error states', () => {
    component.signals.set([
      { ...buy, action: 'HOLD', confidence: 0.4, rsi: 55, ema9: 180, ema21: 175, generatedAt: '2026-10-07T03:00:00Z' },
      sell
    ]);
    component.isAnalyzing.set(true);
    component.toastMessage.set('Updated');
    component.toastType.set('error');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.signals-table')).toBeTruthy();
    expect(component.holdCount()).toBe(1);

    component.setViewMode('card');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.signals-grid')).toBeTruthy();

    component.setFilter('BUY');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No signals match');

    component.signals.set([]);
    component.error.set('offline');
    component.isLoading.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No Trade Signals Yet');
    expect(fixture.nativeElement.querySelector('.error-card')).toBeTruthy();
  });
});
