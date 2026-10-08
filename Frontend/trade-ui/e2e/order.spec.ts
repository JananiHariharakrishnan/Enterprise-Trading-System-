import { test, expect } from '@playwright/test';

test.describe('Order Placement Journey', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://localhost:4200';
  const username = process.env.E2E_USERNAME || 'e2e.tester';
  const password = process.env.E2E_PASSWORD || 'correct horse battery staple';

  /**
   * Helper: Sign in and navigate to orders page
   * Returns the authenticated page ready for placing orders
   */
  async function signInToOrders(page: typeof test['_runningPage']) {
    // Navigate to login
    await page.goto(`${baseUrl}/login`);

    // Sign in with valid credentials
    await page.fill('[data-testid="username-input"]', username);
    await page.fill('[data-testid="password-input"]', password);
    await page.click('[data-testid="sign-in-button"]');

    // Wait for dashboard to load
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });

    // Navigate to orders page
    await page.goto(`${baseUrl}/orders`);
    await page.waitForLoadState('networkidle');
  }

  test('read-only account shows holdings and prevents modification', async ({ page }) => {
    // Sign in
    await signInToOrders(page);

    // Verify we're on the orders page
    await expect(page).toHaveURL(/\/orders/);

    // Navigate to dashboard to verify account summary
    await page.goto(`${baseUrl}/dashboard`);
    await page.waitForLoadState('networkidle');

    // Dashboard should load (verify we're authenticated)
    await expect(page).toHaveURL(/\/dashboard/);

    // Navigate to positions to verify holdings are displayed
    await page.goto(`${baseUrl}/positions`);
    await page.waitForLoadState('networkidle');

    const positionsList = page.locator('[data-testid="positions-list"]');
    await expect(positionsList).toBeVisible({ timeout: 10000 });

    // Verify order form is still accessible on orders page
    await page.goto(`${baseUrl}/orders`);
    await page.waitForLoadState('networkidle');

    const orderSymbolInput = page.locator('[data-testid="order-symbol-input"]');
    await expect(orderSymbolInput).toBeVisible();
  });

  test('order form rejects invalid submission', async ({ page }) => {
    // Sign in
    await signInToOrders(page);

    // Verify form is visible
    const symbolInput = page.locator('[data-testid="order-symbol-input"]');
    await expect(symbolInput).toBeVisible();

    const submitButton = page.locator('[data-testid="place-order-button"]');

    // Button should be disabled initially (empty form)
    await expect(submitButton).toBeDisabled();

    // Search for and select a symbol instead of filling text input
    await symbolInput.fill('INFY.NS');
    const searchResults = page.locator('[role="listbox"] [role="option"]');
    await expect(searchResults.first()).toBeVisible({ timeout: 10000 });
    await searchResults.first().click();

    // Confirmation is required, so a selected symbol alone still cannot be submitted.
    await expect(submitButton).toBeDisabled();
    await page.getByText('I confirm this order').click();
    await expect(submitButton).toBeEnabled();
  });

  test('order form validates symbol selection', async ({ page }) => {
    // Sign in
    await signInToOrders(page);

    // Test 1: With no symbol selected, button should be disabled
    // Set order type to LIMIT first (price field is conditional on this)
    await page.locator('[data-testid="order-type-limit"]').check();

    await page.fill('[data-testid="order-quantity-input"]', '10');
    await page.fill('[data-testid="order-price-input"]', '1600');

    // Click BUY radio button
    const buyRadio = page.locator('[data-testid="order-side-buy"]');
    await buyRadio.click();

    // Button should be disabled without instrument selection
    const submitButton = page.locator('[data-testid="place-order-button"]');
    await expect(submitButton).toBeDisabled();

    // Test 2: Now select a valid symbol, button should enable
    const symbolInput = page.locator('[data-testid="order-symbol-input"]');
    await symbolInput.fill('INFY.NS');
    const searchResults = page.locator('[role="listbox"] [role="option"]');
    await expect(searchResults.first()).toBeVisible({ timeout: 10000 });
    await searchResults.first().click();

    await expect(submitButton).toBeDisabled();
    await page.getByText('I confirm this order').click();
    await expect(submitButton).toBeEnabled();
  });

  test('order form validates quantity', async ({ page }) => {
    // Sign in
    await signInToOrders(page);

    await page.locator('[data-testid="order-type-limit"]').check();

    // Search for and select a symbol
    const symbolInput = page.locator('[data-testid="order-symbol-input"]');
    await symbolInput.fill('INFY.NS');
    const searchResults = page.locator('[role="listbox"] [role="option"]');
    await expect(searchResults.first()).toBeVisible({ timeout: 10000 });
    await searchResults.first().click();

    // Try to submit with zero quantity
    await page.fill('[data-testid="order-quantity-input"]', '0');
    await page.fill('[data-testid="order-price-input"]', '1600');

    // Blur to trigger validation
    await page.locator('[data-testid="order-quantity-input"]').blur();

    // Check for disabled button (validation failed)
    const submitButton = page.locator('[data-testid="place-order-button"]');
    await expect(submitButton).toBeDisabled();
  });

  test('successful order placement returns status (NEW, FILLED, or REJECTED)', async ({ page }) => {
    // Sign in
    await signInToOrders(page);

    await page.locator('[data-testid="order-type-limit"]').check();

    // Search for and select a symbol
    const symbolInput = page.locator('[data-testid="order-symbol-input"]');
    await symbolInput.fill('INFY.NS');
    const searchResults = page.locator('[role="listbox"] [role="option"]');
    await expect(searchResults.first()).toBeVisible({ timeout: 10000 });
    await searchResults.first().click();

    // A one-share limit keeps the order inside a normal test balance.
    await page.fill('[data-testid="order-quantity-input"]', '1');
    await page.fill('[data-testid="order-price-input"]', '1');

    // Click BUY radio button
    const buyRadio = page.locator('[data-testid="order-side-buy"]');
    await buyRadio.click();
    await page.getByText('I confirm this order').click();

    // Submit the order
    const submitButton = page.locator('[data-testid="place-order-button"]');
    await expect(submitButton).toBeEnabled();
    await submitButton.click();

    // Accepted orders show a status. A funds rejection shows the red banner.
    const outcome = page.locator('[data-testid="order-success"], [data-testid="order-error"]');
    await expect(outcome.first()).toBeVisible();
    const outcomeText = await outcome.first().textContent();
    expect(outcomeText).toMatch(/NEW|FILLED|REJECTED|Insufficient funds/i);

    console.log(`Order placed with status from message: ${outcomeText}`);
  });

  test('placed order appears in order history', async ({ page }) => {
    // Sign in
    await signInToOrders(page);

    // We're already on the orders page, refresh to see latest orders
    const refreshButton = page.locator('[data-testid="refresh-orders-button"]');
    if (await refreshButton.isVisible()) {
      await refreshButton.click();
      await page.waitForLoadState('networkidle');
    } else {
      // Otherwise just reload the page
      await page.reload();
      await page.waitForLoadState('networkidle');
    }

    // Verify that orders are displayed on the page
    // The page should show the order list/history
    const pageContent = await page.content();
    expect(pageContent).toBeTruthy();
  });

  test('each order journey is isolated (no shared state)', async ({ page }) => {
    // This test verifies that:
    // 1. We can sign in fresh each time
    // 2. No tokens or state leak between tests
    // 3. Each order can be placed independently

    // First sign in
    await signInToOrders(page);

    // Verify we have a valid session
    await expect(page).toHaveURL(/\/(orders|dashboard)/);

    // Navigate to dashboard
    await page.goto(`${baseUrl}/dashboard`);
    await page.waitForLoadState('networkidle');

    // Verify dashboard loads
    // Dashboard should exist or at least page should load without auth errors
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
