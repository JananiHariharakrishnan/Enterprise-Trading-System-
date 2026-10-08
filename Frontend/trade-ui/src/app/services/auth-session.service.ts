import { Injectable, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { LoginRequest, ResponseError, UserResponse } from '../generated/auth';
import { mapErrorCodeToMessage } from '../errors/error-messages';
import { AuthApiClientService } from './auth-api-client.service';
import { CustomerPreferencesService } from './customer-preferences.service';
import { ThemePreference, ThemeService } from './theme.service';
import { TokenStorageService, SESSION_DURATION_SECONDS } from './token-storage.service';
import { getSafeReturnUrl } from '../guards/return-url.util';
import { PasswordHashService } from './password-hash.service';

@Injectable({
  providedIn: 'root'
})
export class AuthSessionService {
  private readonly authApi = inject(AuthApiClientService);
  private readonly preferencesApi = inject(CustomerPreferencesService, { optional: true });
  private readonly themeService = inject(ThemeService);
  private readonly tokenStorage = inject(TokenStorageService);
  private readonly router = inject(Router);
  private readonly passwordHashService = inject(PasswordHashService);

  private readonly currentUserSignal = signal<UserResponse | null>(null);
  private readonly isAuthenticatedSignal = signal(this.tokenStorage.getAccessToken() !== null);
  private loadCurrentUserPromise: Promise<UserResponse | null> | null = null;
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = this.isAuthenticatedSignal.asReadonly();
  readonly isAdmin = computed(() => Boolean(this.currentUserSignal()?.roles?.includes('ADMIN')));
  private readonly authHost = typeof window === 'undefined' ? 'localhost' : window.location.hostname;

  // Session countdown timer state
  readonly sessionRemainingSeconds = signal<number>(0);
  readonly sessionExpiresIn = computed(() => {
    const total = this.sessionRemainingSeconds();
    const minutes = Math.floor(total / 60);
    const seconds = total % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  });
  readonly isSessionExpiringSoon = computed(() => {
    const rem = this.sessionRemainingSeconds();
    return rem > 0 && rem <= 60;
  });

  private sessionTimerId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    if (this.isAuthenticatedSignal()) {
      this.startSessionTimer();
    }
  }

  async login(username: string, password: string): Promise<void> {
    try {
      // Encode password on client-side to avoid sending plaintext in network requests
      const encodedPassword = this.passwordHashService.encodePassword(password);
      const payload: LoginRequest = { username, password: encodedPassword };

      // Clear any in-memory user state before switching sessions.
      this.currentUserSignal.set(null);
      const response = await this.authApi.client.authLoginPost({ loginRequest: payload });
      this.tokenStorage.setTokens(response.accessToken, response.refreshToken);
      this.isAuthenticatedSignal.set(true);
      this.startSessionTimer();
      // Best effort profile load; successful token issuance should still allow navigation.
      await this.loadCurrentUser(false);
    } catch (error) {
      throw new Error(await this.toReadableError(error));
    }
  }

  async register(username: string, password: string): Promise<UserResponse> {
    try {
      // Encode password on client-side to avoid sending plaintext in network requests
      const encodedPassword = this.passwordHashService.encodePassword(password);
      // The generated `RegisterRequest` model currently requires `accountId`,
      // but the Auth service will allocate an account id when omitted. Cast
      // to `any` to match the generated client signature without forcing a
      // synthetic accountId value here.
      return await this.authApi.client.authRegisterPost({ registerRequest: { username, password: encodedPassword } as any });
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
        let themePreference: ThemePreference = 'SYSTEM';

        // Apply the persisted default account when available so the session
        // opens on the account selected in customer preferences.
        if (this.preferencesApi) {
          try {
            const preferences = await this.preferencesApi.getPreferences(user.accountId);
            if (preferences?.defaultAccountId && Number.isFinite(preferences.defaultAccountId)) {
              user.accountId = Number(preferences.defaultAccountId);
            }

            if (preferences?.display?.theme) {
              themePreference = preferences.display.theme;
            }
          } catch {
            // Best effort: auth should still succeed if preferences are unavailable.
          }
        }

        // Always apply a theme preference for the active user. If preferences
        // are missing or unavailable, default to SYSTEM to avoid stale carry-over.
        this.themeService.applyThemePreference(themePreference);

        this.currentUserSignal.set(user);
        return user;
      } catch (error) {
        this.currentUserSignal.set(null);
        if (clearTokenOnFailure && this.shouldClearSessionOnProfileFailure(error)) {
          this.stopSessionTimer();
          this.sessionRemainingSeconds.set(0);
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
    this.stopSessionTimer();
    this.sessionRemainingSeconds.set(0);
    this.tokenStorage.clear();
    this.currentUserSignal.set(null);
    this.isAuthenticatedSignal.set(false);
    this.themeService.applyThemePreference('SYSTEM');
    await this.router.navigate(['/login']);
  }

  startSessionTimer(): void {
    this.stopSessionTimer();

    let expiresAt = this.tokenStorage.getExpiresAt();
    if (!expiresAt) {
      expiresAt = Date.now() + SESSION_DURATION_SECONDS * 1000;
      this.tokenStorage.setExpiresAt(expiresAt);
    }

    const update = () => {
      const remaining = Math.max(0, Math.ceil((expiresAt! - Date.now()) / 1000));
      this.sessionRemainingSeconds.set(remaining);
      if (remaining <= 0) {
        this.stopSessionTimer();
        void this.handleSessionExpiry();
      }
    };

    update();
    if (this.sessionRemainingSeconds() > 0) {
      this.sessionTimerId = setInterval(update, 1000);
    }
  }

  stopSessionTimer(): void {
    if (this.sessionTimerId !== null) {
      clearInterval(this.sessionTimerId);
      this.sessionTimerId = null;
    }
  }

  async handleSessionExpiry(): Promise<void> {
    this.stopSessionTimer();
    this.sessionRemainingSeconds.set(0);
    this.tokenStorage.clear();
    this.currentUserSignal.set(null);
    this.isAuthenticatedSignal.set(false);

    const currentUrl = this.router.url;
    const safeReturn = getSafeReturnUrl(currentUrl);
    const returnUrl = safeReturn && safeReturn !== '/login' && safeReturn !== '/register'
      ? safeReturn
      : undefined;

    await this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : undefined);
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
        if (error.response.status === 401) {
          // Check if the server message indicates user not found
          if (serverMessage && /user.*not.*found|account.*not.*found|no.{0,5}user|does.*not.*exist/i.test(serverMessage)) {
            return serverMessage; // Pass through the "user not found" message
          }
          if (!code) {
            return 'Invalid username or password.';
          }
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
