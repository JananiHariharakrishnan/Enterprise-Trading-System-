import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export type UserNotification = {
  id: number;
  category: string;
  title: string;
  body: string;
  symbol?: string | null;
  emailSent: boolean;
  createdAt: string;
  readAt: string | null;
};

import { getTradeApiV1Url } from '../config/api.config';

@Injectable({
  providedIn: 'root',
})
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly baseUrl = `${getTradeApiV1Url()}/notifications`;

  async list(): Promise<UserNotification[]> {
    const rows = await firstValueFrom(
      this.http.get<UserNotification[]>(this.baseUrl, { headers: this.getHeaders() })
    );
    return Array.isArray(rows) ? rows : [];
  }

  async markAllRead(): Promise<void> {
    await firstValueFrom(
      this.http.post(this.baseUrl, {}, { headers: this.getHeaders() })
    );
  }

  private getHeaders(): HttpHeaders {
    const token = this.tokenStorage.getAccessToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
