import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { loginRedirectGuard } from './guards/login-redirect.guard';
import { registerRedirectGuard } from './guards/register-redirect.guard';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { HomeComponent } from './pages/home/home.component';
import { ProfileComponent } from './pages/profile/profile.component';

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
		component: RegisterComponent,
		canActivate: [registerRedirectGuard]
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
		path: '**',
		redirectTo: ''
	}
];
