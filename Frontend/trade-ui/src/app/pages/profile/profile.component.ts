import { CommonModule } from '@angular/common';
import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserResponse } from '../../generated/auth';
import { AuthSessionService } from '../../services/auth-session.service';
import { AuthApiClientService } from '../../services/auth-api-client.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  readonly authSession = inject(AuthSessionService);
  private readonly authApi = inject(AuthApiClientService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly currentUser = signal<UserResponse | null>(null);

  profileForm: FormGroup;

  constructor() {
    this.profileForm = this.formBuilder.group({
      username: [{ value: '', disabled: true }, Validators.required],
      firstName: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      phoneNo: ['', [Validators.minLength(5), Validators.maxLength(20)]],
      accountId: [{ value: '', disabled: true }, Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadProfile();
  }

  private async loadProfile(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    try {
      const user = await this.authSession.loadCurrentUser();
      if (!user) {
        this.errorMessage.set('Unable to load profile. Please sign in again.');
        return;
      }

      this.currentUser.set(user);
      this.profileForm.patchValue({
        username: user.username,
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        email: user.email || '',
        phoneNo: user.phoneNo || '',
        accountId: user.accountId
      });
    } catch {
      // Error loading profile - display user-friendly message without interrupting component lifecycle
      this.errorMessage.set('Failed to load profile information.');
    } finally {
      this.isLoading.set(false);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.profileForm.invalid) {
      this.errorMessage.set('Please fix the errors in the form.');
      return;
    }

    this.isSaving.set(true);
    this.successMessage.set(null);
    this.errorMessage.set(null);

    try {
      const formValue = this.profileForm.getRawValue();
      // Call backend endpoint to update profile via PATCH /auth/profile
      await this.authApi.client.authProfilePatch({
        updateProfileRequest: {
          firstName: formValue.firstName,
          lastName: formValue.lastName,
          email: formValue.email,
          phoneNo: formValue.phoneNo
        }
      });

      this.successMessage.set('Profile updated successfully!');
      // Clear auth cache and reload profile to reflect changes from database
      this.authSession.clearCurrentUserCache();
      await this.loadProfile();
    } catch {
      // Error updating profile - display user-friendly message without interrupting component lifecycle
      this.errorMessage.set('Failed to update profile. Please try again.');
    } finally {
      this.isSaving.set(false);
    }
  }

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }
}
