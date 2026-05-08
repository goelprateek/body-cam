import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { OperatorApiService } from '../dashboard/operator-api.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule
  ],
  template: `
    <section class="auth-shell">
      <div class="auth-layout">
        <section class="auth-hero" aria-label="Body Cam overview">
          <img class="auth-logo" src="assets/logo.png" alt="Body Cam logo" />
          <p class="auth-kicker">Remote Assistance Platform</p>
          <h1>Body Cam</h1>
          <p class="auth-copy">
            Secure operator access for live field video, audio, and replay.
          </p>
        </section>

        <mat-card class="auth-card" appearance="outlined">
          <div class="auth-head">
            <img class="auth-card-logo" src="assets/logo.png" alt="Body Cam logo" />
            <h2>Sign in</h2>
            <p>Use your operator account.</p>
          </div>

          @if (isBusy()) {
            <mat-progress-bar mode="indeterminate"></mat-progress-bar>
          }

          <form class="stack" (ngSubmit)="login()">
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Username</mat-label>
              <input
                matInput
                [ngModel]="username()"
                (ngModelChange)="username.set($event)"
                name="username"
                autocomplete="username"
              />
            </mat-form-field>

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Password</mat-label>
              <input
                matInput
                [ngModel]="password()"
                (ngModelChange)="password.set($event)"
                name="password"
                type="password"
                autocomplete="current-password"
              />
            </mat-form-field>

            @if (errorMessage()) {
              <div class="notice notice-error">{{ errorMessage() }}</div>
            }

            <button class="auth-submit" mat-flat-button type="submit" [disabled]="isBusy()">
              {{ isBusy() ? 'Signing In...' : 'Sign In' }}
            </button>
          </form>
        </mat-card>
      </div>
    </section>
  `
})
export class LoginPageComponent {
  private readonly api = inject(OperatorApiService);
  private readonly router = inject(Router);

  readonly username = signal('');
  readonly password = signal('');
  readonly isBusy = signal(false);
  readonly errorMessage = signal<string | null>(null);

  async login(): Promise<void> {
    this.errorMessage.set(null);
    this.isBusy.set(true);

    try {
      await this.api.login(this.username().trim(), this.password().trim());
      this.password.set('');
      await this.router.navigateByUrl('/');
    } catch (error) {
      this.errorMessage.set(this.api.explainError(error));
    } finally {
      this.isBusy.set(false);
    }
  }
}
