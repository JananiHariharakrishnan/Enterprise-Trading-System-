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

describe('ProfileComponent', () => {
  let component: ProfileComponent;
  let fixture: ComponentFixture<ProfileComponent>;
  let mockAuthSession: AuthSessionService;
  let mockAuthApi: AuthApiClientService;
  let mockRouter: Router;

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

    // Component resources are provided centrally in src/test-setup.ts

    await TestBed.configureTestingModule({
      imports: [ProfileComponent, ReactiveFormsModule],
      providers: [
        { provide: AuthSessionService, useValue: mockAuthSession },
        { provide: AuthApiClientService, useValue: mockAuthApi },
        { provide: Router, useValue: mockRouter }
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
    
    // Empty phone is valid (optional)
    phoneControl?.setValue('');
    expect(phoneControl?.valid).toBe(true);

    // Too short
    phoneControl?.setValue('123');
    expect(phoneControl?.hasError('minlength')).toBe(true);

    // Too long
    phoneControl?.setValue('A'.repeat(21));
    expect(phoneControl?.hasError('maxlength')).toBe(true);

    // Valid
    phoneControl?.setValue('+1 (555) 123-4567');
    expect(phoneControl?.valid).toBe(true);
  });

  it('should disable submit button when form is invalid', async () => {
    const mockUser = {
      id: '1',
      username: 'testuser',
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phoneNo: '+1234567890',
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

    // Form should be valid when all fields are populated
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
      phoneNo: '+1234567890',
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
      phoneNo: '+1 (555) 987-6543'
    });

    await component.onSubmit();

    expect(mockAuthApi.client.authProfilePatch).toHaveBeenCalledWith({
      updateProfileRequest: {
        firstName: 'Jane',
        lastName: 'Smith',
        email: 'jane@example.com',
        phoneNo: '+1 (555) 987-6543'
      }
    });
    expect(component.successMessage()).toBeTruthy();
  });
});
