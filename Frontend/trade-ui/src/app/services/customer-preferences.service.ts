import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export type NotificationChannel = 'PUSH' | 'IN_APP' | 'EMAIL' | 'SMS';
export type ThemeChoice = 'LIGHT' | 'DARK' | 'SYSTEM';
export type BaseCurrency = 'USD' | 'INR';
export type OrderType = 'MARKET' | 'LIMIT' | 'STOP';

export type CustomerPreferences = {
  accountId: number;
  defaultAccountId: number;
  trading: {
    defaultOrderType: OrderType;
    skipOrderConfirmation: boolean;
    defaultTradeQuantity: number;
  };
  notifications: {
    tradeFills: NotificationChannel[];
    priceAlerts: NotificationChannel[];
    emailEnabled?: boolean;
  };
  display: {
    theme: ThemeChoice;
    baseCurrency: BaseCurrency;
  };
  updatedAt?: string;
};

import { getTradeApiV1Url } from '../config/api.config';

@Injectable({
  providedIn: 'root',
})
export class CustomerPreferencesService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly baseUrl = getTradeApiV1Url();

  defaultPreferences(accountId: number): CustomerPreferences {
    return {
      accountId,
      defaultAccountId: accountId,
      trading: {
        defaultOrderType: 'MARKET',
        skipOrderConfirmation: false,
        defaultTradeQuantity: 1,
      },
      notifications: {
        tradeFills: ['IN_APP'],
        priceAlerts: ['IN_APP'],
        emailEnabled: false,
      },
      display: {
        theme: 'SYSTEM',
        baseCurrency: 'INR',
      },
    };
  }

  async getPreferences(accountId: number): Promise<CustomerPreferences | null> {
    const headers = this.getHeaders();
    const targetAccountId = this.resolveAuthenticatedAccountId(accountId);
    const url = `${this.baseUrl}/accounts/${targetAccountId}/preferences`;

    try {
      return await firstValueFrom(this.http.get<CustomerPreferences>(url, { headers }));
    } catch (error: any) {
      if (error?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  async upsertPreferences(accountId: number, payload: Omit<CustomerPreferences, 'accountId' | 'updatedAt'>): Promise<CustomerPreferences> {
    const headers = this.getHeaders();
    const targetAccountId = this.resolveAuthenticatedAccountId(accountId);
    const url = `${this.baseUrl}/accounts/${targetAccountId}/preferences`;
    return firstValueFrom(this.http.put<CustomerPreferences>(url, payload, { headers }));
  }

  private getHeaders(): HttpHeaders {
    const token = this.tokenStorage.getAccessToken();
    if (!token) {
      return new HttpHeaders();
    }

    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private resolveAuthenticatedAccountId(fallbackAccountId: number): number {
    const token = this.tokenStorage.getAccessToken();
    if (!token) {
      return fallbackAccountId;
    }

    const parts = token.split('.');
    if (parts.length < 2) {
      return fallbackAccountId;
    }

    try {
      const payloadRaw = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
      const payload = JSON.parse(payloadRaw) as { accountId?: unknown };
      if (typeof payload.accountId === 'number' && Number.isFinite(payload.accountId)) {
        return payload.accountId;
      }
    } catch {
      // Fallback to provided value when token payload is not parseable.
    }

    return fallbackAccountId;
  }
}
