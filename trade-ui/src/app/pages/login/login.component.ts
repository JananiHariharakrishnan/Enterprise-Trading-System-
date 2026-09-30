import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthSessionService } from '../../services/auth-session.service';

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

  readonly isSubmitting = signal(false);
  readonly submitError = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  async onSubmit(): Promise<void> {
    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);

    try {
      const { username, password } = this.form.getRawValue();
      await this.authSession.login(username, password);

      // Honour a returnUrl only if it is a same-origin path (starts with '/').
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || undefined;
      const safeReturn = returnUrl && returnUrl.startsWith('/') ? returnUrl : undefined;

      if (safeReturn) {
        await this.router.navigateByUrl(safeReturn);
      } else {
        await this.router.navigate(['/dashboard']);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to sign in right now. Please try again.';
      this.submitError.set(message);
    } finally {
      this.isSubmitting.set(false);
    }
  }
}
