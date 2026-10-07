import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TradeApiClientService } from '../../services/trade-api-client.service';
import type { AlertResponse } from '../../generated/trade';

type ToastType = 'success' | 'error';

interface Toast {
  id: number;
  type: ToastType;
  text: string;
}

const TOAST_SUCCESS_MS = 3500;
const TOAST_ERROR_MS = 5000;
const TOAST_MAX_VISIBLE = 3;

@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './alerts.component.html',
  styleUrls: ['./alerts.component.css']
})
export class AlertsComponent implements OnInit, OnDestroy {
  private readonly api = inject(TradeApiClientService);
  private readonly fb = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly toasts = signal<Toast[]>([]);
  private readonly toastTimers = new Map<number, ReturnType<typeof setTimeout>>();
  private toastSeq = 0;
  
  readonly alerts = signal<AlertResponse[]>([]);
  readonly isSaving = signal(false);

  readonly createAlertForm = this.fb.nonNullable.group({
    symbol: ['', [Validators.required, Validators.pattern('^[A-Z0-9.]{1,20}$')]],
    direction: ['ABOVE' as 'ABOVE' | 'BELOW', [Validators.required]],
    threshold: [null as number | null, [Validators.required, Validators.min(0.01)]]
  });

  ngOnInit(): void {
    void this.load();
  }

  ngOnDestroy(): void {
    this.toastTimers.forEach((timer) => clearTimeout(timer));
    this.toastTimers.clear();
  }

  // ---------- Toasts ----------

  private showToast(type: ToastType, text: string): void {
    const id = ++this.toastSeq;
    this.toasts.update((list) => [...list.slice(-(TOAST_MAX_VISIBLE - 1)), { id, type, text }]);
    this.toastTimers.set(
      id,
      setTimeout(() => this.dismissToast(id), type === 'error' ? TOAST_ERROR_MS : TOAST_SUCCESS_MS)
    );
  }

  dismissToast(id: number): void {
    const timer = this.toastTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.toastTimers.delete(id);
    }
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private async load(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);
    try {
      const items = await this.api.priceAlerts.listAlerts();
      this.alerts.set(items);
    } catch (error) {
      this.loadError.set('Unable to load alerts. Please refresh and try again.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async createAlert(): Promise<void> {
    this.loadError.set(null);

    if (this.createAlertForm.invalid) {
      this.createAlertForm.markAllAsTouched();
      return;
    }

    this.isSaving.set(true);
    try {
      const values = this.createAlertForm.getRawValue();
      await this.api.priceAlerts.createAlert({
        createAlertRequest: {
          symbol: values.symbol.trim().toUpperCase(),
          direction: values.direction,
          threshold: Number(values.threshold)
        }
      });
      await this.load();
      this.createAlertForm.reset({ symbol: '', direction: 'ABOVE', threshold: null });
      this.showToast('success', 'Alert created successfully.');
    } catch {
      this.showToast('error', 'Unable to create alert right now.');
    } finally {
      this.isSaving.set(false);
    }
  }

  async cancelAlert(alertId: number): Promise<void> {
    this.isSaving.set(true);
    try {
      await this.api.priceAlerts.cancelAlert({ alertId });
      await this.load();
      this.showToast('success', 'Alert cancelled.');
    } catch {
      this.showToast('error', 'Unable to cancel alert right now.');
    } finally {
      this.isSaving.set(false);
    }
  }

  trackById(_: number, item: AlertResponse): number {
    return item.id;
  }
}

