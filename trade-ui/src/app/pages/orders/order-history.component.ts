import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import { AuthSessionService } from '../../services/auth-session.service';
import type { OrderHistoryEntry } from '../../generated/trade';

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [CommonModule, DatePipe, ReactiveFormsModule],
  templateUrl: './order-history.component.html',
  styleUrls: ['./order-history.component.css']
})
export class OrderHistoryComponent implements OnInit {
  private readonly api = inject(TradeApiClientService);
  private readonly auth = inject(AuthSessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly orders = signal<OrderHistoryEntry[]>([]);

  // Filter signals
  readonly statusOptions = ['ALL', 'NEW', 'FILLED', 'REJECTED', 'CANCELLED'];
  readonly selectedStatus = signal<string>('ALL');
  readonly fromDate = signal<string>('');
  readonly toDate = signal<string>('');

  readonly filterForm = this.fb.nonNullable.group({
    status: ['ALL', Validators.required],
    fromDate: ['', Validators.required],
    toDate: ['', Validators.required]
  });

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

      void this.loadOrders();
    });
  }

  private getDefaultFromDate(): string {
    const date = new Date();
    date.setDate(date.getDate() - 30); // 30 days back
    return date.toISOString().split('T')[0];
  }

  private getDefaultToDate(): string {
    return new Date().toISOString().split('T')[0];
  }

  applyFilters(): void {
    if (this.filterForm.invalid) {
      return;
    }

    const { status, fromDate, toDate } = this.filterForm.value;

    // Update query parameters in URL
    this.router.navigate([], {
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

      this.orders.set(fetched.slice(0, 500));
    } catch (err) {
      this.loadError.set('Unable to load order history. Please check your filters.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
