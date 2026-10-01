import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { loginRedirectGuard } from './guards/login-redirect.guard';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { HomeComponent } from './pages/home/home.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { OrdersComponent } from './pages/orders/orders.component';
import { OrderHistoryComponent } from './pages/orders/order-history.component';
import { WatchlistsComponent } from './pages/watchlists/watchlists.component';
import { PositionsComponent } from './pages/positions/positions.component';

export const routes: Routes = [
	{
		path: '',
		component: HomeComponent
	},
	{
		path: 'login',
		component: LoginComponent,
		canActivate: [loginRedirectGuard]
	},
	{
		path: 'register',
		component: RegisterComponent
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
		path: 'watchlists',
		component: WatchlistsComponent,
		canActivate: [authGuard]
	},
	// Analytics removed — no route registered
	{
		path: '**',
		redirectTo: ''
	}
];
