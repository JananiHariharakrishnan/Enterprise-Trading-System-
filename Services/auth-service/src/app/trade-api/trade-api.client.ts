import axios, { AxiosInstance } from 'axios';
import { logger } from '../common/logger.js';

/**
 * TradeApiClient provides an interface for auth-service to call trade-api
 * for user management operations instead of accessing the database directly.
 * This allows proper schema separation between auth and trading systems.
 */
export class TradeApiClient {
  private client: AxiosInstance;
  private baseURL: string;

  constructor(baseURL?: string) {
    this.baseURL = baseURL || process.env.TRADE_API_URL || 'http://localhost:8081/api/v1';
    this.client = axios.create({
      baseURL: this.baseURL,
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Get an available account ID from the trading system for user registration
   */
  async getAvailableAccountId(): Promise<number | null> {
    try {
      const response = await this.client.get('/users/available-account');
      return response.data?.accountId || null;
    } catch (error) {
      logger.error('Failed to get available account ID from trade-api', {
        error: error instanceof Error ? error.message : 'unknown',
        baseURL: this.baseURL,
      });
      throw error;
    }
  }

  /**
   * Create a new user in the trading system
   */
  async createUser(
    username: string,
    accountId: number,
    roles: string[] = ['CUSTOMER'],
  ): Promise<UserRecord | null> {
    try {
      const response = await this.client.post('/users', {
        username,
        accountId,
        roles,
      });

      if (!response.data) {
        return null;
      }

      return mapUserFromResponse(response.data);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        // Conflict - account already taken or other constraint violation
        return null;
      }
      logger.error('Failed to create user in trade-api', {
        username,
        accountId,
        error: error instanceof Error ? error.message : 'unknown',
      });
      throw error;
    }
  }

  /**
   * Get user by ID
   */
  async getUserById(id: string): Promise<UserRecord | null> {
    try {
      const response = await this.client.get(`/users/${id}`);
      return mapUserFromResponse(response.data);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      logger.error('Failed to get user by ID from trade-api', {
        id,
        error: error instanceof Error ? error.message : 'unknown',
      });
      throw error;
    }
  }

  /**
   * Get user by username
   */
  async getUserByUsername(username: string): Promise<UserRecord | null> {
    try {
      const response = await this.client.get(`/users/by-username/${username}`);
      return mapUserFromResponse(response.data);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      logger.error('Failed to get user by username from trade-api', {
        username,
        error: error instanceof Error ? error.message : 'unknown',
      });
      throw error;
    }
  }

  /**
   * Get user by account ID
   */
  async getUserByAccountId(accountId: number): Promise<UserRecord | null> {
    try {
      const response = await this.client.get(`/users/by-account/${accountId}`);
      return mapUserFromResponse(response.data);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      logger.error('Failed to get user by account ID from trade-api', {
        accountId,
        error: error instanceof Error ? error.message : 'unknown',
      });
      throw error;
    }
  }

  /**
   * Update user profile information
   */
  async updateUserProfile(
    id: string,
    updates: {
      firstName?: string;
      lastName?: string;
      email?: string;
      phoneNo?: string;
    },
  ): Promise<UserRecord | null> {
    try {
      const response = await this.client.put(`/users/${id}/profile`, updates);
      return mapUserFromResponse(response.data);
    } catch (error) {
      logger.error('Failed to update user profile in trade-api', {
        id,
        error: error instanceof Error ? error.message : 'unknown',
      });
      throw error;
    }
  }

  /**
   * Delete orphan credentials (credentials without associated users)
   */
  async deleteOrphanCredentials(): Promise<void> {
    try {
      await this.client.delete('/users/orphan-credentials');
    } catch (error) {
      logger.error('Failed to delete orphan credentials from trade-api', {
        error: error instanceof Error ? error.message : 'unknown',
      });
      throw error;
    }
  }
}

export type UserRecord = {
  id: string;
  username: string;
  accountId: number;
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNo?: string;
  roles: string[];
  createdOn: string;
};

function normalizeRoles(roles: unknown): string[] {
  if (!Array.isArray(roles) || roles.length === 0) {
    return ['CUSTOMER'];
  }
  const cleaned = roles
    .map((role) => String(role).trim().replace(/^"+|"+$/g, ''))
    .filter((role) => role.length > 0);
  return cleaned.length > 0 ? cleaned : ['CUSTOMER'];
}

function mapUserFromResponse(data: any): UserRecord {
  return {
    id: data.id,
    username: data.username,
    accountId: data.accountId,
    firstName: data.firstName,
    lastName: data.lastName,
    email: data.email,
    phoneNo: data.phoneNo,
    roles: normalizeRoles(data.roles),
    createdOn: data.createdOn,
  };
}
