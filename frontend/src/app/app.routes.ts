import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { LoginPageComponent } from './features/auth/login-page.component';
import { DashboardPageComponent } from './features/dashboard/dashboard-page.component';
import { OperatorApiService } from './features/dashboard/operator-api.service';

const requireAuth = async () => {
  const api = inject(OperatorApiService);
  const router = inject(Router);

  if (api.currentUser()) {
    return true;
  }

  if (api.accessToken()) {
    const restored = await api.restoreSession();
    if (restored) {
      return true;
    }
  }

  return router.createUrlTree(['/login']);
};

const redirectAuthenticatedUser = async () => {
  const api = inject(OperatorApiService);
  const router = inject(Router);

  if (api.currentUser()) {
    return router.createUrlTree(['/']);
  }

  if (api.accessToken()) {
    const restored = await api.restoreSession();
    if (restored) {
      return router.createUrlTree(['/']);
    }
  }

  return true;
};

export const routes: Routes = [
  {
    path: '',
    component: DashboardPageComponent,
    canActivate: [requireAuth]
  },
  {
    path: 'login',
    component: LoginPageComponent,
    canActivate: [redirectAuthenticatedUser]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
