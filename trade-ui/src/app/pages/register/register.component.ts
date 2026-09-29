import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
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

  readonly isSubmitting = signal(false);
  readonly submitError = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(64), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmPassword: ['', [Validators.required]],
    accountId: ['', [Validators.required, Validators.min(1)]]
  }, {
    validators: (group) => this.passwordMatchValidator(group)
  });

  private passwordMatchValidator(group: any): { [key: string]: any } | null {
    const password = group.get('password')?.value;
    const confirmPassword = group.get('confirmPassword')?.value;
    return password && confirmPassword && password !== confirmPassword
      ? { 'passwordMismatch': true }
      : null;
  }

  async onSubmit(): Promise<void> {
    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);

    try {
      const { username, password, accountId } = this.form.getRawValue();
      await this.authSession.register(username, password, Number.parseInt(accountId, 10));
      await this.router.navigate(['/dashboard']);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to register right now. Please try again.';
      this.submitError.set(message);
    } finally {
      this.isSubmitting.set(false);
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
