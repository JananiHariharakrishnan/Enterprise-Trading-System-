import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WalletService, WalletAccount, WalletDepositResponse } from '../../services/wallet.service';
import { PasswordHashService } from '../../services/password-hash.service';
import { CustomerPreferencesService, type BaseCurrency } from '../../services/customer-preferences.service';
import { USD_INR_RATE, convertAmount, currencySymbol } from '../../utils/currency.util';

@Component({
  selector: 'app-wallet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './wallet.component.html',
  styleUrls: ['./wallet.component.css']
})
export class WalletComponent implements OnInit {
  private readonly walletService = inject(WalletService);
  private readonly passwordHashService = inject(PasswordHashService);
  private readonly preferencesApi = inject(CustomerPreferencesService);

  readonly account = signal<WalletAccount | null>(null);
  readonly isLoading = signal<boolean>(true);
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly lastDeposit = signal<WalletDepositResponse | null>(null);

  readonly displayCurrency = signal<BaseCurrency>('INR');

  // Deposit Form. The number is in the user's display currency.
  depositAmount: number | null = 500;
  readonly quickAmounts = [100, 500, 1000, 2500, 5000];

  // Password confirmation modal
  readonly isPasswordModalOpen = signal<boolean>(false);
  confirmPassword = '';
  modalErrorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadWallet();
  }

  async loadWallet(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      const data = await this.walletService.getWalletDetails();
      this.account.set(data);
      await this.loadDisplayCurrency(data.accountId);
    } catch (err: any) {
      this.errorMessage.set(err?.error?.message || err?.message || 'Failed to load wallet account details');
    } finally {
      this.isLoading.set(false);
    }
  }

  moneySymbol(): string {
    return currencySymbol(this.displayCurrency());
  }

  shownBalance(amount: number | null | undefined): number {
    return convertAmount(Number(amount ?? 0), 'USD', this.displayCurrency());
  }

  shownDeposit(): number {
    return this.depositAmount ?? 0;
  }

  shownNewBalance(): number {
    return this.shownBalance(this.account()?.cashBalance) + this.shownDeposit();
  }

  setQuickAmount(amount: number): void {
    if (!this.quickAmounts.includes(amount)) {
      return;
    }
    this.depositAmount = amount;
  }

  openConfirmation(): void {
    if (!this.depositAmount || !this.quickAmounts.includes(this.depositAmount)) {
      this.errorMessage.set('Choose 100, 500, 1,000, 2,500, or 5,000.');
      return;
    }
    this.errorMessage.set(null);
    this.modalErrorMessage.set(null);
    this.confirmPassword = '';
    this.isPasswordModalOpen.set(true);
  }

  closeModal(): void {
    this.isPasswordModalOpen.set(false);
    this.confirmPassword = '';
    this.modalErrorMessage.set(null);
  }

  async confirmAndDeposit(): Promise<void> {
    if (!this.confirmPassword) {
      this.modalErrorMessage.set('Password confirmation is required.');
      return;
    }

    if (!this.depositAmount || this.depositAmount <= 0) {
      this.modalErrorMessage.set('Deposit amount is invalid.');
      return;
    }

    this.isSubmitting.set(true);
    this.modalErrorMessage.set(null);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    try {
      // Encode password consistently with auth service expectations
      const encodedPassword = this.passwordHashService.encodePassword(this.confirmPassword);
      const selectedAmount = this.depositAmount;
      const resp = await this.walletService.depositFunds(this.toLedgerAmount(selectedAmount), encodedPassword);

      this.lastDeposit.set(resp);
      this.successMessage.set(`Successfully added ${this.moneySymbol()}${selectedAmount.toLocaleString()} to your account!`);
      this.closeModal();

      // Refresh wallet balance
      await this.loadWallet();
    } catch (err: any) {
      const msg = err?.error?.message || err?.message || 'Deposit failed. Please verify your password.';
      this.modalErrorMessage.set(msg);
    } finally {
      this.isSubmitting.set(false);
    }
  }

  private async loadDisplayCurrency(accountId: number): Promise<void> {
    try {
      const preferences = await this.preferencesApi.getPreferences(accountId);
      this.displayCurrency.set(preferences?.display?.baseCurrency === 'USD' ? 'USD' : 'INR');
    } catch {
      this.displayCurrency.set('INR');
    }
  }

  /** Cash is stored in USD. Rupee deposits are converted before they are saved. */
  private toLedgerAmount(displayAmount: number): number {
    const ledger = convertAmount(displayAmount, this.displayCurrency(), 'USD', USD_INR_RATE);
    return Math.round(ledger * 100) / 100;
  }
}
