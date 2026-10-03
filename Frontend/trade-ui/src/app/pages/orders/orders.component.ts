import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import type { PlaceOrderRequest, OrderHistoryEntry, AccountResponse, OrderResponse } from '../../generated/trade';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.css']
})
export class OrdersComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthSessionService);
  private api = inject(TradeApiClientService);

  readonly loading = signal(false);
  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly account = signal<AccountResponse | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    symbol: ['', [Validators.required]],
    side: ['BUY', [Validators.required]],
    quantity: [1, [Validators.required, Validators.min(1), Validators.pattern('^[0-9]+$')]],
    price: [0.01, [Validators.required, Validators.min(0.01), Validators.pattern('^\\d+(\\.\\d{1,2})?$')]],
    orderType: ['MARKET', [Validators.required]],
  });

  async ngOnInit(): Promise<void> {
    // load account info for read-only display
    const user = await this.auth.loadCurrentUser();
    if (user) {
      try {
        const acct = await this.api.accounts.getAccount({ id: user.accountId });
        this.account.set(acct);
      } catch (e) {
        console.error('Failed to load account for orders page', e);
      }
    }
  }
  // loadOrders removed — recent orders UI was removed intentionally

  async submit(): Promise<void> {
    if (this.form.invalid) return;
    const user = await this.auth.loadCurrentUser();
    if (!user) throw new Error('Not authenticated');

    const payload: PlaceOrderRequest = {
      accountId: user.accountId,
      symbol: this.form.value.symbol,
      side: this.form.value.side,
      quantity: Number(this.form.value.quantity),
      price: Number(this.form.value.price),
      orderType: this.form.value.orderType,
      // generate idempotency key in background (do not expose in UI)
      idempotencyKey: crypto.randomUUID(),
    } as PlaceOrderRequest;

    try {
      const result: OrderResponse = await this.api.orders.placeOrder({ placeOrderRequest: payload });
      // show success message including returned status when available
      const status = (result as any)?.status ?? (result as any)?.order?.status ?? 'UNKNOWN';
      this.successMessage.set(`Order placed — status: ${status}`);
      this.errorMessage.set(null);
      console.log('Order placed', result);
      // reset form (keep orderType)
      const currentType = this.form.value.orderType;
      this.form.reset({ symbol: '', side: 'BUY', quantity: 1, price: 0.01, orderType: currentType });
    } catch (err: unknown) {
      console.error('Place order failed', err);
      // Map business-rule rejection if present
      const mapped = err && typeof err === 'object' && 'message' in (err as any) ? (err as any).message : 'Failed to place order';
      this.errorMessage.set(String(mapped));
      this.successMessage.set(null);
    }
  }
}
