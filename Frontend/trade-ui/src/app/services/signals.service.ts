import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export interface TradeSignalResponse {
  symbol: string;
  action: 'BUY' | 'SELL' | 'HOLD';
  confidence: number;
  reasoning: string;
  generatedAt: string;
  methodology?: string;
  disclaimer?: string;
  rsi?: number;
  ema9?: number;
  ema21?: number;
  currentPrice?: number;
  candleCacheDuration?: string;
}

import { getTradeApiV1Url } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class SignalsService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);

  private readonly baseUrl = `${getTradeApiV1Url()}/signals`;

  private getHeaders(): HttpHeaders {
    const token = this.tokenStorage.getAccessToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  /**
   * Fetch all latest AI trade signals.
   */
  getAll(): Observable<TradeSignalResponse[]> {
    return this.http.get<TradeSignalResponse[]>(this.baseUrl, { headers: this.getHeaders() });
  }

  /**
   * Fetch latest AI trade signal for a specific symbol.
   */
  getBySymbol(symbol: string): Observable<TradeSignalResponse> {
    return this.http.get<TradeSignalResponse>(`${this.baseUrl}/${encodeURIComponent(symbol)}`, {
      headers: this.getHeaders()
    });
  }

  /**
   * Request on-demand AI signal generation for a symbol.
   */
  generate(symbol: string): Observable<TradeSignalResponse> {
    return this.http.post<TradeSignalResponse>(`${this.baseUrl}/generate/${encodeURIComponent(symbol)}`, {}, {
      headers: this.getHeaders()
    });
  }

  /**
   * Refresh all default portfolio signals.
   */
  refreshAll(): Observable<TradeSignalResponse[]> {
    return this.http.post<TradeSignalResponse[]>(`${this.baseUrl}/refresh`, {}, {
      headers: this.getHeaders()
    });
  }
}
