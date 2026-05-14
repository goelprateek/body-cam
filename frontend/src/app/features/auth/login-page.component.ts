import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { OperatorApiService } from '@features/operations/operator-api.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatIconModule
  ],
  styleUrl: './login-page.component.scss',
  template: `
    <div class="login-container">
      
      <!-- Brand Panel (Left) -->
      <div class="brand-panel">
        <div class="brand-overlay"></div>
        <div class="brand-header">
          <img class="karebo-logo" src="assets/brand/brand-icon.png" alt="Karebo Logo" />
        </div>
        <div class="brand-content">
          <div class="brand-badge">Remote Assistance</div>
          <h1 class="brand-title">Karebo Body Cam Platform</h1>
          
          <div class="value-cards">
            <div class="value-card">
              <mat-icon>security</mat-icon>
              <div class="card-text">
                <h3>Safety</h3>
                <p>Ensure the physical well-being of your field workers with real-time video monitoring and immediate emergency response.</p>
              </div>
            </div>
            <div class="value-card">
              <mat-icon>fact_check</mat-icon>
              <div class="card-text">
                <h3>Accountability</h3>
                <p>Maintain an indisputable, tamper-proof record of all field interactions for compliance and review.</p>
              </div>
            </div>
            <div class="value-card">
              <mat-icon>handshake</mat-icon>
              <div class="card-text">
                <h3>Trust</h3>
                <p>Build confidence with transparent operations and clear, evidence-backed reporting.</p>
              </div>
            </div>
          </div>
        </div>
        <div class="brand-footer">
          &copy; {{ currentYear }} Body Cam. All rights reserved.
        </div>
      </div>

      <!-- Form Panel (Right) -->
      <div class="form-panel">
        @if (isBusy()) {
          <div class="progress-bar-container">
            <mat-progress-bar class="login-progress" mode="indeterminate"></mat-progress-bar>
          </div>
        }

        <div class="form-wrapper">
          <div class="form-header">
            <img class="form-logo" src="assets/logo.png" alt="Body Cam Logo" />
            <h2>Welcome back</h2>
            <p>Please enter your credentials to access the portal.</p>
          </div>

          <form class="login-form" (ngSubmit)="login()">
            
            @if (errorMessage()) {
              <div class="error-box">
                <mat-icon>error_outline</mat-icon>
                <span>{{ errorMessage() }}</span>
              </div>
            }

            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Username</mat-label>
              <input
                matInput
                [ngModel]="username()"
                (ngModelChange)="username.set($event)"
                name="username"
                autocomplete="username"
                [disabled]="isBusy()"
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
                [disabled]="isBusy()"
              />
            </mat-form-field>

            <button class="submit-btn" mat-flat-button type="submit" [disabled]="isBusy() || !username() || !password()">
              {{ isBusy() ? 'Authenticating...' : 'Sign In' }}
            </button>
          </form>
        </div>
      </div>

    </div>
  `
})
export class LoginPageComponent {
  private readonly api = inject(OperatorApiService);
  private readonly router = inject(Router);

  readonly username = signal('');
  readonly password = signal('');
  readonly isBusy = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly currentYear = new Date().getFullYear();

  async login(): Promise<void> {
    if (!this.username().trim() || !this.password().trim()) {
      return;
    }

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
