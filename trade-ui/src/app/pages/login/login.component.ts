import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthSessionService } from '../../services/auth-session.service';
import { getSafeReturnUrl } from '../../guards/return-url.util';

/** Wrong attempts allowed before the first lock. */
const FREE_ATTEMPTS = 3;
/** First lock, after FREE_ATTEMPTS wrong attempts. */
const FIRST_LOCK_SECONDS = 10;
/** Every lock after the first one (one more wrong attempt triggers it). */
const REPEAT_LOCK_SECONDS = 30;

const STORAGE_KEY = 'login.lockout.v1';
const WRONG_CREDENTIALS_MESSAGE = 'Wrong username or password!';

interface LockoutState {
  /** Wrong attempts since the last lock started. */
  failedAttempts: number;
  /** How many locks have happened so far (0 = none yet). */
  lockCount: number;
  /** Epoch ms when the current lock ends, or null when not locked. */
  lockedUntil: number | null;
  /** Length in seconds of the current/last lock (for the progress bar). */
  lockDuration: number;
}

const EMPTY_STATE: LockoutState = {
  failedAttempts: 0,
  lockCount: 0,
  lockedUntil: null,
  lockDuration: 0
};

@Component({
  selector: 'app-login',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly isSubmitting = signal(false);
  readonly submitError = signal<string | null>(null);

  /** Seconds left on the current lock (0 = not locked). */
  readonly remainingSeconds = signal(0);
  readonly lockDuration = signal(0);
  readonly isLocked = computed(() => this.remainingSeconds() > 0);
  readonly progressPercent = computed(() => {
    const total = this.lockDuration();
    return total > 0 ? Math.min(100, (this.remainingSeconds() / total) * 100) : 0;
  });

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  private state: LockoutState = this.loadState();
  private timerId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopTimer());

    // If the page is refreshed during a lock, pick the countdown back up.
    const { lockedUntil, lockDuration } = this.state;
    if (lockedUntil && lockedUntil > Date.now()) {
      this.startLock(lockedUntil, lockDuration);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.isLocked() || this.isSubmitting()) {
      return;
    }

    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);

    try {
      const { username, password } = this.form.getRawValue();
      await this.authSession.login(username, password);

      this.resetLockoutState();

      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || undefined;
      const safeReturn = getSafeReturnUrl(returnUrl);

      if (safeReturn) {
        await this.router.navigateByUrl(safeReturn);
      } else {
        await this.router.navigate(['/dashboard']);
      }
    } catch (error) {
      if (this.isCredentialFailure(error)) {
        this.registerFailedAttempt();
      } else {
        // Network/server problems are not the user's fault, so they don't count as attempts.
        const message =
          error instanceof Error ? error.message : 'Unable to sign in right now. Please try again.';
        this.submitError.set(message);
      }
    } finally {
      this.isSubmitting.set(false);
    }
  }

  /**
   * Decides whether a login error means "wrong username or password".
   * Adjust this if your AuthSessionService signals bad credentials differently
   * (for example with an error code).
   */
  private isCredentialFailure(error: unknown): boolean {
    if (error instanceof HttpErrorResponse) {
      return error.status === 401 || error.status === 403;
    }
    if (error instanceof Error) {
      return /authentication required|invalid (credentials|username|password)|unauthori[sz]ed/i.test(
        error.message
      );
    }
    return false;
  }

  private registerFailedAttempt(): void {
    const isFirstRound = this.state.lockCount === 0;
    const threshold = isFirstRound ? FREE_ATTEMPTS : 1;
    const failedAttempts = this.state.failedAttempts + 1;

    if (failedAttempts >= threshold) {
      const duration = isFirstRound ? FIRST_LOCK_SECONDS : REPEAT_LOCK_SECONDS;
      const lockedUntil = Date.now() + duration * 1000;

      this.state = {
        failedAttempts: 0,
        lockCount: this.state.lockCount + 1,
        lockedUntil,
        lockDuration: duration
      };
      this.saveState();

      this.submitError.set(WRONG_CREDENTIALS_MESSAGE);
      this.startLock(lockedUntil, duration);
      return;
    }

    this.state = { ...this.state, failedAttempts };
    this.saveState();

    this.submitError.set(WRONG_CREDENTIALS_MESSAGE);
  }

  private startLock(lockedUntil: number, durationSeconds: number): void {
    this.stopTimer();
    this.lockDuration.set(durationSeconds);
    this.form.disable();

    this.updateRemaining(lockedUntil);
    if (this.remainingSeconds() > 0) {
      // Derive the time from the clock each tick so it never drifts.
      this.timerId = setInterval(() => this.updateRemaining(lockedUntil), 250);
    }
  }

  private updateRemaining(lockedUntil: number): void {
    const remaining = Math.max(0, Math.ceil((lockedUntil - Date.now()) / 1000));
    this.remainingSeconds.set(remaining);

    if (remaining === 0) {
      this.endLock();
    }
  }

  private endLock(): void {
    this.stopTimer();
    this.state = { ...this.state, lockedUntil: null };
    this.saveState();

    this.lockDuration.set(0);
    this.submitError.set(null);
    this.form.enable();
    this.form.controls.password.reset('');
  }

  private stopTimer(): void {
    if (this.timerId !== null) {
      clearInterval(this.timerId);
      this.timerId = null;
    }
  }

  private resetLockoutState(): void {
    this.state = { ...EMPTY_STATE };
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable, nothing to clear */
    }
  }

  private loadState(): LockoutState {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return { ...EMPTY_STATE, ...(JSON.parse(raw) as Partial<LockoutState>) };
      }
    } catch {
      /* storage unavailable or corrupted, start fresh */
    }
    return { ...EMPTY_STATE };
  }

  private saveState(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state));
    } catch {
      /* storage unavailable, lockout still works for this page view */
    }
  }
}
