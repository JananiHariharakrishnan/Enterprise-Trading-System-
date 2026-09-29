import { test, expect } from '@playwright/test';

test.describe('Register Journey', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://localhost:4200';
  const accountId = process.env.E2E_ACCOUNT_ID || '9';
  
  // Generate a unique username for each test run to avoid conflicts
  const generateUniqueUsername = (): string => {
    return `user_${Date.now()}_${Math.random().toString(36).substring(7)}`;
  };

  test.beforeEach(async ({ page }) => {
    // Start from the home page
    await page.goto(`${baseUrl}/`);
  });

  test('registration form validates before submission', async ({ page }) => {
    // Click on register link
    await page.click('[data-testid="home-link-register"]');
    
    // Check that we're on the register page
    await expect(page).toHaveURL(/\/register/);
    
    // Get the submit button
    const submitButton = page.locator('[data-testid="register-button"]');
    
    // Button should initially be disabled (all fields empty)
    await expect(submitButton).toBeDisabled();
    
    // Fill in partial data
    await page.fill('[data-testid="register-username-input"]', 'ab'); // Too short
    await expect(submitButton).toBeDisabled();
    
    // Fill in account ID
    await page.fill('[data-testid="register-account-input"]', accountId);
    
    // Password still needed
    await expect(submitButton).toBeDisabled();
    
    // Fill in password that's too short
    await page.fill('[data-testid="register-password-input"]', 'short');
    await expect(submitButton).toBeDisabled();
    
    // Fill in valid password
    await page.fill('[data-testid="register-password-input"]', 'correct horse battery staple');
    
    // Confirm password still empty
    await expect(submitButton).toBeDisabled();
    
    // Fill in confirm password with mismatch
    await page.fill('[data-testid="register-confirm-password-input"]', 'different password');
    await expect(submitButton).toBeDisabled();
    
    // Fix the confirm password
    await page.fill('[data-testid="register-confirm-password-input"]', 'correct horse battery staple');
    await expect(submitButton).toBeDisabled(); // Username is still too short
    
    // Fix the username
    await page.fill('[data-testid="register-username-input"]', generateUniqueUsername());
    
    // Now button should be enabled
    await expect(submitButton).toBeEnabled();
  });

  test('successful registration creates account and redirects to dashboard', async ({ page }) => {
    // Navigate to register
    await page.click('[data-testid="home-link-register"]');
    await expect(page).toHaveURL(/\/register/);
    
    // Generate unique username
    const uniqueUsername = generateUniqueUsername();
    
    // Fill in the form with valid data
    await page.fill('[data-testid="register-username-input"]', uniqueUsername);
    await page.fill('[data-testid="register-account-input"]', accountId);
    await page.fill('[data-testid="register-password-input"]', 'correct horse battery staple');
    await page.fill('[data-testid="register-confirm-password-input"]', 'correct horse battery staple');
    
    // Submit the form
    await page.click('[data-testid="register-button"]');
    
    // Should redirect to dashboard (after automatic login)
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('duplicate username shows error', async ({ page }) => {
    // Navigate to register
    await page.click('[data-testid="home-link-register"]');
    await expect(page).toHaveURL(/\/register/);
    
    // Use an existing username (from test setup or manual creation)
    const existingUsername = 'existing.user';
    
    // Fill in the form
    await page.fill('[data-testid="register-username-input"]', existingUsername);
    await page.fill('[data-testid="register-account-input"]', accountId);
    await page.fill('[data-testid="register-password-input"]', 'correct horse battery staple');
    await page.fill('[data-testid="register-confirm-password-input"]', 'correct horse battery staple');
    
    // Submit the form
    await page.click('[data-testid="register-button"]');
    
    // Check for error message containing "already taken" or similar
    const errorElement = page.locator('[data-testid="register-error"]');
    await expect(errorElement).toContainText(/already taken|username|Registration failed/);
  });

  test('username validation shows specific error messages', async ({ page }) => {
    // Navigate to register
    await page.click('[data-testid="home-link-register"]');
    
    // Enter username that's too short
    const usernameInput = page.locator('[data-testid="register-username-input"]');
    await usernameInput.fill('ab');
    await usernameInput.blur();
    
    const usernameError = page.locator('[data-testid="register-username-error"]');
    await expect(usernameError).toContainText(/3 characters/);
    
    // Enter username with invalid characters
    await usernameInput.fill('user@invalid');
    await usernameInput.blur();
    
    await expect(usernameError).toContainText(/letters, numbers, dots, dashes/);
  });

  test('password validation shows specific error messages', async ({ page }) => {
    // Navigate to register
    await page.click('[data-testid="home-link-register"]');
    
    // Enter password that's too short
    const passwordInput = page.locator('[data-testid="register-password-input"]');
    await passwordInput.fill('short');
    await passwordInput.blur();
    
    const passwordError = page.locator('[data-testid="register-password-error"]');
    await expect(passwordError).toContainText(/12 characters/);
  });

  test('link to sign-in is present and functional', async ({ page }) => {
    // Navigate to register
    await page.click('[data-testid="home-link-register"]');
    
    // Check that sign-in link exists
    const loginLink = page.locator('[data-testid="link-to-login"]');
    await expect(loginLink).toBeVisible();
    
    // Click it
    await loginLink.click();
    
    // Should navigate to login page
    await expect(page).toHaveURL(/\/login/);
  });
});
