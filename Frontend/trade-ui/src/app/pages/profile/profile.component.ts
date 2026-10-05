import { CommonModule } from '@angular/common';
import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserResponse } from '../../generated/auth';
import { AuthSessionService } from '../../services/auth-session.service';
import { AuthApiClientService } from '../../services/auth-api-client.service';

interface CountryCode {
  code: string;
  country: string;
}

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

  readonly countryCodes: CountryCode[] = [
    { code: '+1', country: 'United States' },
    { code: '+91', country: 'India' }
  ];

  profileForm: FormGroup;

  // Mobile number regex: accepts exactly 10 digits, no special characters
  private readonly phonePattern = /^[0-9]{10}$/;

  constructor() {
    this.profileForm = this.formBuilder.group({
      username: [{ value: '', disabled: true }, Validators.required],
      firstName: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
      countryCode: ['+1'],
      phoneNo: ['', [Validators.minLength(10), Validators.maxLength(10), Validators.pattern(this.phonePattern)]],
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

      // Parse phone number to extract country code and number
      let countryCode = '+1';
      let phoneNo = user.phoneNo || '';

      if (phoneNo) {
        if (phoneNo.startsWith('+91')) {
          countryCode = '+91';
          phoneNo = phoneNo.substring(3);
        } else if (phoneNo.startsWith('+1')) {
          countryCode = '+1';
          phoneNo = phoneNo.substring(2);
        }
      }

      this.profileForm.patchValue({
        username: user.username,
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        email: user.email || '',
        countryCode,
        phoneNo,
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
      // Combine country code with phone number
      const fullPhoneNo = formValue.phoneNo ? `${formValue.countryCode}${formValue.phoneNo}` : '';

      // Call backend endpoint to update profile via PATCH /auth/profile
      await this.authApi.client.authProfilePatch({
        updateProfileRequest: {
          firstName: formValue.firstName,
          lastName: formValue.lastName,
          email: formValue.email,
          phoneNo: fullPhoneNo
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
