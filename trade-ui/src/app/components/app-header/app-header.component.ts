import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthSessionService } from '../../services/auth-session.service';

@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css'
})
export class AppHeaderComponent {
  readonly authSession = inject(AuthSessionService);

  async onSignOut(): Promise<void> {
    await this.authSession.logout();
  }
}
