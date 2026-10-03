import { test, expect } from '@playwright/test';

test.describe('Login Journey', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://localhost:4200';
  const authApiUrl = process.env.E2E_AUTH_API || 'http://localhost:3000';
  const username = process.env.E2E_USERNAME || 'e2e.tester';
  const password = process.env.E2E_PASSWORD || 'correct horse battery staple';

  test.beforeEach(async ({ page }) => {
    // Start from the login page
    await page.goto(`${baseUrl}/login`);
  });

  test('guard redirects unauthenticated visitor to login', async ({ page }) => {
    // Try to access dashboard without authentication
    await page.goto(`${baseUrl}/dashboard`);
    
    // Should be redirected to login
    await expect(page).toHaveURL(/\/login/);
  });

  test('refused sign-in shows readable error', async ({ page }) => {
    // Enter invalid credentials
    await page.fill('[data-testid="username-input"]', 'nonexistent');
    await page.fill('[data-testid="password-input"]', 'wrongpassword');
    
    // Submit the form
    await page.click('[data-testid="sign-in-button"]');
    
    // Check for error message
    const errorElement = page.locator('[data-testid="auth-error-invalid-credentials"]');
    await expect(errorElement).toContainText(/Wrong username or password|Invalid username or password|Cannot reach Auth service/);
  });

  test('successful sign-in stores token and redirects to dashboard', async ({ page }) => {
    // Enter valid credentials
    await page.fill('[data-testid="username-input"]', username);
    await page.fill('[data-testid="password-input"]', password);
    
    // Submit the form
    await page.click('[data-testid="sign-in-button"]');
    
    // Should be redirected to dashboard (wait up to 10 seconds)
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
    
    // Check that we can see dashboard content
    const dashboardHeader = page.locator('text=Trading Dashboard');
    await expect(dashboardHeader).toBeVisible();
  });

  test('sign-out clears session and redirects to login', async ({ page }) => {
    // First sign in
    await page.fill('[data-testid="username-input"]', username);
    await page.fill('[data-testid="password-input"]', password);
    await page.click('[data-testid="sign-in-button"]');
    
    // Wait for dashboard to load
    await expect(page).toHaveURL(/\/dashboard/);
    
    // Open the profile dropdown in the header, then click Sign out.
    const profileMenuButton = page.locator('button[aria-label="Open profile menu"]');
    await expect(profileMenuButton).toBeVisible();
    await profileMenuButton.click();

    const signOutButton = page.locator('.profile-dropdown button').filter({ hasText: 'Sign out' });
    await expect(signOutButton).toBeVisible();
    await signOutButton.click();
    
    // Should be redirected to login
    await expect(page).toHaveURL(/\/login/);
  });

  test('valid credentials remain across page refresh', async ({ page }) => {
    // Sign in
    await page.fill('[data-testid="username-input"]', username);
    await page.fill('[data-testid="password-input"]', password);
    await page.click('[data-testid="sign-in-button"]');
    
    // Wait for dashboard
    await expect(page).toHaveURL(/\/dashboard/);
    
    // Refresh the page
    await page.reload();
    
    // Should still be on dashboard (token was stored)
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('empty fields are blocked by validation', async ({ page }) => {
    // Leave fields empty and try to submit
    const submitButton = page.locator('[data-testid="sign-in-button"]');
    
    // Button should be disabled
    await expect(submitButton).toBeDisabled();
    
    // Enter only username
    await page.fill('[data-testid="username-input"]', username);
    
    // Button should still be disabled (password missing)
    await expect(submitButton).toBeDisabled();
    
    // Clear username and enter password
    await page.fill('[data-testid="username-input"]', '');
    await page.fill('[data-testid="password-input"]', password);
    
    // Button should still be disabled (username missing)
    await expect(submitButton).toBeDisabled();
  });
});
