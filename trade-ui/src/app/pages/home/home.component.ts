import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthSessionService } from '../../services/auth-session.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {
  readonly authSession = inject(AuthSessionService);
  private readonly router = inject(Router);

  async navigateToDashboard(): Promise<void> {
    await this.router.navigate(['/dashboard']);
  }

  async navigateToLogin(): Promise<void> {
    await this.router.navigate(['/login']);
  }

  async navigateToRegister(): Promise<void> {
    await this.router.navigate(['/register']);
  }
}
