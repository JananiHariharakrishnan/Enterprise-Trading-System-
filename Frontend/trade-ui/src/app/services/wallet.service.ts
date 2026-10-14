import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export interface WalletAccount {
  accountId: number;
  accountReference: string;
  cashBalance: number;
  buyingPower: number;
  status: string;
}

export interface WalletDepositResponse {
  accountId: number;
  accountReference: string;
  previousBalance: number;
  depositedAmount: number;
  newCashBalance: number;
  newBuyingPower: number;
  message: string;
}

import { getTradeApiV1Url } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class WalletService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly baseUrl = `${getTradeApiV1Url()}/wallet`;

  private getHeaders(): HttpHeaders {
    const token = this.tokenStorage.getAccessToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  async getWalletDetails(): Promise<WalletAccount> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.get<WalletAccount>(this.baseUrl, { headers }));
  }

  async depositFunds(amount: number, password: string): Promise<WalletDepositResponse> {
    const headers = this.getHeaders();
    return firstValueFrom(
      this.http.post<WalletDepositResponse>(`${this.baseUrl}/deposit`, { amount, password }, { headers })
    );
  }
}
