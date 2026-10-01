import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LoginRequest, ResponseError, UserResponse } from '../generated/auth';
import { mapErrorCodeToMessage } from '../errors/error-messages';
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

  async register(username: string, password: string): Promise<UserResponse> {
    const payload = { username, password };

    try {
      return await this.authApi.client.authRegisterPost({ registerRequest: payload });
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

  clearCurrentUserCache(): void {
    this.currentUserSignal.set(null);
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
    // Network errors / CORS / service down typically surface as TypeError or status 0
    if (error instanceof TypeError) {
      return `Cannot reach Auth service on http://${this.authHost}:3000. Start auth-service and try again.`;
    }

    if (error instanceof ResponseError) {
      // map known codes to trader-friendly messages first
      try {
        const clone = error.response.clone();
        const body = await clone.json().catch(() => ({}));
        const serverMessage = body && typeof body.message === 'string' ? body.message : null;
        const code = body && typeof body.errorCode === 'string' ? body.errorCode : undefined;

        // Handle HTTP 0 (unreachable) explicitly
        if (error.response.status === 0) {
          return `Cannot reach Auth service on http://${this.authHost}:3000. Start auth-service and try again.`;
        }

        // Specific 401 semantics for login
        if (error.response.status === 401 && !code) {
          return 'Invalid username or password.';
        }

        if (code) {
          return mapErrorCodeToMessage(code, serverMessage);
        }

        if (serverMessage && serverMessage.trim().length > 0) {
          return serverMessage;
        }
      } catch {
        // Fallthrough to generic
      }
    }

    return 'Unable to sign in right now. Please try again.';
  }
}
