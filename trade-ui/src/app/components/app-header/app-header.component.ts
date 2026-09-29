import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthSessionService } from '../../services/auth-session.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css'
})
export class AppHeaderComponent {
  readonly authSession = inject(AuthSessionService);
  readonly themeService = inject(ThemeService);

  async onSignOut(): Promise<void> {
    await this.authSession.logout();
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }
}
