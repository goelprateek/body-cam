import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { LoginPageComponent } from './features/auth/login-page.component';
import { OperatorApiService } from '@features/operations/operator-api.service';
import { AppShellComponent } from './shared/components/app-shell/app-shell.component';
import { ProfilePageComponent } from './features/profile/profile-page.component';
import { PreferencesPageComponent } from './features/preferences/preferences-page.component';

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
    path: 'join/:inviteToken',
    loadComponent: () =>
      import('./features/public/session-browser-join-page.component').then(
        (c) => c.SessionBrowserJoinPageComponent
      )
  },
  {
    path: 'login',
    component: LoginPageComponent,
    canActivate: [redirectAuthenticatedUser]
  },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [requireAuth],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/operations/operations-page.component').then(
            (c) => c.OperationsPageComponent
          )
      },
      {
        path: 'profile',
        component: ProfilePageComponent
      },
      {
        path: 'recordings/:sessionId/transcript-review',
        loadComponent: () =>
          import('./features/recordings/transcript-review-page.component').then(
            (c) => c.TranscriptReviewPageComponent
          )
      },
      {
        path: 'recordings',
        loadComponent: () =>
          import('./features/recordings/recordings-page.component').then(
            (c) => c.RecordingsPageComponent
          )
      },
      {
        path: 'preferences',
        component: PreferencesPageComponent
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
