import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminComponent } from './admin.component';
import { AdminService, AdminDashboardStats, AdminUserSummary } from '../../services/admin.service';

describe('AdminComponent', () => {
  let component: AdminComponent;
  let fixture: ComponentFixture<AdminComponent>;
  let adminServiceSpy: {
    getDashboardStats: ReturnType<typeof vi.fn>;
    getAllUsers: ReturnType<typeof vi.fn>;
    updateUserStatus: ReturnType<typeof vi.fn>;
    updateAccountStatus: ReturnType<typeof vi.fn>;
    createUnmappedAccount: ReturnType<typeof vi.fn>;
    createBatchAccounts: ReturnType<typeof vi.fn>;
  };

  const mockStats: AdminDashboardStats = {
    totalMoney: 1500000,
    totalAccounts: 10,
    openAccounts: 8,
    suspendedAccounts: 1,
    closedAccounts: 1,
    totalUsers: 7,
    unmappedAccounts: 3
  };

  const mockUsers: AdminUserSummary[] = [
    {
      userId: 'u-1',
      accountId: 1001,
      username: 'alice',
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice@example.com',
      roles: ['CUSTOMER'],
      accountReference: 'ETP-1001',
      accountStatus: 'ACTIVE',
      cashBalance: 5000,
      buyingPower: 5000
    },
    {
      userId: 'u-2',
      accountId: 1002,
      username: 'bob',
      firstName: 'Bob',
      lastName: 'Jones',
      roles: ['CUSTOMER'],
      accountReference: 'ETP-1002',
      accountStatus: 'SUSPENDED',
      cashBalance: 2000,
      buyingPower: 2000
    }
  ];

  beforeEach(async () => {
    adminServiceSpy = {
      getDashboardStats: vi.fn().mockResolvedValue(mockStats),
      getAllUsers: vi.fn().mockResolvedValue(mockUsers),
      updateUserStatus: vi.fn().mockResolvedValue(undefined),
      updateAccountStatus: vi.fn().mockResolvedValue(undefined),
      createUnmappedAccount: vi.fn().mockResolvedValue({ id: 999 }),
      createBatchAccounts: vi.fn().mockResolvedValue([
        { id: 101, accountReference: 'ETP-POOL-1', cashBalance: 100000 },
        { id: 102, accountReference: 'ETP-POOL-2', cashBalance: 100000 }
      ])
    };

    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [
        { provide: AdminService, useValue: adminServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('loads and displays stats and users on init', () => {
    expect(adminServiceSpy.getDashboardStats).toHaveBeenCalled();
    expect(adminServiceSpy.getAllUsers).toHaveBeenCalled();
    expect(component.stats()).toEqual(mockStats);
    expect(component.users().length).toBe(2);
  });

  it('calculates pie chart percentages properly', () => {
    expect(component.openCount()).toBe(8);
    expect(component.suspendedCount()).toBe(1);
    expect(component.closedCount()).toBe(1);
    expect(component.totalAccountsCount()).toBe(10);
    expect(component.openPct()).toBe(80);
    expect(component.suspendedPct()).toBe(10);
    expect(component.closedPct()).toBe(10);
  });

  it('switches tabs between dashboard, users, and create', () => {
    expect(component.activeTab()).toBe('dashboard');

    component.switchTab('users');
    expect(component.activeTab()).toBe('users');

    component.switchTab('create');
    expect(component.activeTab()).toBe('create');
  });

  it('filters users by search term', () => {
    component.searchTerm = 'bob';
    expect(component.filteredUsers.length).toBe(1);
    expect(component.filteredUsers[0].username).toBe('bob');
  });

  it('filters users by status', () => {
    component.statusFilter = 'SUSPENDED';
    expect(component.filteredUsers.length).toBe(1);
    expect(component.filteredUsers[0].username).toBe('bob');
  });

  it('calls updateUserStatus when changing status', async () => {
    await component.setAccountStatus(mockUsers[0], 'SUSPENDED');
    expect(adminServiceSpy.updateUserStatus).toHaveBeenCalledWith('u-1', 'SUSPENDED');
    expect(component.successMessage()).toContain('SUSPENDED');
  });

  it('creates batch accounts with active status and specified balance', async () => {
    component.accountBatchCount = 10;
    component.accountBatchBalance = 50000;
    await component.onCreateBatchAccounts();

    expect(adminServiceSpy.createBatchAccounts).toHaveBeenCalledWith(10, 50000);
    expect(component.successMessage()).toContain('Successfully created 10 active accounts');
    expect(component.recentlyCreatedBatch().length).toBe(2);
  });

  it('renders the dashboard, user directory, and account provisioning tabs', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dashboard-tab')).toBeTruthy();

    component.stats.set({ ...mockStats, unmappedAccounts: 1 });
    component.successMessage.set('Saved');
    component.errorMessage.set('Failed');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="admin-success"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="admin-error"]')).toBeTruthy();

    component.users.set([
      ...mockUsers,
      {
        ...mockUsers[0],
        userId: 'u-3',
        username: 'cara',
        accountStatus: 'CLOSED',
        roles: ['ADMIN'],
        email: undefined,
        firstName: undefined,
        lastName: undefined
      }
    ]);
    component.switchTab('users');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.users-tab')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('cara');

    component.switchTab('create');
    component.accountBatchCount = 500;
    component.isCreatingBatch.set(true);
    component.recentlyCreatedBatch.set([{ accountReference: 'ETP-9', cashBalance: 1000 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.create-tab')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('ETP-9');
  });

  it('calls createUnmappedAccount when generating single account', async () => {
    component.newAccountReference = 'ETP-NEW-1';
    component.newAccountInitialBalance = 50000;
    await component.onCreateUnmappedAccount();

    expect(adminServiceSpy.createUnmappedAccount).toHaveBeenCalledWith({
      accountReference: 'ETP-NEW-1',
      initialBalance: 50000
    });
    expect(component.successMessage()).toContain('generated successfully');
  });
});
