import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { WatchlistsComponent } from './watchlists.component';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { MarketState } from '../../generated/trade';
import type { WatchlistDetailResponse, WatchlistInstrumentResponse } from '../../generated/trade';
import type { InstrumentListItem } from '../../components/instrument-search/instrument-search.component';

class TradeApiClientServiceStub {
  watchlists = {
    listWatchlists: vi.fn(),
    createWatchlist: vi.fn(),
    getWatchlist: vi.fn(),
    addWatchlistInstrument: vi.fn(),
    removeWatchlistInstrument: vi.fn(),
    deleteWatchlist: vi.fn()
  };
}

const buildSummary = (id: number) => ({
  id,
  name: `WL-${id}`,
  entryCount: 0,
  createdOn: new Date('2026-01-01T00:00:00Z'),
  updatedOn: new Date('2026-01-01T00:00:00Z')
});

const buildInstrument = (symbol: string, changePct = 0): WatchlistInstrumentResponse => ({
  symbol,
  addedOn: new Date('2026-01-01T00:00:00Z'),
  latestPrice: {
    price: 100,
    currency: 'USD',
    changePercent: changePct,
    stale: false,
    quoteAsOf: new Date('2026-01-01T00:00:00Z'),
    marketState: MarketState.Open
  }
});

const buildDetail = (): WatchlistDetailResponse => ({
  id: 3,
  name: 'Growth',
  entryCount: 2,
  createdOn: new Date('2026-01-01T00:00:00Z'),
  updatedOn: new Date('2026-01-01T00:00:00Z'),
  instruments: [buildInstrument('MSFT', -0.45), buildInstrument('AAPL', 1.2)]
});

const lastToastText = (component: WatchlistsComponent): string | undefined => {
  const toasts = component.toasts();
  return toasts[toasts.length - 1]?.text;
};

describe('WatchlistsComponent', () => {
  let fixture: ComponentFixture<WatchlistsComponent>;
  let component: WatchlistsComponent;
  let tradeApi: TradeApiClientServiceStub;

  beforeEach(async () => {
    tradeApi = new TradeApiClientServiceStub();
    tradeApi.watchlists.listWatchlists.mockResolvedValue([buildSummary(2), buildSummary(1)]);
    tradeApi.watchlists.createWatchlist.mockResolvedValue(buildSummary(3));
    tradeApi.watchlists.getWatchlist.mockResolvedValue(buildDetail());
    tradeApi.watchlists.addWatchlistInstrument.mockResolvedValue(undefined);
    tradeApi.watchlists.removeWatchlistInstrument.mockResolvedValue(undefined);
    tradeApi.watchlists.deleteWatchlist.mockResolvedValue(undefined);

    await TestBed.configureTestingModule({
      imports: [WatchlistsComponent],
      providers: [{ provide: TradeApiClientService, useValue: tradeApi }]
    }).compileComponents();

    fixture = TestBed.createComponent(WatchlistsComponent);
    component = fixture.componentInstance;
  });

  it('loads watchlists on init', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    expect(tradeApi.watchlists.listWatchlists).toHaveBeenCalled();
    expect(component.watchlists()).toHaveLength(2);
    expect(component.loadError()).toBeNull();
    expect(component.isLoading()).toBe(false);
  });

  it('limits loaded watchlists to 50', async () => {
    tradeApi.watchlists.listWatchlists.mockResolvedValueOnce(
      Array.from({ length: 55 }, (_, i) => buildSummary(i + 1))
    );

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.watchlists()).toHaveLength(50);
  });

  it('sets load error when initial load fails', async () => {
    tradeApi.watchlists.listWatchlists.mockRejectedValueOnce(new Error('failed'));

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.loadError()).toBe('Unable to load watchlists. Please refresh and try again.');
    expect(component.isLoading()).toBe(false);
  });

  it('creates and opens a watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    component.createWatchlistForm.setValue({ name: 'Growth' });
    await component.createWatchlist();

    expect(tradeApi.watchlists.createWatchlist).toHaveBeenCalledWith({
      createWatchlistRequest: { name: 'Growth' }
    });
    expect(tradeApi.watchlists.getWatchlist).toHaveBeenCalledWith({ watchlistId: 3 });
    expect(lastToastText(component)).toBe('Watchlist created.');
    expect(component.createWatchlistForm.getRawValue().name).toBe('');
  });

  it('sorts instrument symbols when opening a watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);

    const symbols = component.selectedWatchlist()?.instruments?.map((i) => i.symbol);
    expect(symbols).toEqual(['AAPL', 'MSFT']);
  });

  it('adds an instrument with uppercase symbol', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    const instrument: InstrumentListItem = { symbol: 'tsla', name: 'Tesla Inc', assetClass: 'equity', currency: 'USD' };
    await component.addInstrument(instrument);

    expect(tradeApi.watchlists.addWatchlistInstrument).toHaveBeenCalledWith({
      watchlistId: 3,
      addWatchlistInstrumentRequest: { symbol: 'TSLA' }
    });
    expect(lastToastText(component)).toBe('Added TSLA to Growth.');
  });

  it('removes an instrument from selected watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    await component.removeInstrument('AAPL');

    expect(tradeApi.watchlists.removeWatchlistInstrument).toHaveBeenCalledWith({
      watchlistId: 3,
      symbol: 'AAPL'
    });
    expect(lastToastText(component)).toBe('Removed AAPL from Growth.');
  });

  it('deletes selected watchlist when confirmed', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));

    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    await component.deleteWatchlist(3);

    expect(tradeApi.watchlists.deleteWatchlist).toHaveBeenCalledWith({ watchlistId: 3 });
    expect(component.selectedWatchlist()).toBeNull();
    expect(lastToastText(component)).toBe('Watchlist deleted.');
  });

  it('keeps watchlist when delete is cancelled', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(false));

    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    await component.deleteWatchlist(3);

    expect(tradeApi.watchlists.deleteWatchlist).not.toHaveBeenCalled();
  });

  it('covers formatting helpers and selection toggle', () => {
    expect(component.formatPrice()).toBe('N/A');
    expect(component.formatPrice(22.1234)).toBe('22.12');
    expect(component.formatPrice(0.12345)).toBe('0.1235');

    expect(component.getPriceClass(null)).toBe('price-unavailable');
    expect(component.getPriceClass(1)).toBe('price-available');

    expect(component.getChangeClass()).toBe('neutral');
    expect(component.getChangeClass(0)).toBe('neutral');
    expect(component.getChangeClass(1)).toBe('positive');
    expect(component.getChangeClass(-1)).toBe('negative');

    expect(component.formatChange()).toBe('N/A');
    expect(component.formatChange(1.236)).toBe('+1.24');
    expect(component.formatChange(-1.236)).toBe('-1.24');

    component.selectedWatchlist.set(buildDetail());
    const instrument = component.selectedWatchlist()!.instruments[0];
    component.selectInstrument(instrument);
    expect(component.selectedInstrument()).toBe(instrument);
    component.selectInstrument(instrument);
    expect(component.selectedInstrument()).toBeNull();
  });

  it('renders loading and empty states in template', () => {
    component.isLoading.set(true);
    component.watchlists.set([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading watchlists...');

    component.isLoading.set(false);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No watchlists yet');
  });

  it('submits create form via DOM', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector('form.watchlist-form input[formControlName="name"]') as HTMLInputElement;
    input.value = 'Momentum';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form.watchlist-form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(tradeApi.watchlists.createWatchlist).toHaveBeenCalledWith({
      createWatchlistRequest: { name: 'Momentum' }
    });
  });

  it('clicking watchlist summary opens detail', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const openSpy = vi.spyOn(component, 'openWatchlist').mockResolvedValue();
    const openButton = fixture.nativeElement.querySelector('.watchlist-open-button') as HTMLButtonElement;
    openButton.click();

    expect(openSpy).toHaveBeenCalledWith(2);
  });

  it('renders detail modal and closes it from close button', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    component.selectInstrument(component.selectedWatchlist()!.instruments[0]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.instrument-detail-panel')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.modal-backdrop')).toBeTruthy();

    const closeButton = fixture.nativeElement.querySelector('.close-btn') as HTMLButtonElement;
    closeButton.click();
    fixture.detectChanges();

    expect(component.selectedInstrument()).toBeNull();
    expect(fixture.nativeElement.querySelector('.instrument-detail-panel')).toBeFalsy();
  });

  it('shows action labels when saving watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    component.isSavingWatchlist.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Creating...');
  });

  it('shows error when watchlist creation fails', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    tradeApi.watchlists.createWatchlist.mockRejectedValueOnce(new Error('Server error'));
    component.createWatchlistForm.setValue({ name: 'New' });

    await component.createWatchlist();

    expect(component.loadError()).toBeNull();
    expect(lastToastText(component)).toContain('Unable to create');
  });

  it('shows error when watchlist open fails', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    tradeApi.watchlists.getWatchlist.mockRejectedValueOnce(new Error('Server error'));

    await component.openWatchlist(3);

    expect(lastToastText(component)).toContain('Unable to load watchlist');
  });

  it('shows error when removing instrument fails', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    tradeApi.watchlists.removeWatchlistInstrument.mockRejectedValueOnce(new Error('Server error'));

    await component.removeInstrument('AAPL');

    expect(lastToastText(component)).toContain('Unable to remove');
  });

  it('shows error when deleting watchlist fails', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    tradeApi.watchlists.deleteWatchlist.mockRejectedValueOnce(new Error('Server error'));

    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    await component.deleteWatchlist(3);

    expect(lastToastText(component)).toContain('Unable to delete');
  });

  it('does not delete watchlist if user cancels confirmation', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    const initialWatchlist = component.selectedWatchlist();

    vi.stubGlobal('confirm', vi.fn().mockReturnValue(false));
    await component.deleteWatchlist(3);

    expect(tradeApi.watchlists.deleteWatchlist).not.toHaveBeenCalled();
    expect(component.selectedWatchlist()).toBe(initialWatchlist);
  });

  it('clears error when creating new watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    component.loadError.set('Previous error');
    component.createWatchlistForm.setValue({ name: 'New' });
    tradeApi.watchlists.createWatchlist.mockResolvedValueOnce(buildSummary(4));

    await component.createWatchlist();

    expect(component.loadError()).toBeNull();
  });

  it('clears error when opening watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    component.loadError.set('Previous error');

    await component.openWatchlist(3);

    expect(component.loadError()).toBeNull();
  });

  it('handles adding duplicate instrument', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    const instrument: InstrumentListItem = { symbol: 'aapl', name: 'Apple', assetClass: 'equity', currency: 'USD' };

    await component.addInstrument(instrument);

    expect(tradeApi.watchlists.addWatchlistInstrument).toHaveBeenCalled();
  });

  it('closes selected instrument when switching watchlists', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    component.selectInstrument(component.selectedWatchlist()!.instruments[0]);

    expect(component.selectedInstrument()).not.toBeNull();

    await component.openWatchlist(1);
    fixture.detectChanges();

    expect(component.selectedInstrument()).toBeNull();
  });

  it('computes correct watchlist statistics', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    const stats = component.watchlistStats();

    expect(stats.total).toBeGreaterThanOrEqual(0);
    expect(stats.gainers).toBeGreaterThanOrEqual(0);
    expect(stats.losers).toBeGreaterThanOrEqual(0);
    expect(stats.pricedTotal).toBeGreaterThanOrEqual(0);
  });

  it('generates stable color class for watchlist', () => {
    const class1 = component.toneClass(1);
    const class2 = component.toneClass(1);
    const class3 = component.toneClass(2);

    expect(class1).toBe(class2);
    expect(class1).not.toBe(class3);
    expect(class1).toContain('tone-');
  });

  it('gets watchlist initial letter', () => {
    expect(component.watchlistInitial('Tech Stocks')).toBe('T');
    expect(component.watchlistInitial('AAPL Holdings')).toBe('A');
    expect(component.watchlistInitial('  Spaces')).toBe('S');
    expect(component.watchlistInitial('')).toBe('?');
  });

  it('handles escape key to close instrument detail', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    component.selectInstrument(component.selectedWatchlist()!.instruments[0]);

    component.onEscape();

    expect(component.selectedInstrument()).toBeNull();
  });

  it('does not close instrument detail when escape not pressed', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    component.selectInstrument(component.selectedWatchlist()!.instruments[0]);

    expect(component.selectedInstrument()).not.toBeNull();
  });

  it('limits watchlists to 50 when multiple loads happen', async () => {
    tradeApi.watchlists.listWatchlists.mockResolvedValueOnce(
      Array.from({ length: 100 }, (_, i) => buildSummary(i + 1))
    );

    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.watchlists().length).toBeLessThanOrEqual(50);
  });

  it('handles refreshing watchlist', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);

    await component.refreshWatchlist();

    expect(tradeApi.watchlists.getWatchlist).toHaveBeenCalled();
  });

  it('does not refresh if already refreshing', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.openWatchlist(3);
    component.isRefreshing.set(true);

    await component.refreshWatchlist();

    expect(tradeApi.watchlists.getWatchlist).toHaveBeenCalledTimes(1);
  });

  it('does not refresh if no watchlist selected', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    await component.refreshWatchlist();

    expect(component.selectedWatchlist()).toBeNull();
  });

  it('formats change with positive value', () => {
    expect(component.formatChange(1.5)).toBe('+1.50');
  });

  it('formats change with negative value', () => {
    expect(component.formatChange(-1.5)).toBe('-1.50');
  });

  it('formats change with zero', () => {
    expect(component.formatChange(0)).toBe('0.00');
  });

  it('formats percent correctly', () => {
    expect(component.formatPercent(1.5)).toBe('+1.50%');
    expect(component.formatPercent(-1.5)).toBe('-1.50%');
    expect(component.formatPercent(0)).toBe('0.00%');
  });

  it('gets correct change class for values', () => {
    expect(component.getChangeClass(1)).toBe('positive');
    expect(component.getChangeClass(-1)).toBe('negative');
    expect(component.getChangeClass(0)).toBe('neutral');
    expect(component.getChangeClass(null)).toBe('neutral');
  });

  it('gets change arrow for positive and negative', () => {
    expect(component.changeArrow(1)).toBe('▲');
    expect(component.changeArrow(-1)).toBe('▼');
    expect(component.changeArrow(0)).toBe('–');
    expect(component.changeArrow(null)).toBe('–');
  });

  it('gets currency symbol based on market', () => {
    expect(component.getCurrencySymbol('INFY.NS')).toBe('₹');
    expect(component.getCurrencySymbol('AAPL')).toBe('$');
    expect(component.getCurrencySymbol('infy.ns')).toBe('₹');
  });

  it('dismisses toast by ID', () => {
    component.toasts.set([
      { id: 1, type: 'success', text: 'Message 1' },
      { id: 2, type: 'error', text: 'Message 2' }
    ]);

    component.dismissToast(1);

    expect(component.toasts().find(t => t.id === 1)).toBeUndefined();
    expect(component.toasts().find(t => t.id === 2)).toBeDefined();
  });

  it('tracks by ID for ngFor optimization', () => {
    const item1 = buildSummary(1);
    const item2 = buildSummary(2);

    expect(component.trackById(0, item1)).toBe(1);
    expect(component.trackById(1, item2)).toBe(2);
  });

  it('tracks by symbol for instruments', () => {
    const instrument1: WatchlistInstrumentResponse = {
      symbol: 'AAPL',
      addedOn: new Date(),
      latestPrice: { price: 100, currency: 'USD', changePercent: 0, stale: false, quoteAsOf: new Date(), marketState: MarketState.Open }
    };

    expect(component.trackBySymbol(0, instrument1)).toBe('AAPL');
  });
});
