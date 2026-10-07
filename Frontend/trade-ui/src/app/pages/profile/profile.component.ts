import { CommonModule } from '@angular/common';
import { Component, inject, signal, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserResponse } from '../../generated/auth';
import { AuthSessionService } from '../../services/auth-session.service';
import { AuthApiClientService } from '../../services/auth-api-client.service';
import { CustomerPreferencesService, type BaseCurrency, type NotificationChannel, type OrderType, type ThemeChoice } from '../../services/customer-preferences.service';
import { ThemeService } from '../../services/theme.service';

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
  private readonly preferencesApi = inject(CustomerPreferencesService, { optional: true });
  private readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly isSavingPreferences = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly currentUser = signal<UserResponse | null>(null);
  readonly isLoadingPreferences = signal(false);

  readonly orderTypes: OrderType[] = ['MARKET', 'LIMIT', 'STOP'];
  readonly notificationChannels: NotificationChannel[] = [
    'NO NOTIFICATION',
    'IN_APP',
    'EMAIL'
  ];
  readonly themeChoices: ThemeChoice[] = ['LIGHT', 'DARK', 'SYSTEM'];
  readonly baseCurrencies: BaseCurrency[] = ['USD', 'INR'];

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
      accountId: [{ value: '', disabled: true }, Validators.required],
      defaultAccountId: [1, [Validators.required, Validators.min(1)]],
      defaultOrderType: ['MARKET', Validators.required],
      skipOrderConfirmation: [false],
      defaultTradeQuantity: [1, [Validators.required, Validators.min(1)]],
      notificationsAndAlerts: ['YES', Validators.required],
      tradeFills: [['IN_APP', 'EMAIL'], Validators.required],
      priceAlerts: [['IN_APP', 'EMAIL'], Validators.required],
      theme: ['SYSTEM', Validators.required],
      baseCurrency: ['USD', Validators.required]
    });

    this.profileForm.get('notificationsAndAlerts')?.valueChanges.subscribe((val) => {
      const isYes = val === 'YES';
      this.profileForm.patchValue({
        tradeFills: isYes ? ['IN_APP', 'EMAIL'] : ['NO NOTIFICATION'],
        priceAlerts: isYes ? ['IN_APP', 'EMAIL'] : ['NO NOTIFICATION'],
      }, { emitEvent: false });

      if (!this.isLoading() && this.currentUser()) {
        void this.onSavePreferences();
      }
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

      // Mark profile load complete before loading optional preferences to
      // avoid test timing races where `isLoading` remains true while
      // background preference loading occurs.
      this.isLoading.set(false);

      await this.loadPreferences(user.accountId);
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
    const fullPhoneNo = formValue.phoneNo
      ? `${formValue.countryCode}${formValue.phoneNo}`
      : '';

    // Update profile
    await this.authApi.client.authProfilePatch({
      updateProfileRequest: {
        firstName: formValue.firstName,
        lastName: formValue.lastName,
        email: formValue.email,
        phoneNo: fullPhoneNo
      }
    });

    // Update preferences if the preferences service is available
    if (this.preferencesApi) {
      const accountId = Number(formValue.accountId);

      if (!Number.isFinite(accountId) || accountId < 1) {
        this.errorMessage.set('Unable to determine account for preferences.');
        return;
      }

      await this.preferencesApi.upsertPreferences(
        accountId,
        this.toPreferencesPayload(formValue)
      );

      this.themeService.applyThemePreference(
        formValue.theme as ThemeChoice
      );
    }

    // Clear cached user so the next profile load gets fresh data
    this.authSession.clearCurrentUserCache();

    // Show success only after every requested update has completed
    this.successMessage.set('Profile updated successfully!');

  } catch {
    this.errorMessage.set(
      'Failed to update profile. Please try again.'
    );
    this.successMessage.set(null);

  } finally {
    this.isSaving.set(false);
  }
}

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }

  async onSavePreferences(): Promise<void> {
    if (!this.preferencesApi) {
      this.errorMessage.set('Preferences service is unavailable.');
      return;
    }

    const formAccountId = Number(this.profileForm.getRawValue().accountId);
    const userAccountId = Number(this.currentUser()?.accountId);
    const accountId = Number.isFinite(formAccountId) && formAccountId > 0
      ? formAccountId
      : (Number.isFinite(userAccountId) && userAccountId > 0 ? userAccountId : NaN);

    if (!Number.isFinite(accountId) || accountId < 1) {
      this.errorMessage.set('Unable to determine account for preferences.');
      return;
    }

    this.isSavingPreferences.set(true);
    this.successMessage.set(null);
    this.errorMessage.set(null);

    try {
      const formValue = this.profileForm.getRawValue();
      await this.preferencesApi.upsertPreferences(accountId, this.toPreferencesPayload(formValue));
      this.themeService.applyThemePreference(formValue.theme as ThemeChoice);
      this.successMessage.set('Preferences saved successfully!');
    } catch {
      this.errorMessage.set('Failed to save preferences. Please try again.');
    } finally {
      this.isSavingPreferences.set(false);
    }
  }

  private async loadPreferences(accountId: number): Promise<void> {
    if (!this.preferencesApi) {
      return;
    }

    this.isLoadingPreferences.set(true);
    try {
      const preferences = await this.preferencesApi.getPreferences(accountId)
        ?? this.preferencesApi.defaultPreferences(accountId);

      const isTurnedOff = this.isNotificationsTurnedOff(preferences.notifications);
      const notificationsAndAlertsValue = isTurnedOff ? 'NO' : 'YES';

      this.profileForm.patchValue({
        defaultAccountId: preferences.defaultAccountId,
        defaultOrderType: preferences.trading.defaultOrderType,
        skipOrderConfirmation: preferences.trading.skipOrderConfirmation,
        defaultTradeQuantity: preferences.trading.defaultTradeQuantity,
        notificationsAndAlerts: notificationsAndAlertsValue,
        tradeFills: isTurnedOff ? ['NO NOTIFICATION'] : ['IN_APP', 'EMAIL'],
        priceAlerts: isTurnedOff ? ['NO NOTIFICATION'] : ['IN_APP', 'EMAIL'],
        theme: preferences.display.theme,
        baseCurrency: preferences.display.baseCurrency,
      });

      this.themeService.applyThemePreference(preferences.display.theme);
    } catch {
      // Preferences are optional during load; fallback values remain in the form.
    } finally {
      this.isLoadingPreferences.set(false);
    }
  }

  private isNotificationsTurnedOff(notifications?: { tradeFills?: unknown; priceAlerts?: unknown }): boolean {
    if (!notifications) {
      return false;
    }
    const hasNoNotification = (channels: unknown): boolean => {
      if (!channels) return false;
      const list = Array.isArray(channels) ? channels : [channels];
      return list.some((item) => {
        const str = String(item).trim().toUpperCase();
        return str === 'NO NOTIFICATION' || str === 'NO_NOTIFICATION';
      });
    };

    return hasNoNotification(notifications.tradeFills) || hasNoNotification(notifications.priceAlerts);
  }

  private normalizeChannels(value: unknown): NotificationChannel[] {
    const allowed = new Set<NotificationChannel>([
      'NO NOTIFICATION',
      'IN_APP',
      'EMAIL'
    ]);
    const source = Array.isArray(value)
      ? value
      : typeof value === 'string'
        ? value.split(/[\s,;|]+/)
        : [];

    const channels = source
      .map((item) => String(item).trim().toUpperCase())
      .map((item) => (item === 'INAPP' ? 'IN_APP' : item === 'NO_NOTIFICATION' ? 'NO NOTIFICATION' : item))
      .filter((item): item is NotificationChannel => allowed.has(item as NotificationChannel));

    return channels.length > 0 ? [...new Set(channels)] : ['IN_APP', 'EMAIL'];
  }

  private toPreferencesPayload(formValue: any): {
    defaultAccountId: number;
    trading: {
      defaultOrderType: OrderType;
      skipOrderConfirmation: boolean;
      defaultTradeQuantity: number;
    };
    notifications: {
      tradeFills: NotificationChannel[];
      priceAlerts: NotificationChannel[];
    };
    display: {
      theme: ThemeChoice;
      baseCurrency: BaseCurrency;
    };
  } {
    const isNotificationsEnabled = formValue.notificationsAndAlerts !== 'NO';
    const channels: NotificationChannel[] = isNotificationsEnabled
      ? ['IN_APP', 'EMAIL']
      : ['NO NOTIFICATION'];

    return {
      defaultAccountId: Number(formValue.defaultAccountId),
      trading: {
        defaultOrderType: formValue.defaultOrderType,
        skipOrderConfirmation: Boolean(formValue.skipOrderConfirmation),
        defaultTradeQuantity: Number(formValue.defaultTradeQuantity),
      },
      notifications: {
        tradeFills: formValue.notificationsAndAlerts !== undefined
          ? channels
          : this.normalizeChannels(formValue.tradeFills),
        priceAlerts: formValue.notificationsAndAlerts !== undefined
          ? channels
          : this.normalizeChannels(formValue.priceAlerts),
      },
      display: {
        theme: formValue.theme,
        baseCurrency: formValue.baseCurrency,
      },
    };
  }
}
