import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AppHeaderComponent } from './components/app-header/app-header.component';
import { AuthSessionService } from './services/auth-session.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, AppHeaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  constructor(readonly authSession: AuthSessionService) {}
}
