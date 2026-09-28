import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthSessionService } from '../../services/auth-session.service';
import { AppHeaderComponent } from './app-header.component';

class AuthSessionServiceStub {
  logout = vi.fn().mockResolvedValue(undefined);
}

describe('AppHeaderComponent', () => {
  let fixture: ComponentFixture<AppHeaderComponent>;
  let authSession: AuthSessionServiceStub;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppHeaderComponent],
      providers: [{ provide: AuthSessionService, useClass: AuthSessionServiceStub }]
    }).compileComponents();

    fixture = TestBed.createComponent(AppHeaderComponent);
    authSession = TestBed.inject(AuthSessionService) as unknown as AuthSessionServiceStub;
    fixture.detectChanges();
  });

  it('signing out clears the session via auth service', async () => {
    const button = fixture.nativeElement.querySelector('[data-testid="sign-out-button"]') as HTMLButtonElement;

    button.click();
    await fixture.whenStable();

    expect(authSession.logout).toHaveBeenCalledTimes(1);
  });
});