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

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);

  private readonly host = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
  private readonly baseUrl = (typeof window !== 'undefined' && window.location.port !== '8081')
    ? `http://${this.host}:8081/api/v1/chat/message`
    : '/api/v1/chat/message';

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
