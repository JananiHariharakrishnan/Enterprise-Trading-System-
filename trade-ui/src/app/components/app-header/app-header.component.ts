import { Component, inject } from '@angular/core';
import { AuthSessionService } from '../../services/auth-session.service';

@Component({
  selector: 'app-app-header',
  imports: [],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css'
})
export class AppHeaderComponent {
  private readonly authSession = inject(AuthSessionService);

  async onSignOut(): Promise<void> {
    await this.authSession.logout();
  }
}
