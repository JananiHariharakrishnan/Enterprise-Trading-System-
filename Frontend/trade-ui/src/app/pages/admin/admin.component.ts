import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminService, AdminDashboardStats, AdminUserSummary } from '../../services/admin.service';

export type AdminTab = 'dashboard' | 'users' | 'create';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminComponent implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly router = inject(Router, { optional: true });
  private readonly route = inject(ActivatedRoute, { optional: true });

  readonly activeTab = signal<AdminTab>('dashboard');
  readonly stats = signal<AdminDashboardStats | null>(null);
  readonly users = signal<AdminUserSummary[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly isActionInProgress = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  /**
   * True only until the first load finishes. Later refreshes keep the current
   * content on screen (dimmed) instead of swapping it for a spinner.
   */
  readonly isInitialLoading = computed(() => this.isLoading() && this.stats() === null);

  // Batch Account Creation state
  readonly batchLimit = 200;
  accountBatchCount = 10;
  accountBatchBalance = 100000;
  isCreatingBatch = signal<boolean>(false);
  recentlyCreatedBatch = signal<any[]>([]);

  // Preserved for backwards compatibility with tests
  newAccountReference = '';
  newAccountInitialBalance = 100000;
  isCreatingAccount = signal<boolean>(false);

  // User directory filter & search
  searchTerm = '';
  statusFilter = 'ALL';

  readonly statusOptions = [
    { value: 'ALL', label: 'All' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'SUSPENDED', label: 'Suspended' },
    { value: 'CLOSED', label: 'Closed' }
  ];

  /** Users per status, shown on the filter buttons. */
  readonly statusCounts = computed<Record<string, number>>(() => {
    const list = this.users();
    const count = (status: string) => list.filter(u => u.accountStatus === status).length;
    return {
      ALL: list.length,
      ACTIVE: count('ACTIVE'),
      SUSPENDED: count('SUSPENDED'),
      CLOSED: count('CLOSED')
    };
  });

  // Pie chart calculations
  readonly circumference = 439.82; // 2 * PI * 70

  readonly openCount = computed(() => this.stats()?.openAccounts ?? 0);
  readonly suspendedCount = computed(() => this.stats()?.suspendedAccounts ?? 0);
  readonly closedCount = computed(() => this.stats()?.closedAccounts ?? 0);
  readonly totalAccountsCount = computed(() => {
    const s = this.stats();
    if (s && s.totalAccounts > 0) return s.totalAccounts;
    return this.openCount() + this.suspendedCount() + this.closedCount();
  });

  readonly openPct = computed(() => {
    const total = this.totalAccountsCount();
    return total > 0 ? (this.openCount() / total) * 100 : 0;
  });

  readonly suspendedPct = computed(() => {
    const total = this.totalAccountsCount();
    return total > 0 ? (this.suspendedCount() / total) * 100 : 0;
  });

  readonly closedPct = computed(() => {
    const total = this.totalAccountsCount();
    return total > 0 ? (this.closedCount() / total) * 100 : 0;
  });

  readonly openStroke = computed(() => (this.openPct() / 100) * this.circumference);
  readonly suspendedStroke = computed(() => (this.suspendedPct() / 100) * this.circumference);
  readonly closedStroke = computed(() => (this.closedPct() / 100) * this.circumference);

  readonly openOffset = 0;
  readonly suspendedOffset = computed(() => -this.openStroke());
  readonly closedOffset = computed(() => -(this.openStroke() + this.suspendedStroke()));

  ngOnInit(): void {
    if (this.route) {
      this.route.queryParamMap.subscribe(params => {
        const tab = params.get('tab');
        if (tab === 'users' || tab === 'create') {
          this.activeTab.set(tab);
        } else if (tab === 'dashboard') {
          this.activeTab.set('dashboard');
        }
      });
    }

    this.loadData();
  }

  switchTab(tab: AdminTab): void {
    this.activeTab.set(tab);
    if (this.router && this.route) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { tab },
        queryParamsHandling: 'merge'
      });
    }
  }

  async loadData(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const [statsData, usersData] = await Promise.all([
        this.adminService.getDashboardStats(),
        this.adminService.getAllUsers()
      ]);
      this.stats.set(statsData);
      this.users.set(usersData);
    } catch (err: any) {
      this.errorMessage.set(err?.error?.message || err?.message || 'Failed to load admin dashboard data');
    } finally {
      this.isLoading.set(false);
    }
  }

  get filteredUsers(): AdminUserSummary[] {
    const term = this.searchTerm.trim().toLowerCase();
    const filter = this.statusFilter;

    return this.users().filter(u => {
      const matchesSearch = !term ||
        u.username.toLowerCase().includes(term) ||
        u.accountReference?.toLowerCase().includes(term) ||
        (u.email && u.email.toLowerCase().includes(term)) ||
        (u.firstName && u.firstName.toLowerCase().includes(term)) ||
        (u.lastName && u.lastName.toLowerCase().includes(term));

      const matchesStatus = filter === 'ALL' || u.accountStatus === filter;
      return matchesSearch && matchesStatus;
    });
  }

  setStatusFilter(value: string): void {
    this.statusFilter = value;
  }

  trackByUserId(_: number, user: AdminUserSummary): string | number {
    return user.userId;
  }

  /** Stable hue (0-359) per username, used to tint the avatars. */
  userHue(name: string): number {
    let hash = 0;
    for (const char of name ?? '') {
      hash = (hash * 31 + char.charCodeAt(0)) % 360;
    }
    return hash;
  }

  async setAccountStatus(user: AdminUserSummary, newStatus: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'): Promise<void> {
    if (this.isActionInProgress()) return;

    this.isActionInProgress.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    try {
      await this.adminService.updateUserStatus(user.userId, newStatus);
      this.successMessage.set(`Successfully updated status of ${user.username} to ${newStatus}.`);
      await this.loadData();
    } catch (err: any) {
      this.errorMessage.set(err?.error?.message || err?.message || `Failed to update status to ${newStatus}`);
    } finally {
      this.isActionInProgress.set(false);
    }
  }

  /** Total liquidity the current batch would add. */
  get batchTotal(): number {
    return (Number(this.accountBatchCount) || 0) * (Number(this.accountBatchBalance) || 0);
  }

  /** The form is valid when the count is 1..limit and the balance isn't negative. */
  get isBatchValid(): boolean {
    const count = Number(this.accountBatchCount);
    const balance = Number(this.accountBatchBalance);
    return count >= 1 && count <= this.batchLimit && balance >= 0;
  }

  async onCreateBatchAccounts(): Promise<void> {
    if (this.isCreatingBatch()) return;

    const count = Number(this.accountBatchCount) || 1;
    const balance = Number(this.accountBatchBalance) || 100000;

    if (count < 1) {
      this.errorMessage.set('Number of accounts must be at least 1.');
      return;
    }

    if (count > this.batchLimit) {
      this.errorMessage.set(`You can create at most ${this.batchLimit} accounts at a time.`);
      return;
    }

    this.isCreatingBatch.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    try {
      const created = await this.adminService.createBatchAccounts(count, balance);
      this.recentlyCreatedBatch.set(created || []);
      this.successMessage.set(`Successfully created ${count} active accounts with $${balance.toLocaleString()} initial balance!`);
      await this.loadData();
    } catch (err: any) {
      this.errorMessage.set(err?.error?.message || err?.message || 'Failed to generate accounts.');
    } finally {
      this.isCreatingBatch.set(false);
    }
  }

  // Preserved for compatibility
  async onCreateUnmappedAccount(): Promise<void> {
    if (this.isCreatingAccount()) return;

    this.isCreatingAccount.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    try {
      const ref = this.newAccountReference.trim() || undefined;
      const balance = Number(this.newAccountInitialBalance) || 100000;
      await this.adminService.createUnmappedAccount({
        accountReference: ref,
        initialBalance: balance
      });
      this.successMessage.set(`Unmapped account generated successfully.`);
      this.newAccountReference = '';
      await this.loadData();
    } catch (err: any) {
      this.errorMessage.set(err?.error?.message || err?.message || 'Failed to create unmapped account.');
    } finally {
      this.isCreatingAccount.set(false);
    }
  }

  setBatchCountPreset(count: number): void {
    this.accountBatchCount = count;
  }

  setBatchBalancePreset(balance: number): void {
    this.accountBatchBalance = balance;
  }
}
