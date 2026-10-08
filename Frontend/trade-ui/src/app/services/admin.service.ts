import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from './token-storage.service';

export interface AdminDashboardStats {
  totalMoney: number;
  totalAccounts: number;
  openAccounts: number;
  suspendedAccounts: number;
  closedAccounts: number;
  totalUsers: number;
  unmappedAccounts: number;
}

export interface AdminUserSummary {
  userId: string;
  accountId: number;
  username: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNo?: string;
  roles: string[];
  accountReference: string;
  accountStatus: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  cashBalance: number;
  buyingPower: number;
  createdOn?: string;
}

export interface UnassignedAccountSummary {
  accountId: number;
  accountReference: string;
  cashBalance: number;
  buyingPower: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  unassigned: boolean;
  assignedUsername?: string;
  lastUpdated?: string;
}

export interface CreateAccountPayload {
  accountReference?: string;
  initialBalance?: number;
}

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly host = typeof window === 'undefined' ? 'localhost' : window.location.hostname;
  private readonly baseUrl = `http://${this.host}:8081/api/v1/admin`;

  private getHeaders(): HttpHeaders {
    const token = this.tokenStorage.getAccessToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }

  async getDashboardStats(): Promise<AdminDashboardStats> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.get<AdminDashboardStats>(`${this.baseUrl}/stats`, { headers }));
  }

  async getAllUsers(): Promise<AdminUserSummary[]> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.get<AdminUserSummary[]>(`${this.baseUrl}/users`, { headers }));
  }

  async getUnassignedAndDormantAccounts(): Promise<UnassignedAccountSummary[]> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.get<UnassignedAccountSummary[]>(`${this.baseUrl}/accounts/unassigned`, { headers }));
  }

  async updateUserStatus(userId: string, status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'): Promise<void> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.put<void>(`${this.baseUrl}/users/${userId}/status`, { status }, { headers }));
  }

  async updateAccountStatus(accountId: number, status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'): Promise<void> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.put<void>(`${this.baseUrl}/accounts/${accountId}/status`, { status }, { headers }));
  }

  async createUnmappedAccount(payload: CreateAccountPayload): Promise<any> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.post<any>(`${this.baseUrl}/accounts`, payload, { headers }));
  }

  async createBatchAccounts(count: number, initialBalance: number): Promise<any[]> {
    const headers = this.getHeaders();
    return firstValueFrom(this.http.post<any[]>(`${this.baseUrl}/accounts/batch`, { count, initialBalance }, { headers }));
  }
}
