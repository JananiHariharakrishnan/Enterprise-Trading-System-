import { ComponentFixture, TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ProfileComponent } from './profile.component';
import { AuthSessionService } from '../../services/auth-session.service';
import { AuthApiClientService } from '../../services/auth-api-client.service';
// Removed raw template/style imports; rely on TestBed to compile component resources.

import { CustomerPreferencesService } from '../../services/customer-preferences.service';
import { ThemeService } from '../../services/theme.service';

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let mockAuthSession: AuthSessionService;
  let mockAuthApi: AuthApiClientService;
  let mockRouter: Router;
  let mockPreferencesApi: CustomerPreferencesService;
  let mockThemeService: ThemeService;

  const renderWithoutInit = () => {
    vi.spyOn(component, 'ngOnInit').mockImplementation(() => undefined);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    mockAuthSession = {
      loadCurrentUser: vi.fn().mockResolvedValue(null),
      clearCurrentUserCache: vi.fn()
    } as unknown as AuthSessionService;

    mockAuthApi = {
      client: {
        authProfilePatch: vi.fn().mockResolvedValue(null)
      }
    } as unknown as AuthApiClientService;

    mockRouter = {
      navigate: vi.fn()
    } as unknown as Router;

    mockPreferencesApi = {
      upsertPreferences: vi.fn().mockResolvedValue(null),
      getPreferences: vi.fn().mockResolvedValue(null),
      defaultPreferences: vi.fn().mockReturnValue({
        defaultAccountId: 1,
        trading: { defaultOrderType: 'MARKET', skipOrderConfirmation: false, defaultTradeQuantity: 1 },
        notifications: { tradeFills: ['IN_APP'], priceAlerts: ['IN_APP'] },
        display: { theme: 'SYSTEM', baseCurrency: 'USD' }
      })
    } as unknown as CustomerPreferencesService;

    mockThemeService = {
      applyThemePreference: vi.fn()
    } as unknown as ThemeService;

    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [ProfileComponent, ReactiveFormsModule],
      providers: [
        { provide: AuthSessionService, useValue: mockAuthSession },
        { provide: AuthApiClientService, useValue: mockAuthApi },
        { provide: Router, useValue: mockRouter },
        { provide: CustomerPreferencesService, useValue: mockPreferencesApi },
        { provide: ThemeService, useValue: mockThemeService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load profile on init', async () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890',
      accountId: 8,
      roles: [],
      createdOn: '2024-01-01'
    };

    vi.mocked(mockAuthSession.loadCurrentUser).mockResolvedValue(mockUser as any);

    fixture.detectChanges();

    await new Promise(resolve => setTimeout(resolve, 100));

    expect(mockAuthSession.loadCurrentUser).toHaveBeenCalled();
    expect(component.currentUser()).toEqual(mockUser);
    expect(component.isLoading()).toBe(false);
  });

  it('should handle load profile error', async () => {
    vi.mocked(mockAuthSession.loadCurrentUser).mockRejectedValue(new Error('Load failed'));

    fixture.detectChanges();

    await new Promise(resolve => setTimeout(resolve, 100));

    expect(component.errorMessage()).toBeTruthy();
    expect(component.isLoading()).toBe(false);
  });

  it('scrolls to the top on refresh and clears a load error after 5 seconds', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    vi.mocked(mockAuthSession.loadCurrentUser).mockRejectedValue(new Error('Load failed'));
    renderWithoutInit();

    vi.useFakeTimers();
    try {
      component.refreshPage();
      await vi.advanceTimersByTimeAsync(0);
      expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
      expect(component.errorMessage()).toBe('Failed to load profile information.');

      await vi.advanceTimersByTimeAsync(4999);
      expect(component.errorMessage()).toBe('Failed to load profile information.');
      await vi.advanceTimersByTimeAsync(1);
      expect(component.errorMessage()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('should validate firstName field', () => {
    const firstNameControl = component.profileForm.get('firstName');

    firstNameControl?.setValue('');
    expect(firstNameControl?.hasError('required')).toBe(true);

    firstNameControl?.setValue('A'.repeat(101));
    expect(firstNameControl?.hasError('maxlength')).toBe(true);

    firstNameControl?.setValue('John');
    expect(firstNameControl?.valid).toBe(true);
  });

  it('should validate lastName field', () => {
    const lastNameControl = component.profileForm.get('lastName');

    lastNameControl?.setValue('');
    expect(lastNameControl?.hasError('required')).toBe(true);

    lastNameControl?.setValue('A'.repeat(101));
    expect(lastNameControl?.hasError('maxlength')).toBe(true);

    lastNameControl?.setValue('Doe');
    expect(lastNameControl?.valid).toBe(true);
  });

  it('should validate email field', () => {
    const emailControl = component.profileForm.get('email');

    emailControl?.setValue('');
    expect(emailControl?.hasError('required')).toBe(true);

    emailControl?.setValue('invalid-email');
    expect(emailControl?.hasError('email')).toBe(true);

    emailControl?.setValue('valid@example.com');
    expect(emailControl?.valid).toBe(true);
  });

  it('should validate phoneNo field (optional)', () => {
    const phoneControl = component.profileForm.get('phoneNo');

    // Empty phone is valid (optional, no required validator)
    phoneControl?.setValue('');
    expect(phoneControl?.valid).toBe(true);

    // Too short
    phoneControl?.setValue('123');
    expect(phoneControl?.hasError('minlength')).toBe(true);

    // Too long
    phoneControl?.setValue('A'.repeat(21));
    expect(phoneControl?.hasError('maxlength')).toBe(true);

    // Valid: 10 digits only (no special chars or spaces)
    phoneControl?.setValue('5551234567');
    expect(phoneControl?.valid).toBe(true);
  });

  it('should disable submit button when form is invalid', async () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '5551234567',
      accountId: 8,
      roles: [],
      createdOn: new Date('2024-01-01')
    };

    vi.mocked(mockAuthSession.loadCurrentUser).mockResolvedValue(mockUser as any);

    fixture.detectChanges(); // Triggers ngOnInit -> loadProfile()

    // Wait for async operations to complete
    await new Promise(resolve => setTimeout(resolve, 150));
    fixture.detectChanges();

    const submitButton = fixture.debugElement.nativeElement.querySelector('[data-testid="profile-save-button"]') as HTMLButtonElement;

    // Form should be valid when all fields are populated correctly
    expect(submitButton.disabled).toBe(false);

    // Clear a required field to make form invalid
    component.profileForm.patchValue({
      firstName: ''
    });
    fixture.detectChanges();

    expect(submitButton.disabled).toBe(true);
  });

  it('should submit form with all profile fields', async () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+15551234567',
      accountId: 8,
      roles: [],
      createdOn: new Date('2024-01-01')
    };

    vi.mocked(mockAuthSession.loadCurrentUser).mockResolvedValue(mockUser as any);
    vi.mocked(mockAuthApi.client.authProfilePatch).mockResolvedValue(mockUser as any);

    fixture.detectChanges();

    await new Promise(resolve => setTimeout(resolve, 100));

    component.profileForm.patchValue({
      firstName: 'Jane',
      lastName: 'Smith',
      email: 'jane@example.com',
      phoneNo: '5559876543'
    });

    await component.onSubmit();

    expect(mockAuthApi.client.authProfilePatch).toHaveBeenCalledWith({
      updateProfileRequest: {
        firstName: 'Jane',
        lastName: 'Smith',
        email: 'jane@example.com',
        phoneNo: '+15559876543'
      }
    });
    expect(component.successMessage()).toBeTruthy();
  });

  it('should render loading spinner while profile is loading', () => {
    component.isLoading.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Loading profile...');
  });

  it('should render error and success messages in template', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.errorMessage.set('Failed to update profile. Please try again.');
    component.successMessage.set('Profile updated successfully!');
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('[data-testid="profile-error"]') as HTMLElement;
    const success = fixture.nativeElement.querySelector('[data-testid="profile-success"]') as HTMLElement;

    expect(error).toBeTruthy();
    expect(success).toBeTruthy();
    expect(error.textContent).toContain('Failed to update profile. Please try again.');
    expect(success.textContent).toContain('Profile updated successfully!');
  });

  it('should show validation messages for touched invalid fields', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: '',
      lastName: '',
      email: '',
      phoneNo: ''
    } as any);

    component.profileForm.patchValue({
      firstName: '',
      lastName: '',
      email: 'invalid-email',
      phoneNo: '123'
    });

    component.profileForm.get('firstName')?.markAsTouched();
    component.profileForm.get('lastName')?.markAsTouched();
    component.profileForm.get('email')?.markAsTouched();
    component.profileForm.get('phoneNo')?.markAsTouched();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-firstName-error-required"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-lastName-error-required"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-email-error-invalid"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-phoneNo-error-minlength"]')).toBeTruthy();
  });

  it('should set editable fields to readonly while saving', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.isSaving.set(true);
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    } as any);
    component.profileForm.patchValue({
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    });
    fixture.detectChanges();

    const firstName = fixture.nativeElement.querySelector('[data-testid="profile-firstName-input"]') as HTMLInputElement;
    const lastName = fixture.nativeElement.querySelector('[data-testid="profile-lastName-input"]') as HTMLInputElement;
    const email = fixture.nativeElement.querySelector('[data-testid="profile-email-input"]') as HTMLInputElement;
    const phone = fixture.nativeElement.querySelector('[data-testid="profile-phoneNo-input"]') as HTMLInputElement;
    const backButton = fixture.nativeElement.querySelector('[data-testid="profile-back-button"]') as HTMLButtonElement;

    expect(firstName.readOnly).toBe(true);
    expect(lastName.readOnly).toBe(true);
    expect(email.readOnly).toBe(true);
    expect(phone.readOnly).toBe(true);
    expect(backButton.disabled).toBe(true);
  });

  it('should navigate back to dashboard from back button click', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    } as any);
    fixture.detectChanges();

    const backButton = fixture.nativeElement.querySelector('[data-testid="profile-back-button"]') as HTMLButtonElement;
    backButton.click();

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should initialize notification channels as in-app and keep email optional', () => {
    expect(component.profileForm.get('tradeFills')?.value).toEqual(['IN_APP']);
    expect(component.profileForm.get('priceAlerts')?.value).toEqual(['IN_APP']);

    component.profileForm.patchValue({
      tradeFills: ['IN_APP', 'EMAIL'],
      priceAlerts: ['EMAIL']
    });
    expect(component.profileForm.get('tradeFills')?.value).toEqual(['IN_APP', 'EMAIL']);
    expect(component.profileForm.get('priceAlerts')?.value).toEqual(['EMAIL']);
  });

  it('should render trade fill and price alert selects with in-app and email', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    } as any);
    fixture.detectChanges();

    const fills = fixture.nativeElement.querySelector('[data-testid="profile-tradeFills-select"]') as HTMLSelectElement;
    const alerts = fixture.nativeElement.querySelector('[data-testid="profile-priceAlerts-select"]') as HTMLSelectElement;
    expect(fills).toBeTruthy();
    expect(alerts).toBeTruthy();
    expect([...fills.options].map((option) => option.text.trim())).toEqual(['In app', 'Email']);
    expect([...alerts.options].map((option) => option.text.trim())).toEqual(['In app', 'Email']);
  });

  it('should save the selected notification channels', async () => {
    renderWithoutInit();
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    } as any);
    component.profileForm.patchValue({
      accountId: 8,
      tradeFills: ['IN_APP'],
      priceAlerts: ['IN_APP', 'EMAIL']
    });

    await component.onSavePreferences();

    expect(mockPreferencesApi.upsertPreferences).toHaveBeenCalledWith(8, expect.objectContaining({
      notifications: expect.objectContaining({
        tradeFills: ['IN_APP'],
        priceAlerts: ['IN_APP', 'EMAIL']
      })
    }));
  });

  it('labels roles and preference choices', () => {
    expect(component.roleLabel()).toBe('Customer');
    component.currentUser.set({ roles: 'admin' } as any);
    expect(component.roleLabel()).toBe('Admin');
    component.currentUser.set({ roles: ['CUSTOMER', 'TRADER'] } as any);
    expect(component.roleLabel()).toBe('Customer, TRADER');

    component.setChoice('theme', 'DARK');
    expect(component.isChoice('theme', 'DARK')).toBe(true);
    component.isSaving.set(true);
    component.setChoice('theme', 'LIGHT');
    expect(component.isChoice('theme', 'DARK')).toBe(true);
    component.isSaving.set(false);

    expect(component.channelLabel('IN_APP')).toBe('In app');
    expect(component.channelLabel('EMAIL')).toBe('Email');
    expect(component.channelLabel('SMS')).toBe('SMS');
    expect(component.orderTypeLabel('MARKET')).toBe('Market');
    expect(component.orderTypeLabel('LIMIT')).toBe('Limit');
    expect(component.orderTypeLabel('STOP')).toBe('Stop');
    expect(component.themeLabel('LIGHT')).toBe('Light');
    expect(component.themeLabel('DARK')).toBe('Dark');
    expect(component.themeLabel('SYSTEM')).toBe('System');
  });

  it('toggles channels and explains when email alerts need a saved address', async () => {
    expect(component.isChannelSelected('tradeFills', 'IN_APP')).toBe(true);
    component.toggleChannel('tradeFills', 'EMAIL');
    expect(component.isChannelSelected('tradeFills', 'EMAIL')).toBe(true);
    component.toggleChannel('tradeFills', 'IN_APP');
    expect(component.isChannelSelected('tradeFills', 'IN_APP')).toBe(false);
    component.isSaving.set(true);
    component.toggleChannel('priceAlerts', 'EMAIL');
    component.isSaving.set(false);

    component.currentUser.set({ email: '   ' } as any);
    const blocked = document.createElement('input');
    blocked.checked = true;
    await component.onEmailNotificationToggle({ target: blocked } as unknown as Event);
    expect(blocked.checked).toBe(false);
    expect(component.emailOptInError()).toBe('Update your email first to enable this');

    component.currentUser.set({ email: 'ada@example.com' } as any);
    component.profileForm.patchValue({ accountId: 8 });
    const enabled = document.createElement('input');
    enabled.checked = true;
    await component.onEmailNotificationToggle({ target: enabled } as unknown as Event);
    expect(component.emailNotificationsEnabled()).toBe(true);

    vi.mocked(mockPreferencesApi.upsertPreferences).mockRejectedValueOnce(new Error('down'));
    const failed = document.createElement('input');
    failed.checked = false;
    await component.onEmailNotificationToggle({ target: failed } as unknown as Event);
    expect(component.errorMessage()).toContain('Failed to update email notification preference');
    expect(failed.checked).toBe(true);
  });

  it('rejects a preference save without an account and reports a save failure', async () => {
    component.profileForm.patchValue({ accountId: 0 });
    await component.onSavePreferences();
    expect(component.errorMessage()).toContain('Unable to determine account');

    component.profileForm.patchValue({ accountId: 8 });
    vi.mocked(mockPreferencesApi.upsertPreferences).mockRejectedValueOnce(new Error('down'));
    await component.onSavePreferences();
    expect(component.errorMessage()).toContain('Failed to save preferences');
  });

  it('renders default trade quantity input and validates positive integer', () => {
    renderWithoutInit();
    component.isLoading.set(false);
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    } as any);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('[data-testid="profile-defaultTradeQuantity-input"]') as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(component.profileForm.get('defaultTradeQuantity')?.value).toBe(1);

    component.profileForm.patchValue({ defaultTradeQuantity: 0 });
    expect(component.profileForm.get('defaultTradeQuantity')?.valid).toBe(false);

    component.profileForm.patchValue({ defaultTradeQuantity: 25 });
    expect(component.profileForm.get('defaultTradeQuantity')?.valid).toBe(true);
  });

  it('persists preferred defaultTradeQuantity when saving preferences', async () => {
    renderWithoutInit();
    component.currentUser.set({
      id: '1',
      username: 'testuser',
      accountId: 8,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890'
    } as any);
    component.profileForm.patchValue({
      accountId: 8,
      defaultTradeQuantity: 50
    });

    await component.onSavePreferences();

    expect(mockPreferencesApi.upsertPreferences).toHaveBeenCalledWith(8, expect.objectContaining({
      trading: expect.objectContaining({
        defaultTradeQuantity: 50
      })
    }));
  });
});
