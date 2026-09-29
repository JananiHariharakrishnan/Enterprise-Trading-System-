import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LoginRequest, ResponseError, UserResponse } from '../generated/auth';
import { AuthApiClientService } from './auth-api-client.service';
import { TokenStorageService } from './token-storage.service';

@Injectable({
  providedIn: 'root'
})
export class AuthSessionService {
  private readonly authApi = inject(AuthApiClientService);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly router = inject(Router);

  private readonly currentUserSignal = signal<UserResponse | null>(null);
  private readonly isAuthenticatedSignal = signal(this.tokenStorage.getAccessToken() !== null);
  private loadCurrentUserPromise: Promise<UserResponse | null> | null = null;
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = this.isAuthenticatedSignal.asReadonly();
  private readonly authHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;

  async login(username: string, password: string): Promise<void> {
    const payload: LoginRequest = { username, password };

    try {
      const response = await this.authApi.client.authLoginPost({ loginRequest: payload });
      this.tokenStorage.setTokens(response.accessToken, response.refreshToken);
      this.isAuthenticatedSignal.set(true);
      // Best effort profile load; successful token issuance should still allow navigation.
      await this.loadCurrentUser(false);
    } catch (error) {
      throw new Error(await this.toReadableError(error));
    }
  }

  async loadCurrentUser(clearTokenOnFailure = true): Promise<UserResponse | null> {
    if (!this.isAuthenticated()) {
      this.currentUserSignal.set(null);
      return null;
    }

    const cachedUser = this.currentUserSignal();
    if (cachedUser) {
      return cachedUser;
    }

    if (this.loadCurrentUserPromise) {
      return this.loadCurrentUserPromise;
    }

    this.loadCurrentUserPromise = (async () => {
      try {
        const user = await this.authApi.client.authMeGet();
        this.currentUserSignal.set(user);
        return user;
      } catch (error) {
        this.currentUserSignal.set(null);
        if (clearTokenOnFailure && this.shouldClearSessionOnProfileFailure(error)) {
          this.tokenStorage.clear();
          this.isAuthenticatedSignal.set(false);
        }
        return null;
      } finally {
        this.loadCurrentUserPromise = null;
      }
    })();

    return this.loadCurrentUserPromise;
  }

  async logout(): Promise<void> {
    this.tokenStorage.clear();
    this.currentUserSignal.set(null);
    this.isAuthenticatedSignal.set(false);
    await this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return this.tokenStorage.getAccessToken();
  }

  private shouldClearSessionOnProfileFailure(error: unknown): boolean {
    if (error instanceof ResponseError) {
      // Keep tokens for retryable failures such as rate limiting or temporary server errors.
      return error.response.status === 401 || error.response.status === 403;
    }

    return false;
  }

  private async toReadableError(error: unknown): Promise<string> {
    if (error instanceof TypeError) {
      return `Cannot reach Auth service on http://${this.authHost}:3000. Start auth-service and try again.`;
    }

    if (error instanceof ResponseError) {
      if (error.response.status === 401) {
        return 'Invalid username or password.';
      }

      try {
        const body = (await error.response.clone().json()) as { message?: string };
        if (body.message && body.message.trim().length > 0) {
          return body.message;
        }
      } catch {
        // Fallback to generic error below when response body is missing.
      }
    }

    return 'Unable to sign in right now. Please try again.';
  }
}
