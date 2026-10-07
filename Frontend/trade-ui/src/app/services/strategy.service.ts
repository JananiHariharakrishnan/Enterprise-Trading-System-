import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export interface StrategyResponse {
  strategyId: string;
  accountId: number;
  symbol: string;
  type: 'MA_CROSSOVER' | 'BOLLINGER_BANDS';
  quantity: number;
  enabled: boolean;
  currentSpend: number;
  maxSpend: number;
  maxPositionSize: number;
  consecutiveFailures: number;
  maxConsecutiveFailures: number;
}

export interface CreateStrategyRequest {
  strategyId?: string;
  accountId?: number;
  symbol: string;
  type: 'MA_CROSSOVER' | 'BOLLINGER_BANDS';
  quantity: number;
  fastPeriod?: number;
  slowPeriod?: number;
  bbPeriod?: number;
  bbStdDev?: number;
  maxSpend?: number;
  maxPositionSize?: number;
  maxConsecutiveFailures?: number;
}

@Injectable({
  providedIn: 'root'
})
export class StrategyService {
  private http = inject(HttpClient);
  private tokenStorage = inject(TokenStorageService);

  private get baseUrl(): string {
    const baseHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
    return `http://${baseHost}:8081/api/v1/strategies`;
  }

  private getHeaders(): HttpHeaders {
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    const token = this.tokenStorage.getAccessToken();
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  getStrategies(): Observable<StrategyResponse[]> {
    return this.http.get<StrategyResponse[]>(this.baseUrl, { headers: this.getHeaders() });
  }

  createStrategy(request: CreateStrategyRequest): Observable<StrategyResponse> {
    return this.http.post<StrategyResponse>(this.baseUrl, request, { headers: this.getHeaders() });
  }

  disableStrategy(strategyId: string): Observable<StrategyResponse> {
    return this.http.post<StrategyResponse>(`${this.baseUrl}/${strategyId}/disable`, {}, { headers: this.getHeaders() });
  }

  enableStrategy(strategyId: string): Observable<StrategyResponse> {
    return this.http.post<StrategyResponse>(`${this.baseUrl}/${strategyId}/enable`, {}, { headers: this.getHeaders() });
  }

  simulateTick(symbol: string, price: number): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/simulate-tick`, { symbol, price }, { headers: this.getHeaders() });
  }
}

