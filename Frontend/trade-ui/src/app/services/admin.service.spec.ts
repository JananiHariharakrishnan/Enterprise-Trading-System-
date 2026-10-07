import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminService } from './admin.service';
import { TokenStorageService } from './token-storage.service';

describe('AdminService', () => {
  let service: AdminService;
  let http: HttpTestingController;
  let tokenStorage: { getAccessToken: ReturnType<typeof vi.fn> };

  const base = () => `http://${window.location.hostname}:8081/api/v1/admin`;

  beforeEach(() => {
    tokenStorage = { getAccessToken: vi.fn().mockReturnValue('admin-token') };
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AdminService,
        { provide: TokenStorageService, useValue: tokenStorage }
      ]
    });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads dashboard stats with a bearer token', async () => {
    const stats = {
      totalMoney: 1,
      totalAccounts: 2,
      openAccounts: 1,
      suspendedAccounts: 0,
      closedAccounts: 1,
      totalUsers: 1,
      unmappedAccounts: 0
    };
    const pending = service.getDashboardStats();
    const req = http.expectOne(`${base()}/stats`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer admin-token');
    req.flush(stats);
    await expect(pending).resolves.toEqual(stats);
  });

  it('omits the authorization header when no token is stored', async () => {
    tokenStorage.getAccessToken.mockReturnValue(null);
    const pending = service.getAllUsers();
    const req = http.expectOne(`${base()}/users`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
    await pending;
  });

  it('loads unassigned accounts and updates user and account status', async () => {
    const accounts = service.getUnassignedAndDormantAccounts();
    http.expectOne(`${base()}/accounts/unassigned`).flush([]);
    await accounts;

    const userStatus = service.updateUserStatus('u-1', 'SUSPENDED');
    const userReq = http.expectOne(`${base()}/users/u-1/status`);
    expect(userReq.request.method).toBe('PUT');
    expect(userReq.request.body).toEqual({ status: 'SUSPENDED' });
    userReq.flush(null);
    await userStatus;

    const accountStatus = service.updateAccountStatus(9, 'CLOSED');
    const accountReq = http.expectOne(`${base()}/accounts/9/status`);
    expect(accountReq.request.body).toEqual({ status: 'CLOSED' });
    accountReq.flush(null);
    await accountStatus;
  });

  it('creates one account and a batch of accounts', async () => {
    const created = service.createUnmappedAccount({ accountReference: 'ETP-9', initialBalance: 10000 });
    const createReq = http.expectOne(`${base()}/accounts`);
    expect(createReq.request.method).toBe('POST');
    createReq.flush({ accountId: 9 });
    await expect(created).resolves.toEqual({ accountId: 9 });

    const batch = service.createBatchAccounts(3, 10000);
    const batchReq = http.expectOne(`${base()}/accounts/batch`);
    expect(batchReq.request.body).toEqual({ count: 3, initialBalance: 10000 });
    batchReq.flush([{ accountId: 10 }]);
    await expect(batch).resolves.toEqual([{ accountId: 10 }]);
  });
});
