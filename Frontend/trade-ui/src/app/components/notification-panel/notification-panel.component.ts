import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { NotificationApiService, type UserNotification } from '../../services/notification-api.service';

@Component({
  selector: 'app-notification-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-panel.component.html',
  styleUrl: './notification-panel.component.css'
})
export class NotificationPanelComponent implements OnInit {
  private readonly notificationApi = inject(NotificationApiService);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly isOpen = signal(false);
  readonly isLoading = signal(false);
  readonly errorMessage = signal('');
  readonly notifications = signal<UserNotification[]>([]);
  readonly unreadCount = computed(() => this.notifications().filter((item) => !item.readAt).length);

  ngOnInit(): void {
    void this.load();
  }

  async toggle(event: Event): Promise<void> {
    event.stopPropagation();
    const next = !this.isOpen();
    this.isOpen.set(next);
    if (!next) {
      return;
    }

    await this.load();
    if (this.unreadCount() === 0) {
      return;
    }

    try {
      await this.notificationApi.markAllRead();
      const readAt = new Date().toISOString();
      this.notifications.update((items) => items.map((item) => ({
        ...item,
        readAt: item.readAt ?? readAt
      })));
    } catch {
      // The list is still visible if marking read fails.
    }
  }

  close(): void {
    this.isOpen.set(false);
  }

  instrumentSymbol(item: UserNotification): string {
    const stored = item.symbol?.trim();
    if (stored) {
      return stored;
    }

    const body = item.body ?? '';
    if (item.category === 'PRICE_ALERT') {
      const alertMatch = body.match(/^([A-Za-z0-9.^_-]+)\s+moved\b/);
      if (alertMatch) {
        return alertMatch[1].toUpperCase();
      }
    }

    const filledMatch = body.match(/\b([A-Za-z0-9.^_-]+)\s+was filled\b/i);
    if (filledMatch) {
      return filledMatch[1].toUpperCase();
    }

    return '';
  }

  symbolFontSize(symbol: string): number {
    const length = Math.max(symbol.length, 1);
    if (length <= 4) {
      return 13;
    }
    if (length <= 6) {
      return 11;
    }
    if (length <= 8) {
      return 9;
    }
    if (length <= 10) {
      return 8;
    }
    return 7;
  }

  categoryLabel(category: string): string {
    if (category === 'PRICE_ALERT') {
      return 'Alert';
    }
    if (category === 'ORDER_FILLED') {
      return 'Order';
    }
    return 'Update';
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen()) {
      return;
    }
    const target = event.target;
    if (target instanceof Node && this.host.nativeElement.contains(target)) {
      return;
    }
    this.isOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.isOpen.set(false);
  }

  private async load(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set('');
    try {
      const rows = await this.notificationApi.list();
      this.notifications.set(rows);
    } catch {
      this.notifications.set([]);
      this.errorMessage.set('Unable to load notifications right now.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
