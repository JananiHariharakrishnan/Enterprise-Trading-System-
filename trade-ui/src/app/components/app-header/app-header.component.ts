import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthSessionService } from '../../services/auth-session.service';
import { ThemeService } from '../../services/theme.service';
import { Router } from '@angular/router';
import { UiEventsService } from '../../services/ui-events.service';

@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css'
})
export class AppHeaderComponent {
  readonly authSession = inject(AuthSessionService);
  readonly themeService = inject(ThemeService);
  readonly router = inject(Router);
  readonly uiEvents = inject(UiEventsService);

  menuOpen = false;

  async onSignOut(): Promise<void> {
    await this.authSession.logout();
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  toggleProfileMenu(): void {
    this.menuOpen = !this.menuOpen;
  }

  goToProfile(): void {
    void this.router.navigate(['/profile']);
    this.menuOpen = false;
  }

  emitRefresh(): void {
    this.uiEvents.emitRefresh();
    this.menuOpen = false;
  }
}
