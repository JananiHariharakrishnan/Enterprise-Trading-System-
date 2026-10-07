import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { loginRedirectGuard } from './guards/login-redirect.guard';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { AuthLayoutComponent } from './components/auth-layout/auth-layout.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { OrdersComponent } from './pages/orders/orders.component';
import { OrderHistoryComponent } from './pages/orders/order-history.component';
import { WatchlistsComponent } from './pages/watchlists/watchlists.component';
import { PositionsComponent } from './pages/positions/positions.component';
import { PnlComponent } from './pages/pnl/pnl.component';

export const routes: Routes = [
  // '/' now goes straight to sign in (HomeComponent removed)
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'login'
  },
  {
    // Empty path keeps the URLs as /login and /register, so every existing
    // navigate(['/login']) in guards and services keeps working.
    path: '',
    component: AuthLayoutComponent,
    // Preserve existing guest redirect guard at parent so both children inherit it
    canActivate: [loginRedirectGuard],
    children: [
      { path: 'login', component: LoginComponent },
      { path: 'register', component: RegisterComponent }
    ]
  },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    component: ProfileComponent,
    canActivate: [authGuard]
  },
  {
    path: 'orders',
    component: OrdersComponent,
    canActivate: [authGuard]
  },
  {
    path: 'orders/history',
    component: OrderHistoryComponent,
    canActivate: [authGuard]
  },
  {
    path: 'positions',
    component: PositionsComponent,
    canActivate: [authGuard]
  },
  {
    path: 'pnl',
    component: PnlComponent,
    canActivate: [authGuard]
  },
  {
    path: 'watchlists',
    component: WatchlistsComponent,
    canActivate: [authGuard]
  },
  {
    path: 'alerts',
    loadComponent: () => import('./pages/alerts/alerts.component').then(m => m.AlertsComponent),
    canActivate: [authGuard]
  },
  // Analytics removed — no route registered
  {
    // Unknown URLs go to '/', which redirects to /login
    path: '**',
    redirectTo: ''
  }
];
