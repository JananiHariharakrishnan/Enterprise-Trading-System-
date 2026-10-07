import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WalletService, WalletAccount, WalletDepositResponse } from '../../services/wallet.service';
import { PasswordHashService } from '../../services/password-hash.service';

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

  readonly account = signal<WalletAccount | null>(null);
  readonly isLoading = signal<boolean>(true);
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly lastDeposit = signal<WalletDepositResponse | null>(null);

  // Deposit Form
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
    } catch (err: any) {
      this.errorMessage.set(err?.error?.message || err?.message || 'Failed to load wallet account details');
    } finally {
      this.isLoading.set(false);
    }
  }

  setQuickAmount(amount: number): void {
    this.depositAmount = amount;
  }

  openConfirmation(): void {
    if (!this.depositAmount || this.depositAmount <= 0) {
      this.errorMessage.set('Please enter a valid deposit amount greater than $0.');
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
      const resp = await this.walletService.depositFunds(this.depositAmount, encodedPassword);

      this.lastDeposit.set(resp);
      this.successMessage.set(`Successfully added $${resp.depositedAmount.toLocaleString()} to your account!`);
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
}
