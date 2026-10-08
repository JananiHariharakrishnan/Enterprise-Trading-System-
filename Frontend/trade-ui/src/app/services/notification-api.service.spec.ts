import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationApiService, UserNotification } from './notification-api.service';
import { TokenStorageService } from './token-storage.service';

describe('NotificationApiService', () => {
  let service: NotificationApiService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const note: UserNotification = {
    id: 4,
    category: 'ORDER_FILLED',
    title: 'Order filled',
    body: 'AAPL was filled',
    symbol: 'AAPL',
    emailSent: false,
    createdAt: '2026-01-01T00:00:00.000Z',
    readAt: null
  };

  const baseUrl = () => `http://${window.location.hostname}:8081/api/v1/notifications`;

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue('token-1') };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        NotificationApiService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(NotificationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('returns the notification list', async () => {
    const pending = service.list();
    const req = http.expectOne(baseUrl());

    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    req.flush([note]);

    await expect(pending).resolves.toEqual([note]);
  });

  it('returns an empty list when the payload is not an array', async () => {
    const pending = service.list();
    http.expectOne(baseUrl()).flush({ unexpected: true });

    await expect(pending).resolves.toEqual([]);
  });

  it('marks every notification read', async () => {
    const pending = service.markAllRead();
    const req = http.expectOne(baseUrl());

    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);

    await expect(pending).resolves.toBeUndefined();
  });

  it('sends no authorization header without a token', async () => {
    tokenStorage.getAccessToken.mockReturnValue(null);

    const pending = service.list();
    const req = http.expectOne(baseUrl());

    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
    await pending;
  });
});
