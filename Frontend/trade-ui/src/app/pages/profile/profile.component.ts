import { CommonModule } from '@angular/common';
import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
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
export class ProfileComponent implements OnInit, OnDestroy {
  readonly authSession = inject(AuthSessionService);
  private readonly authApi = inject(AuthApiClientService);
  private readonly preferencesApi = inject(CustomerPreferencesService, { optional: true });
  private readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly isSavingPreferences = signal(false);
  readonly isSavingEmailPreference = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly emailOptInError = signal<string | null>(null);
  readonly emailNotificationsEnabled = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);
  readonly isLoadingPreferences = signal(false);

  readonly orderTypes: OrderType[] = ['MARKET', 'LIMIT'];
  readonly notificationChannels: NotificationChannel[] = ['IN_APP', 'EMAIL'];
  readonly themeChoices: ThemeChoice[] = ['LIGHT', 'DARK', 'SYSTEM'];
  readonly baseCurrencies: BaseCurrency[] = ['USD', 'INR'];

  readonly countryCodes: CountryCode[] = [
    { code: '+1', country: 'United States' },
    { code: '+91', country: 'India' }
  ];

  profileForm: FormGroup;

  // Mobile number regex: accepts exactly 10 digits, no special characters
  private readonly phonePattern = /^[0-9]{10}$/;
  private emailOptInTimer: ReturnType<typeof setTimeout> | null = null;

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
      tradeFills: [['IN_APP'], Validators.required],
      priceAlerts: [['IN_APP'], Validators.required],
      theme: ['SYSTEM', Validators.required],
      baseCurrency: ['INR', Validators.required]
    });
  }

  ngOnInit(): void {
    void this.loadProfile();
  }

  refreshPage(): void {
    this.successMessage.set(null);
    this.errorMessage.set(null);
    void this.loadProfile();
  }

  roleLabel(): string {
    const raw = this.currentUser()?.roles ?? [];
    const roles = (Array.isArray(raw) ? raw : [raw])
      .map((role) => String(role).replace(/[^A-Za-z_]/g, '').toUpperCase())
      .filter((role) => role.length > 0);

    if (roles.length === 0) {
      return 'Customer';
    }

    return roles
      .map((role) => role === 'ADMIN' ? 'Admin' : role === 'CUSTOMER' ? 'Customer' : role)
      .join(', ');
  }

  ngOnDestroy(): void {
    if (this.emailOptInTimer) {
      clearTimeout(this.emailOptInTimer);
      this.emailOptInTimer = null;
    }
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
        phoneNo = phoneNo.replace(/\D/g, '');
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

      const phoneControl = this.profileForm.get('phoneNo');
      if (phoneControl?.invalid) {
        phoneControl.markAsTouched();
      }

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

    const existing = this.currentUser();
    if (existing) {
      this.currentUser.set({
        ...existing,
        firstName: formValue.firstName,
        lastName: formValue.lastName,
        email: formValue.email,
        phoneNo: fullPhoneNo
      });
    }

    // Clear cached user so the next profile load gets fresh data
    this.authSession.clearCurrentUserCache();

    // Show success only after every requested update has completed
    this.successMessage.set('Profile and preferences saved.');

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

  setChoice(controlName: 'defaultOrderType' | 'theme' | 'baseCurrency' | 'countryCode', value: string): void {
    if (this.isSaving()) {
      return;
    }
    this.profileForm.get(controlName)?.setValue(value);
  }

  isChoice(controlName: string, value: string): boolean {
    return this.profileForm.get(controlName)?.value === value;
  }

  isChannelSelected(controlName: 'tradeFills' | 'priceAlerts', channel: NotificationChannel): boolean {
    const value = this.profileForm.get(controlName)?.value;
    const channels = Array.isArray(value) ? value : [];
    return channels.map((item) => String(item).toUpperCase()).includes(channel);
  }

  toggleChannel(controlName: 'tradeFills' | 'priceAlerts', channel: NotificationChannel): void {
    if (this.isSaving()) {
      return;
    }
    const selected = this.normalizeChannels(this.profileForm.get(controlName)?.value);
    if (selected.includes(channel) && selected.length === 1) {
      return;
    }
    const next = selected.includes(channel)
      ? selected.filter((item) => item !== channel)
      : [...selected, channel];
    this.profileForm.get(controlName)?.setValue(next);
  }

  channelLabel(channel: NotificationChannel): string {
    if (channel === 'IN_APP') {
      return 'In app';
    }
    if (channel === 'EMAIL') {
      return 'Email';
    }
    return channel;
  }

  orderTypeLabel(orderType: OrderType): string {
    if (orderType === 'MARKET') {
      return 'Market';
    }
    if (orderType === 'LIMIT') {
      return 'Limit';
    }
    return 'Stop';
  }

  themeLabel(theme: ThemeChoice): string {
    if (theme === 'LIGHT') {
      return 'Light';
    }
    if (theme === 'DARK') {
      return 'Dark';
    }
    return 'System';
  }

  async onEmailNotificationToggle(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const enabling = input.checked;

    if (enabling && !this.hasSavedEmail()) {
      input.checked = false;
      this.emailNotificationsEnabled.set(false);
      this.showEmailRequiredNotice();
      return;
    }

    this.emailOptInError.set(null);
    this.emailNotificationsEnabled.set(enabling);
    const saved = await this.persistEmailPreference();
    if (!saved) {
      this.emailNotificationsEnabled.set(!enabling);
      input.checked = !enabling;
    }
  }

  async onSavePreferences(): Promise<void> {
    if (!this.preferencesApi) {
      this.errorMessage.set('Preferences service is unavailable.');
      return;
    }

    const accountId = Number(this.profileForm.getRawValue().accountId);
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

      this.profileForm.patchValue({
        defaultAccountId: preferences.defaultAccountId,
        defaultOrderType: preferences.trading.defaultOrderType === 'STOP'
          ? 'MARKET'
          : preferences.trading.defaultOrderType,
        skipOrderConfirmation: preferences.trading.skipOrderConfirmation,
        defaultTradeQuantity: preferences.trading.defaultTradeQuantity,
        tradeFills: this.normalizeChannels(preferences.notifications.tradeFills),
        priceAlerts: this.normalizeChannels(preferences.notifications.priceAlerts),
        theme: preferences.display.theme,
        baseCurrency: preferences.display.baseCurrency,
      });

      this.emailNotificationsEnabled.set(preferences.notifications?.emailEnabled === true);
      this.themeService.applyThemePreference(preferences.display.theme);
    } catch {
      // Preferences are optional during load; fallback values remain in the form.
    } finally {
      this.isLoadingPreferences.set(false);
    }
  }

  private hasSavedEmail(): boolean {
    return (this.currentUser()?.email?.trim() ?? '').length > 0;
  }

  private showEmailRequiredNotice(): void {
    this.emailOptInError.set('Update your email first to enable this');
    if (this.emailOptInTimer) {
      clearTimeout(this.emailOptInTimer);
    }
    this.emailOptInTimer = setTimeout(() => {
      this.emailOptInError.set(null);
      this.emailOptInTimer = null;
    }, 5000);
  }

  private async persistEmailPreference(): Promise<boolean> {
    if (!this.preferencesApi) {
      this.errorMessage.set('Preferences service is unavailable.');
      return false;
    }

    const accountId = Number(this.profileForm.getRawValue().accountId);
    if (!Number.isFinite(accountId) || accountId < 1) {
      this.errorMessage.set('Unable to determine account for preferences.');
      return false;
    }

    this.isSavingEmailPreference.set(true);
    this.errorMessage.set(null);
    try {
      await this.preferencesApi.upsertPreferences(accountId, this.toPreferencesPayload(this.profileForm.getRawValue()));
      return true;
    } catch {
      this.errorMessage.set('Failed to update email notification preference. Please try again.');
      return false;
    } finally {
      this.isSavingEmailPreference.set(false);
    }
  }

  private normalizeChannels(value: unknown): NotificationChannel[] {
    const allowed = new Set<NotificationChannel>(['IN_APP', 'EMAIL']);
    const source = Array.isArray(value)
      ? value
      : typeof value === 'string'
        ? value.split(/[\s,;|]+/)
        : [];

    const channels = source
      .map((item) => String(item).trim().toUpperCase())
      .map((item) => (item === 'INAPP' ? 'IN_APP' : item))
      .filter((item): item is NotificationChannel => allowed.has(item as NotificationChannel));

    return channels.length > 0 ? [...new Set(channels)] : ['IN_APP'];
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
      emailEnabled: boolean;
    };
    display: {
      theme: ThemeChoice;
      baseCurrency: BaseCurrency;
    };
  } {
    return {
      defaultAccountId: Number(formValue.defaultAccountId),
      trading: {
        defaultOrderType: formValue.defaultOrderType,
        skipOrderConfirmation: Boolean(formValue.skipOrderConfirmation),
        defaultTradeQuantity: Number(formValue.defaultTradeQuantity),
      },
      notifications: {
        tradeFills: this.normalizeChannels(formValue.tradeFills),
        priceAlerts: this.normalizeChannels(formValue.priceAlerts),
        emailEnabled: this.emailNotificationsEnabled(),
      },
      display: {
        theme: formValue.theme,
        baseCurrency: formValue.baseCurrency,
      },
    };
  }
}
