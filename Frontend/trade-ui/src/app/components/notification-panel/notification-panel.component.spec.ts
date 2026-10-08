import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationPanelComponent } from './notification-panel.component';
import { NotificationApiService, type UserNotification } from '../../services/notification-api.service';

const unread: UserNotification = {
  id: 1,
  category: 'PRICE_ALERT',
  title: 'Alert',
  body: 'AAPL moved above 100',
  symbol: '  aapl  ',
  emailSent: false,
  createdAt: '2026-10-07T00:00:00Z',
  readAt: null
};

describe('NotificationPanelComponent', () => {
  let fixture: ComponentFixture<NotificationPanelComponent>;
  let component: NotificationPanelComponent;
  let api: {
    list: ReturnType<typeof vi.fn>;
    markAllRead: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      list: vi.fn().mockResolvedValue([unread]),
      markAllRead: vi.fn().mockResolvedValue(undefined)
    };
    await TestBed.configureTestingModule({
      imports: [NotificationPanelComponent],
      providers: [{ provide: NotificationApiService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationPanelComponent);
    component = fixture.componentInstance;
  });

  it('loads notifications on init and counts unread rows', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    expect(api.list).toHaveBeenCalled();
    expect(component.unreadCount()).toBe(1);
    expect(component.isLoading()).toBe(false);
  });

  it('marks unread notifications when the panel opens and ignores mark-read failures', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    await component.toggle(new Event('click'));
    expect(component.isOpen()).toBe(true);
    expect(api.markAllRead).toHaveBeenCalled();
    expect(component.unreadCount()).toBe(0);

    api.markAllRead.mockRejectedValueOnce(new Error('down'));
    component.notifications.set([{ ...unread, readAt: null }]);
    await component.toggle(new Event('click'));
    await component.toggle(new Event('click'));
    expect(component.notifications()[0].readAt).toBeNull();
  });

  it('closes from the close action, outside clicks, and escape', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    component.isOpen.set(true);
    component.close();
    expect(component.isOpen()).toBe(false);

    component.isOpen.set(true);
    component.onDocumentClick(new MouseEvent('click'));
    expect(component.isOpen()).toBe(false);

    component.isOpen.set(true);
    const inside = new MouseEvent('click');
    Object.defineProperty(inside, 'target', { value: fixture.nativeElement });
    component.onDocumentClick(inside);
    expect(component.isOpen()).toBe(true);

    component.onEscape();
    expect(component.isOpen()).toBe(false);
    component.onDocumentClick(new MouseEvent('click'));
  });

  it('derives a symbol from the stored field, the body, or nothing', () => {
    expect(component.instrumentSymbol(unread)).toBe('aapl');
    expect(component.instrumentSymbol({ ...unread, symbol: ' ', body: 'msft moved above 1', category: 'PRICE_ALERT' })).toBe('MSFT');
    expect(component.instrumentSymbol({ ...unread, symbol: '', body: 'Buy AAPL was filled', category: 'ORDER_FILLED' })).toBe('AAPL');
    expect(component.instrumentSymbol({ ...unread, symbol: '', body: 'hello', category: 'OTHER' })).toBe('');
  });

  it('sizes the symbol tile and labels categories', () => {
    expect(component.symbolFontSize('AAPL')).toBe(13);
    expect(component.symbolFontSize('ABCDEF')).toBe(11);
    expect(component.symbolFontSize('RELIANCE')).toBe(9);
    expect(component.symbolFontSize('ABCDEFGHIJ')).toBe(8);
    expect(component.symbolFontSize('HDFCBANK.NS')).toBe(7);
    expect(component.categoryLabel('PRICE_ALERT')).toBe('Alert');
    expect(component.categoryLabel('ORDER_FILLED')).toBe('Order');
    expect(component.categoryLabel('SYSTEM')).toBe('Update');
  });

  it('shows a load error and clears the list', async () => {
    api.list.mockRejectedValueOnce(new Error('offline'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.errorMessage()).toBe('Unable to load notifications right now.');
    expect(component.notifications()).toEqual([]);
    component.isOpen.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Unable to load notifications');
  });

  it('renders the open list, an empty list, and a loading state', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    await component.toggle(new Event('click'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="notification-panel"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('aapl');

    component.notifications.set([]);
    component.errorMessage.set('');
    component.isLoading.set(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No notifications yet');

    component.isLoading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading notifications');
  });
});
