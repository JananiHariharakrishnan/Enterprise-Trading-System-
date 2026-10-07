import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WalletComponent } from './wallet.component';
import { WalletService, WalletAccount, WalletDepositResponse } from '../../services/wallet.service';
import { PasswordHashService } from '../../services/password-hash.service';

describe('WalletComponent', () => {
  let component: WalletComponent;
  let fixture: ComponentFixture<WalletComponent>;
  let walletServiceSpy: {
    getWalletDetails: ReturnType<typeof vi.fn>;
    depositFunds: ReturnType<typeof vi.fn>;
  };
  let passwordHashSpy: {
    encodePassword: ReturnType<typeof vi.fn>;
  };

  const mockAccount: WalletAccount = {
    accountId: 1001,
    accountReference: 'ETP-1001',
    cashBalance: 12500,
    buyingPower: 12500,
    status: 'ACTIVE'
  };

  const mockDepositResp: WalletDepositResponse = {
    accountId: 1001,
    accountReference: 'ETP-1001',
    previousBalance: 12500,
    depositedAmount: 500,
    newCashBalance: 13000,
    newBuyingPower: 13000,
    message: 'Funds deposited successfully.'
  };

  beforeEach(async () => {
    walletServiceSpy = {
      getWalletDetails: vi.fn().mockResolvedValue(mockAccount),
      depositFunds: vi.fn().mockResolvedValue(mockDepositResp)
    };
    passwordHashSpy = {
      encodePassword: vi.fn().mockImplementation((pwd: string) => `b64_${pwd}`)
    };

    await TestBed.configureTestingModule({
      imports: [WalletComponent],
      providers: [
        { provide: WalletService, useValue: walletServiceSpy },
        { provide: PasswordHashService, useValue: passwordHashSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WalletComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads wallet details on initialization', () => {
    expect(walletServiceSpy.getWalletDetails).toHaveBeenCalled();
    expect(component.account()).toEqual(mockAccount);
    expect(component.depositAmount).toBe(500);
  });

  it('updates depositAmount when quick chip is selected', () => {
    component.setQuickAmount(1000);
    expect(component.depositAmount).toBe(1000);
  });

  it('opens password confirmation modal when amount is valid', () => {
    component.depositAmount = 750;
    component.openConfirmation();
    expect(component.isPasswordModalOpen()).toBe(true);
    expect(component.confirmPassword).toBe('');
  });

  it('shows error if opening confirmation with zero or negative amount', () => {
    component.depositAmount = 0;
    component.openConfirmation();
    expect(component.isPasswordModalOpen()).toBe(false);
    expect(component.errorMessage()).toContain('greater than $0');
  });

  it('submits deposit with encoded password and refreshes balance', async () => {
    component.depositAmount = 500;
    component.openConfirmation();
    component.confirmPassword = 'mySecurePassword';

    await component.confirmAndDeposit();

    expect(passwordHashSpy.encodePassword).toHaveBeenCalledWith('mySecurePassword');
    expect(walletServiceSpy.depositFunds).toHaveBeenCalledWith(500, 'b64_mySecurePassword');
    expect(component.isPasswordModalOpen()).toBe(false);
    expect(component.successMessage()).toContain('$500');
    expect(walletServiceSpy.getWalletDetails).toHaveBeenCalledTimes(2); // init + after deposit
  });

  it('displays modal error message when deposit fails', async () => {
    walletServiceSpy.depositFunds.mockRejectedValue({ error: { message: 'Invalid password' } });

    component.depositAmount = 500;
    component.openConfirmation();
    component.confirmPassword = 'badPassword';

    await component.confirmAndDeposit();

    expect(component.modalErrorMessage()).toBe('Invalid password');
    expect(component.isPasswordModalOpen()).toBe(true);
  });
});
