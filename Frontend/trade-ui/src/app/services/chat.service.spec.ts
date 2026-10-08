import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatService } from './chat.service';
import { TokenStorageService } from './token-storage.service';

describe('ChatService', () => {
  let service: ChatService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const chatUrl = () => {
    const host = window.location.hostname;
    return window.location.port !== '8081'
      ? `http://${host}:8081/api/v1/chat/message`
      : '/api/v1/chat/message';
  };

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue('token-1') };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        ChatService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(ChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('posts the chat message with a bearer token', () => {
    let reply: unknown;
    service.sendMessage('Should I buy AAPL?').subscribe((value) => {
      reply = value;
    });

    const req = http.expectOne(chatUrl());
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ message: 'Should I buy AAPL?' });
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');

    const payload = { reply: 'AAPL is a hold.', relatedSignals: [] };
    req.flush(payload);
    expect(reply).toEqual(payload);
  });

  it('omits the authorization header when signed out', () => {
    tokenStorage.getAccessToken.mockReturnValue('');

    service.sendMessage('hello').subscribe();
    const req = http.expectOne(chatUrl());

    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ reply: 'hi' });
  });
});
