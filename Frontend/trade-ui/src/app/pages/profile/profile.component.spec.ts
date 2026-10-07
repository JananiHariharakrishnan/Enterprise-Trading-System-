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

  it('should initialize notificationsAndAlerts as YES by default and update channels', () => {
    expect(component.profileForm.get('notificationsAndAlerts')?.value).toBe('YES');

    component.profileForm.patchValue({ notificationsAndAlerts: 'NO' });
    expect(component.profileForm.get('tradeFills')?.value).toEqual(['NO NOTIFICATION']);
    expect(component.profileForm.get('priceAlerts')?.value).toEqual(['NO NOTIFICATION']);

    component.profileForm.patchValue({ notificationsAndAlerts: 'YES' });
    expect(component.profileForm.get('tradeFills')?.value).toEqual(['IN_APP', 'EMAIL']);
    expect(component.profileForm.get('priceAlerts')?.value).toEqual(['IN_APP', 'EMAIL']);
  });

  it('should render Notifications and Alerts select with only Yes and No options', () => {
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

    const select = fixture.nativeElement.querySelector('[data-testid="profile-notificationsAndAlerts-select"]') as HTMLSelectElement;
    expect(select).toBeTruthy();
    expect(select.options.length).toBe(2);
    expect(select.options[0].value).toBe('YES');
    expect(select.options[0].text.trim()).toBe('Yes');
    expect(select.options[1].value).toBe('NO');
    expect(select.options[1].text.trim()).toBe('No');
  });

  it('should correctly save preferences with notificationsAndAlerts NO and YES', async () => {
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
      notificationsAndAlerts: 'NO'
    });

    await component.onSavePreferences();

    expect(mockPreferencesApi.upsertPreferences).toHaveBeenCalledWith(8, expect.objectContaining({
      notifications: {
        tradeFills: ['NO NOTIFICATION'],
        priceAlerts: ['NO NOTIFICATION']
      }
    }));

    component.profileForm.patchValue({
      notificationsAndAlerts: 'YES'
    });

    await component.onSavePreferences();

    expect(mockPreferencesApi.upsertPreferences).toHaveBeenCalledWith(8, expect.objectContaining({
      notifications: {
        tradeFills: ['IN_APP', 'EMAIL'],
        priceAlerts: ['IN_APP', 'EMAIL']
      }
    }));
  });
});
