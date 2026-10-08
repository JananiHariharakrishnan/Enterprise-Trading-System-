import { test, expect } from '@playwright/test';

test.describe.serial('Watchlist Journey', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://localhost:4200';
  const username = process.env.E2E_USERNAME || 'e2e.tester';
  const password = process.env.E2E_PASSWORD || 'correct horse battery staple';

  // Fixed watchlist name for all tests
  const watchlistName = 'watchlist-test';

  /**
   * Helper: Sign in and navigate to watchlists page
   * Returns the authenticated page ready for watchlist operations
   */
  async function signInToWatchlists(page: typeof test['_runningPage']) {
    // Navigate to login
    await page.goto(`${baseUrl}/login`);

    // Sign in with valid credentials
    await page.fill('[data-testid="username-input"]', username);
    await page.fill('[data-testid="password-input"]', password);
    await page.click('[data-testid="sign-in-button"]');

    // Wait for dashboard to load
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });

    // Navigate to watchlists page
    await page.goto(`${baseUrl}/watchlists`);
    await page.waitForLoadState('networkidle');
  }

  test('can create a watchlist', async ({ page }) => {
    // Sign in
    await signInToWatchlists(page);

    // Find the create watchlist form
    const nameInput = page.getByLabel('New watchlist name');
    const createButton = page.getByRole('button', { name: 'Create Watchlist' });

    // Fill in the watchlist name
    await nameInput.fill(watchlistName);

    // Click create button
    await createButton.click();

    // Wait for the API response
    await page.waitForLoadState('networkidle');

    // Verify success message or new watchlist appears
    // Look for the watchlist button card (first occurrence, not the heading)
    const watchlistCard = page.locator('button').filter({ hasText: watchlistName }).first();
    await expect(watchlistCard).toBeVisible({ timeout: 5000 });
  });

  test('can add instrument to watchlist', async ({ page }) => {
    // Sign in
    await signInToWatchlists(page);

    // Click on the existing watchlist to open it (use button selector to avoid heading)
    const watchlistCard = page.locator('button').filter({ hasText: watchlistName }).first();
    await expect(watchlistCard).toBeVisible();
    await watchlistCard.click();
    await page.waitForLoadState('networkidle');

    // Find the search input and fill it
    const symbolInput = page.locator('input[placeholder*="Add symbol"]').first();
    await expect(symbolInput).toBeVisible();
    await symbolInput.fill('INFY.NS');

    // Wait for search results to appear and click the first matching result
    await page.waitForTimeout(500);
    const searchResults = page.locator('[role="listbox"] [role="option"]');
    await expect(searchResults.first()).toBeVisible();
    await searchResults.first().click();

    // Wait for the API call
    await page.waitForLoadState('networkidle');

    // Verify the instrument appears in the watchlist table row
    // Look for the strong tag in the instrument row that contains the symbol
    const instrumentRow = page.locator('strong').filter({ hasText: 'INFY.NS' }).first();
    await expect(instrumentRow).toBeVisible({ timeout: 5000 });
  });

  test('can remove instrument from watchlist', async ({ page }) => {
    // Sign in
    await signInToWatchlists(page);

    // Open the existing watchlist
    const watchlistCard = page.locator('button').filter({ hasText: watchlistName }).first();
    await watchlistCard.click();
    await page.waitForLoadState('networkidle');

    // Find the instrument row containing INFY.NS
    const instrumentSymbol = page.locator('strong').filter({ hasText: 'INFY.NS' }).first();
    await expect(instrumentSymbol).toBeVisible();

    // Try to find delete button - look for buttons on the page with delete/remove text or icon
    // First, try finding buttons with aria-label or title containing delete
    const deleteByAriaLabel = page.locator('button[aria-label*="delete" i], button[aria-label*="remove" i]').first();
    const deleteByTitle = page.locator('button[title*="delete" i], button[title*="remove" i]').first();
    const deleteByDataTestid = page.locator('button[data-testid*="delete" i]').first();

    // Try the most specific selectors first
    let deleteButton = deleteByDataTestid;
    if (await deleteButton.count() === 0) {
      deleteButton = deleteByAriaLabel;
    }
    if (await deleteButton.count() === 0) {
      deleteButton = deleteByTitle;
    }
    if (await deleteButton.count() === 0) {
      // As fallback, look for any button with x, ×, or delete text on the page
      deleteButton = page.locator('button').filter({ hasText: /^x$|^×$|delete|remove/i }).last();
    }

    // Click the delete button
    await expect(deleteButton).toBeVisible({ timeout: 5000 });
    await deleteButton.click();

    // Wait for deletion
    await page.waitForLoadState('networkidle');

    // Verify instrument is removed
    await expect(instrumentSymbol).not.toBeVisible({ timeout: 5000 });
  });

  test('can delete watchlist', async ({ page }) => {
    // Sign in
    await signInToWatchlists(page);

    // Open the existing watchlist
    const watchlistCard = page.locator('button').filter({ hasText: watchlistName }).first();
    await expect(watchlistCard).toBeVisible();
    await watchlistCard.click();
    await page.waitForLoadState('networkidle');

    // Set up handler for alert/confirm dialog
    page.once('dialog', dialog => {
      console.log(`Dialog type: ${dialog.type()}, message: ${dialog.message()}`);
      dialog.accept(); // Accept the confirmation
    });

    // Find and click delete button for the watchlist
    // Try multiple selectors to find the delete button
    const deleteByDataTestid = page.locator('button[data-testid*="delete" i]').first();
    const deleteByAriaLabel = page.locator('button[aria-label*="delete" i], button[aria-label*="remove" i]').first();
    const deleteByText = page.locator('button').filter({ hasText: /delete|remove/i }).first();

    let deleteWatchlistButton = deleteByDataTestid;
    if (await deleteWatchlistButton.count() === 0) {
      deleteWatchlistButton = deleteByAriaLabel;
    }
    if (await deleteWatchlistButton.count() === 0) {
      deleteWatchlistButton = deleteByText;
    }

    await expect(deleteWatchlistButton).toBeVisible();
    await deleteWatchlistButton.click();

    // Wait for the alert to be processed
    await page.waitForTimeout(500);

    // Wait for deletion to complete
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Navigate back to watchlists list page
    await page.goto(`${baseUrl}/watchlists`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);

    // Verify watchlist is completely removed from the list
    // Use a more specific check - look for exact button text match
    const watchlistButtons = await page.locator('button').all();
    let watchlistFound = false;

    for (const btn of watchlistButtons) {
      const text = await btn.textContent();
      if (text === watchlistName) {
        watchlistFound = true;
        break;
      }
    }

    expect(watchlistFound).toBe(false);
  });
});
