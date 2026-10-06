import { AfterViewInit, Component, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { AuthSessionService } from '../../services/auth-session.service';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import type { PlaceOrderRequest, OrderHistoryEntry, AccountResponse, OrderResponse } from '../../generated/trade';
import { InstrumentSearchComponent, type InstrumentListItem } from '../../components/instrument-search/instrument-search.component';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, InstrumentSearchComponent],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.css']
})
export class OrdersComponent implements OnInit, AfterViewInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthSessionService);
  private api = inject(TradeApiClientService);
  private route = inject(ActivatedRoute);

  @ViewChild(InstrumentSearchComponent) instrumentSearchComponent?: InstrumentSearchComponent;

  readonly submitting = signal(false);
  readonly prefilling = signal(false);
  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly account = signal<AccountResponse | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly lastOrderResponse = signal<OrderResponse | null>(null);
  readonly selectedInstrument = signal<InstrumentListItem | null>(null);
  
  private currentIdempotencyKey: string | null = null;

  readonly form = this.fb.group({
    symbol: [''], // Not required - validated by selectedInstrument() instead
    side: ['BUY', [Validators.required]],
    quantity: [1, [Validators.required, Validators.min(1), Validators.pattern('^[0-9]+$')]],
    price: [null, []], // Validation is applied conditionally based on orderType
    orderType: ['MARKET', [Validators.required]],
  });

  /** Form values as a signal, so the summary and button label update as the user types. */
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.value });

  readonly side = computed<'BUY' | 'SELL'>(() => (this.formValue().side === 'SELL' ? 'SELL' : 'BUY'));
  readonly orderType = computed(() => (this.formValue().orderType === 'LIMIT' ? 'LIMIT' : 'MARKET'));

  /**
   * Dynamically update price field validation based on order type.
   * For LIMIT orders, price is required. For MARKET orders, price is optional.
   */
  constructor() {
    // Set up validator changes based on order type
    this.form.get('orderType')?.valueChanges.subscribe((orderType) => {
      const priceControl = this.form.get('price');
      if (!priceControl) return;

      if (orderType === 'LIMIT') {
        priceControl.setValidators([Validators.required, Validators.min(0.01), Validators.pattern('^\\d+(\\.\\d{1,2})?$')]);
        priceControl.updateValueAndValidity();
      } else {
        // MARKET order - price is not required
        priceControl.setValidators([]);
        priceControl.setValue(null);
        priceControl.updateValueAndValidity();
      }
    });
  }

  /** Quantity x price, or null until both are valid numbers. For MARKET orders, always null. */
  readonly estimatedTotal = computed<number | null>(() => {
    const v = this.formValue();
    if (v.orderType === 'MARKET') return null; // Market orders don't have a known price until execution
    const quantity = Number(v.quantity);
    const price = Number(v.price);
    return Number.isFinite(quantity) && Number.isFinite(price) && quantity > 0 && price > 0
      ? quantity * price
      : null;
  });

  readonly submitLabel = computed(() => {
    if (this.submitting()) return 'Placing order...';
    const instrument = this.selectedInstrument();
    if (!instrument) return 'Select an instrument';
    this.formValue(); // re-evaluate when the form changes
    if (this.form.invalid) return 'Fix the highlighted fields';
    const verb = this.side() === 'BUY' ? 'Buy' : 'Sell';
    return `${verb} ${Number(this.form.value.quantity)} ${instrument.symbol}`;
  });

  /** Exchange rate actually applied, derived from the response instead of being hard-coded. */
  readonly conversionRate = computed<number | null>(() => {
    const order = this.lastOrderResponse();
    const original = Number(order?.originalPrice);
    const converted = Number(order?.price);
    return original > 0 && converted > 0 ? original / converted : null;
  });

  async ngOnInit(): Promise<void> {
    // Links from the watchlist look like /orders?symbol=AAPL&side=buy
    const params = this.route.snapshot.queryParamMap;
    const side = params.get('side')?.toUpperCase();
    if (side === 'BUY' || side === 'SELL') {
      this.form.patchValue({ side });
    }

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

  ngAfterViewInit(): void {
    void this.prefillFromUrl();
  }

  private async prefillFromUrl(): Promise<void> {
    const symbol = this.route.snapshot.queryParamMap.get('symbol')?.trim();
    if (!symbol || !this.instrumentSearchComponent) return;

    this.prefilling.set(true);
    try {
      const found = await this.instrumentSearchComponent.findBySymbol(symbol);
      // If the lookup fails we still let the user trade the symbol from the link.
      this.onInstrumentSelected(
        found ?? { symbol: symbol.toUpperCase(), name: symbol.toUpperCase(), assetClass: null, currency: null }
      );
    } finally {
      this.prefilling.set(false);
    }
  }

  // ---------- Instrument ----------

  onInstrumentSelected(instrument: InstrumentListItem): void {
    this.selectedInstrument.set(instrument);
    this.form.patchValue({ symbol: instrument.symbol });
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.lastOrderResponse.set(null);
    // Clear cached idempotency key so new order gets a fresh key
    this.currentIdempotencyKey = null;
  }

  clearInstrument(): void {
    this.selectedInstrument.set(null);
    this.form.patchValue({ symbol: '' });
  }

  // ---------- Form helpers ----------

  changeQuantity(delta: number): void {
    const current = Math.floor(Number(this.form.value.quantity)) || 0;
    this.form.controls.quantity.setValue(Math.max(1, current + delta));
    this.form.controls.quantity.markAsDirty();
  }

  quantityError(): string | null {
    const control = this.form.controls.quantity;
    if (control.valid || !(control.touched || control.dirty)) return null;
    return 'Enter a whole number of shares, 1 or more.';
  }

  priceError(): string | null {
    const orderType = this.form.controls.orderType.value;
    if (orderType === 'MARKET') return null; // Price not required for market orders
    const control = this.form.controls.price;
    if (control.valid || !(control.touched || control.dirty)) return null;
    return 'Enter a price of $0.01 or more, with up to 2 decimals.';
  }

  formatAssetClass(value: string | null): string {
    const text = (value || 'equity').replace(/_/g, ' ').toLowerCase();
    return text.length <= 4 ? text.toUpperCase() : text.charAt(0).toUpperCase() + text.slice(1);
  }

  /** Stable hue per symbol, same as the badges on the watchlist page. */
  symbolHue(symbol: string): number {
    let hash = 0;
    for (const char of symbol) {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }

  // ---------- Submit ----------

  async submit(): Promise<void> {
    if (this.form.invalid || !this.selectedInstrument() || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    // Prevent duplicate submissions by generating idempotency key only once
    if (!this.currentIdempotencyKey) {
      this.currentIdempotencyKey = crypto.randomUUID();
    }

    this.submitting.set(true);
    try {
      const user = await this.auth.loadCurrentUser();
      if (!user) throw new Error('Not authenticated');

      const selectedOrderType = this.form.value.orderType as 'MARKET' | 'LIMIT';
      const payload: PlaceOrderRequest = {
        accountId: user.accountId,
        symbol: this.form.value.symbol ?? '',
        side: this.form.value.side as 'BUY' | 'SELL',
        quantity: Number(this.form.value.quantity),
        orderType: selectedOrderType,
        // For MARKET orders, price is null. For LIMIT orders, price is required.
        price: selectedOrderType === 'MARKET' ? null : Number(this.form.value.price),
        // Use cached idempotency key to prevent duplicates on retry
        idempotencyKey: this.currentIdempotencyKey,
      } as PlaceOrderRequest;

      const result: OrderResponse = await this.api.orders.placeOrder({ placeOrderRequest: payload });
      this.lastOrderResponse.set(result); // Store for conversion display
      // show success message including returned status when available
      const status = (result as any)?.status ?? (result as any)?.order?.status ?? 'UNKNOWN';
      this.successMessage.set(`Order placed. Status: ${status}`);
      this.errorMessage.set(null);

      // reset form (keep side and order type so repeat orders are quick)
      const { side, orderType: prevOrderType } = this.form.value;
      this.form.reset({ 
        symbol: '', 
        side: side ?? 'BUY', 
        quantity: 1, 
        price: null,
        orderType: prevOrderType ?? 'MARKET' 
      });
      this.selectedInstrument.set(null);
      this.instrumentSearchComponent?.resetSearch();
      
      // Clear idempotency key for next order
      this.currentIdempotencyKey = null;
    } catch (err: unknown) {
      console.error('Place order failed', err);
      // Map business-rule rejection if present
      const mapped = err && typeof err === 'object' && 'message' in (err as any) ? (err as any).message : 'Failed to place order';
      this.errorMessage.set(String(mapped));
      this.successMessage.set(null);
      
      // Keep idempotency key on error - user can retry and backend will deduplicate
      // Only clear if it's a duplicate key error (which means order was already placed)
      const errorMessage = String(mapped).toLowerCase();
      if (errorMessage.includes('duplicate') || errorMessage.includes('409')) {
        this.currentIdempotencyKey = null;
      }
    } finally {
      this.submitting.set(false);
    }
  }
}
