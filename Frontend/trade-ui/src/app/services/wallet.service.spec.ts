import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TokenStorageService } from './token-storage.service';
import { WalletAccount, WalletService } from './wallet.service';

describe('WalletService', () => {
  let service: WalletService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const account: WalletAccount = {
    accountId: 1001,
    accountReference: 'ETP-1001',
    cashBalance: 12500,
    buyingPower: 12500,
    status: 'ACTIVE'
  };

  const walletUrl = () => `http://${window.location.hostname}:8081/api/v1/wallet`;

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue('token-1') };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        WalletService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(WalletService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads wallet details with a bearer token', async () => {
    const pending = service.getWalletDetails();
    const req = http.expectOne(walletUrl());

    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    req.flush(account);

    await expect(pending).resolves.toEqual(account);
  });

  it('omits the authorization header when no token is stored', async () => {
    tokenStorage.getAccessToken.mockReturnValue(null);

    const pending = service.getWalletDetails();
    const req = http.expectOne(walletUrl());

    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(account);
    await pending;
  });

  it('posts a deposit with the amount and password', async () => {
    const deposit = {
      accountId: 1001,
      accountReference: 'ETP-1001',
      previousBalance: 12500,
      depositedAmount: 500,
      newCashBalance: 13000,
      newBuyingPower: 13000,
      message: 'Funds deposited successfully.'
    };
    const pending = service.depositFunds(500, 'encoded-secret');
    const req = http.expectOne(`${walletUrl()}/deposit`);

    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 500, password: 'encoded-secret' });
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    req.flush(deposit);

    await expect(pending).resolves.toEqual(deposit);
  });

  it('propagates deposit failures', async () => {
    const pending = service.depositFunds(500, 'encoded-secret');
    const req = http.expectOne(`${walletUrl()}/deposit`);
    req.flush('nope', { status: 400, statusText: 'Bad Request' });

    await expect(pending).rejects.toBeTruthy();
  });
});
