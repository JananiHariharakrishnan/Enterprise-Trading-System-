import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { loginRedirectGuard } from './guards/login-redirect.guard';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { LoginComponent } from './pages/login/login.component';

export const routes: Routes = [
	{
		path: 'login',
		component: LoginComponent,
		canActivate: [loginRedirectGuard]
	},
	{
		path: 'dashboard',
		component: DashboardComponent,
		canActivate: [authGuard]
	},
	{
		path: '',
		pathMatch: 'full',
		redirectTo: 'dashboard'
	},
	{
		path: '**',
		redirectTo: 'dashboard'
	}
];
