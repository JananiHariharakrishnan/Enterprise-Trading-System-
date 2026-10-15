import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { TradeSignalResponse } from './signals.service';

export interface ChatMessageRequest {
  message: string;
}

export interface ChatMessageResponse {
  reply: string;
  relatedSignals?: TradeSignalResponse[];
}

import { getTradeApiV1Url } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);

  private readonly baseUrl = `${getTradeApiV1Url()}/chat/message`;

  private getHeaders(): HttpHeaders {
    const token = this.tokenStorage.getAccessToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  /**
   * Send a question to the AI chatbot and receive an enriched answer + related signals.
   */
  sendMessage(message: string): Observable<ChatMessageResponse> {
    return this.http.post<ChatMessageResponse>(
      this.baseUrl,
      { message } as ChatMessageRequest,
      { headers: this.getHeaders() }
    );
  }
}
