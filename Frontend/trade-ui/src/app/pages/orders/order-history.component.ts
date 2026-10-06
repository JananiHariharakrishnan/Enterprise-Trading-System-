import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import {
  AbstractControl,
  ReactiveFormsModule,
  FormBuilder,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { AuthSessionService } from '../../services/auth-session.service';
import type { OrderHistoryEntry } from '../../generated/trade';

/** The "From" date must not be after the "To" date. */
function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const from = group.get('fromDate')?.value as string | undefined;
  const to = group.get('toDate')?.value as string | undefined;
  // ISO dates (YYYY-MM-DD) compare correctly as plain strings
  return from && to && from > to ? { dateRange: true } : null;
}

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [CommonModule, DatePipe, ReactiveFormsModule],
  templateUrl: './order-history.component.html',
  styleUrls: ['./order-history.component.css']
})
export class OrderHistoryComponent implements OnInit, OnDestroy {
  private readonly api = inject(TradeApiClientService);
  private readonly auth = inject(AuthSessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly orders = signal<OrderHistoryEntry[]>([]);

  /** The most orders shown at once. */
  readonly maxOrders = 500;

  // Polling signals
  readonly isPolling = signal(false);
  readonly pollAttempts = signal(0);
  readonly maxPollAttempts = 12;
  readonly pollIntervalMs = 5000; // 5 seconds
  private pollTimeoutId: ReturnType<typeof setTimeout> | null = null;

  // Computed signal: true if any order has status === 'NEW'
  readonly hasNewOrders = computed(() => {
    return this.orders().some(order => order.status === 'NEW');
  });

  /** Counts by status for the summary row. */
  readonly summary = computed(() => {
    const list = this.orders();
    const count = (status: string) => list.filter(order => order.status === status).length;
    return {
      total: list.length,
      open: count('NEW'),
      filled: count('FILLED'),
      rejected: count('REJECTED'),
      cancelled: count('CANCELLED')
    };
  });

  // Filter signals
  readonly statusOptions = ['ALL', 'NEW', 'FILLED', 'REJECTED', 'CANCELLED'];
  readonly quickRanges = [
    { label: '7 days', days: 7 },
    { label: '30 days', days: 30 },
    { label: '90 days', days: 90 }
  ];
  readonly selectedStatus = signal<string>('ALL');
  readonly fromDate = signal<string>('');
  readonly toDate = signal<string>('');

  // Cancel order state
  readonly cancelling = signal<Set<string>>(new Set());

  readonly filterForm = this.fb.nonNullable.group(
    {
      status: ['ALL', Validators.required],
      fromDate: ['', Validators.required],
      toDate: ['', Validators.required]
    },
    { validators: dateRangeValidator }
  );

  ngOnInit(): void {
    // Read query parameters from URL
    this.route.queryParams.subscribe((params) => {
      const status = params['status'] || 'ALL';
      const fromDate = params['from'] || this.getDefaultFromDate();
      const toDate = params['to'] || this.getDefaultToDate();

      this.selectedStatus.set(status);
      this.fromDate.set(fromDate);
      this.toDate.set(toDate);

      this.filterForm.patchValue({
        status,
        fromDate,
        toDate
      });

      // New filters start a fresh polling cycle
      this.stopPolling();
      this.pollAttempts.set(0);
      void this.loadOrders();
    });
  }

  /** Local calendar date as YYYY-MM-DD (toISOString would use UTC and can be a day off). */
  private toLocalDateString(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private dateDaysAgo(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return this.toLocalDateString(date);
  }

  private getDefaultFromDate(): string {
    return this.dateDaysAgo(30); // 30 days back
  }

  private getDefaultToDate(): string {
    return this.toLocalDateString(new Date());
  }

  applyFilters(): void {
    if (this.filterForm.invalid) {
      return;
    }

    const { status, fromDate, toDate } = this.filterForm.value;

    // Update query parameters in URL
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        status: status === 'ALL' ? null : status,
        from: fromDate,
        to: toDate
      },
      queryParamsHandling: 'merge'
    });
  }

  clearFilters(): void {
    this.filterForm.reset({
      status: 'ALL',
      fromDate: this.getDefaultFromDate(),
      toDate: this.getDefaultToDate()
    });
    this.applyFilters();
  }

  /** Status buttons apply straight away. */
  setStatus(status: string): void {
    this.filterForm.controls.status.setValue(status);
    this.applyFilters();
  }

  /** Quick range buttons ("7 days" etc.) apply straight away. */
  setQuickRange(days: number): void {
    this.filterForm.patchValue({
      fromDate: this.dateDaysAgo(days),
      toDate: this.getDefaultToDate()
    });
    this.applyFilters();
  }

  isActiveRange(days: number): boolean {
    return this.fromDate() === this.dateDaysAgo(days) && this.toDate() === this.getDefaultToDate();
  }

  /** Get the currency symbol for display */
  getCurrencySymbol(currency: string | undefined): string {
    if (!currency) return '$'; // Default to USD
    if (currency.toUpperCase() === 'INR') return '₹';
    if (currency.toUpperCase() === 'USD') return '$';
    return '$'; // Default fallback
  }

  /** Currency the symbol trades in: the order's own currency, else guessed from the exchange suffix. */
  instrumentCurrency(order: OrderHistoryEntry): string {
    const explicit = (order as unknown as Record<string, unknown>)['currency'];
    if (explicit) return String(explicit).toUpperCase();
    const symbol = (order.symbol ?? '').toUpperCase();
    return symbol.endsWith('.NS') || symbol.endsWith('.BO') ? 'INR' : 'USD';
  }

  private async loadOrders(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);

    try {
      const user = await this.auth.loadCurrentUser();
      if (!user) {
        this.loadError.set('Not authenticated');
        return;
      }

      const status = this.selectedStatus();
      const fromDate = this.fromDate();
      const toDate = this.toDate();

      // Convert dates to ISO format strings as Date objects
      const fromDateObj = fromDate ? new Date(`${fromDate}T00:00:00Z`) : undefined;
      const toDateObj = toDate ? new Date(`${toDate}T23:59:59Z`) : undefined;

      // Build API call with filters
      const fetched = await this.api.accounts.getOrders({
        id: user.accountId,
        from: fromDateObj,
        to: toDateObj,
        status: status !== 'ALL' ? (status as any) : undefined
      });

      this.orders.set(fetched.slice(0, this.maxOrders));

      // Schedule next poll if there are NEW orders and we haven't exceeded max attempts
      this.scheduleNextPoll();
    } catch (err) {
      this.loadError.set('Unable to load order history. Please check your filters.');
      // Still attempt to reschedule polling on error
      this.scheduleNextPoll();
    } finally {
      this.isLoading.set(false);
    }
  }

  /**
   * Public method for users to manually refresh the order history.
   * Resets poll attempts and triggers an immediate load.
   */
  refresh(): void {
    this.stopPolling();
    this.pollAttempts.set(0);
    void this.loadOrders();
  }

  /**
   * Schedule the next poll if conditions are met:
   * - At least one order has status === 'NEW'
   * - Poll attempts haven't exceeded the maximum
   */
  private scheduleNextPoll(): void {
    if (!this.hasNewOrders() || this.pollAttempts() >= this.maxPollAttempts) {
      // Stop polling: either no NEW orders or max attempts reached
      this.stopPolling();
      return;
    }

    // Clear any pending timer first, then mark polling as active
    this.clearPollTimer();
    this.pollAttempts.update(count => count + 1);
    this.isPolling.set(true);

    // Schedule next load
    this.pollTimeoutId = setTimeout(() => {
      void this.loadOrders();
    }, this.pollIntervalMs);
  }

  private clearPollTimer(): void {
    if (this.pollTimeoutId) {
      clearTimeout(this.pollTimeoutId);
      this.pollTimeoutId = null;
    }
  }

  /** Cancel any pending poll and clear the "still working" state. */
  private stopPolling(): void {
    this.clearPollTimer();
    this.isPolling.set(false);
  }

  // ---------- Display helpers ----------

  statusKey(value?: string | null): string {
    return (value ?? 'unknown').toLowerCase();
  }

  /** Stable hue (0-359) per symbol, used to tint the symbol badges. */
  symbolHue(symbol: string): number {
    let hash = 0;
    for (const char of symbol ?? '') {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }

  trackByOrderId(_: number, order: OrderHistoryEntry): string | number {
    return order.orderId;
  }

  /**
   * Check if an order is currently being cancelled.
   */
  isCancelling(orderId: string): boolean {
    return this.cancelling().has(orderId);
  }

  /**
   * Cancel an order that is still NEW.
   */
  async cancelOrder(orderId: string): Promise<void> {
    // Mark as cancelling
    const cancelling = this.cancelling();
    cancelling.add(orderId);
    this.cancelling.set(new Set(cancelling));

    try {
      // Call the cancel API
      const response = await this.api.orders.cancelOrder({ id: orderId });

      if (response) {
        // Find and update the order in the list
        const orders = this.orders();
        const index = orders.findIndex(o => o.orderId === orderId);
        if (index !== -1) {
          const updated = [...orders];
          updated[index] = {
            ...updated[index],
            status: 'CANCELLED' as any  // Update status to CANCELLED
          };
          this.orders.set(updated);
        }
      }
    } catch (error) {
      // If cancel fails, show error to user (could add toast notification here)
      console.error('Failed to cancel order:', error);
      // Optionally show error message to user
    } finally {
      // Remove from cancelling set
      const cancelling = this.cancelling();
      cancelling.delete(orderId);
      this.cancelling.set(new Set(cancelling));
    }
  }

  /**
   * Angular lifecycle: cleanup when component is destroyed.
   */
  ngOnDestroy(): void {
    this.stopPolling();
  }
}
