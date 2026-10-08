import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CustomerPreferencesService } from './customer-preferences.service';
import { TokenStorageService } from './token-storage.service';

function tokenWithAccount(accountId: number): string {
  const body = btoa(JSON.stringify({ accountId })).replace(/\+/g, '-').replace(/\//g, '_');
  return `header.${body}.sig`;
}

describe('CustomerPreferencesService', () => {
  let service: CustomerPreferencesService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const prefsUrl = (accountId: number) =>
    `http://${window.location.hostname}:8081/api/v1/accounts/${accountId}/preferences`;

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue(null) };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        CustomerPreferencesService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(CustomerPreferencesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('builds INR defaults for a new account', () => {
    const defaults = service.defaultPreferences(44);
    expect(defaults.accountId).toBe(44);
    expect(defaults.display.baseCurrency).toBe('INR');
    expect(defaults.notifications.emailEnabled).toBe(false);
    expect(defaults.trading.defaultOrderType).toBe('MARKET');
  });

  it('returns null when preferences are missing', async () => {
    const pending = service.getPreferences(7);
    const req = http.expectOne(prefsUrl(7));
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush('missing', { status: 404, statusText: 'Not Found' });
    await expect(pending).resolves.toBeNull();
  });

  it('rethrows errors other than 404', async () => {
    const pending = service.getPreferences(7);
    http.expectOne(prefsUrl(7)).flush('down', { status: 500, statusText: 'Server Error' });
    await expect(pending).rejects.toBeTruthy();
  });

  it('uses the account id inside the access token', async () => {
    tokenStorage.getAccessToken.mockReturnValue(tokenWithAccount(99));
    const pending = service.getPreferences(7);
    const req = http.expectOne(prefsUrl(99));
    expect(req.request.headers.get('Authorization')).toContain('Bearer ');
    req.flush(service.defaultPreferences(99));
    await expect(pending).resolves.toMatchObject({ accountId: 99 });
  });

  it('falls back when the token payload is not a number or cannot be parsed', async () => {
    tokenStorage.getAccessToken.mockReturnValue('not-a-jwt');
    const first = service.getPreferences(3);
    http.expectOne(prefsUrl(3)).flush(service.defaultPreferences(3));
    await first;

    const bad = `h.${btoa('{"accountId":"nope"}')}.s`;
    tokenStorage.getAccessToken.mockReturnValue(bad);
    const second = service.getPreferences(3);
    http.expectOne(prefsUrl(3)).flush(service.defaultPreferences(3));
    await second;

    tokenStorage.getAccessToken.mockReturnValue('h.%%% .s');
    const third = service.getPreferences(3);
    http.expectOne(prefsUrl(3)).flush(service.defaultPreferences(3));
    await third;
  });

  it('puts preferences for the authenticated account', async () => {
    tokenStorage.getAccessToken.mockReturnValue(tokenWithAccount(12));
    const payload = service.defaultPreferences(12);
    const pending = service.upsertPreferences(1, payload);
    const req = http.expectOne(prefsUrl(12));
    expect(req.request.method).toBe('PUT');
    req.flush({ ...payload, updatedAt: '2026-10-07T00:00:00Z' });
    await expect(pending).resolves.toMatchObject({ accountId: 12 });
  });
});
