import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthSessionService } from '../../services/auth-session.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** How long the "account created" popup stays before moving to sign in. */
  readonly redirectMs = 4000;

  readonly isSubmitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly assignedAccountId = signal<number | null>(null);

  private redirectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(64), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', [Validators.required]],
  }, {
    validators: (group) => this.passwordMatchValidator(group)
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.clearRedirectTimer());
  }

  private passwordMatchValidator(group: any): { [key: string]: any } | null {
    const password = group.get('password')?.value;
    const confirmPassword = group.get('confirmPassword')?.value;
    return password && confirmPassword && password !== confirmPassword
      ? { 'passwordMismatch': true }
      : null;
  }

  /** True when the confirm field should glow red because the two passwords differ. */
  get showMismatch(): boolean {
    return this.form.hasError('passwordMismatch') && !!this.form.get('confirmPassword')?.touched;
  }

  async onSubmit(): Promise<void> {
    if (this.assignedAccountId() !== null) {
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
      const registeredUser = await this.authSession.register(username, password);

      this.form.patchValue({ password: '', confirmPassword: '' });
      this.form.markAsPristine();
      this.form.disable();
      this.assignedAccountId.set(registeredUser.accountId);

      // Show the popup for a few seconds, then move to the sign-in page.
      this.redirectTimer = setTimeout(() => void this.goToSignIn(), this.redirectMs);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to register right now. Please try again.';
      this.submitError.set(message);
    } finally {
      this.isSubmitting.set(false);
    }
  }

  /** Called by the timer, or immediately by the "Sign in now" button. */
  async goToSignIn(): Promise<void> {
    this.clearRedirectTimer();
    await this.router.navigate(['/login'], { queryParamsHandling: 'preserve' });
  }

  private clearRedirectTimer(): void {
    if (this.redirectTimer !== null) {
      clearTimeout(this.redirectTimer);
      this.redirectTimer = null;
    }
  }

  getPasswordErrorMessage(): string {
    const control = this.form.get('password');
    if (!control?.errors) return '';

    if (control.hasError('required')) return 'Password is required.';
    if (control.hasError('minlength')) return 'Password must be at least 12 characters.';
    if (control.hasError('maxlength')) return 'Password must not exceed 128 characters.';
    return 'Invalid password.';
  }

  getUsernameErrorMessage(): string {
    const control = this.form.get('username');
    if (!control?.errors) return '';

    if (control.hasError('required')) return 'Username is required.';
    if (control.hasError('minlength')) return 'Username must be at least 3 characters.';
    if (control.hasError('maxlength')) return 'Username must not exceed 64 characters.';
    if (control.hasError('pattern')) return 'Username can only contain letters, numbers, dots, dashes, and underscores.';
    return 'Invalid username.';
  }
}
