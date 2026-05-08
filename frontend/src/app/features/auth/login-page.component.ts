import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { OperatorApiService } from '../dashboard/operator-api.service';

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
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      font-family: "Manrope", "Segoe UI", sans-serif;
    }

    .login-container {
      display: flex;
      min-height: 100vh;
      width: 100%;
    }

    /* Left Side: Brand Panel */
    .brand-panel {
      flex: 1;
      display: none;
      background: linear-gradient(135deg, #1A1F23 0%, #275046 50%, #61B748 100%);
      position: relative;
      overflow: hidden;
      color: #FFFFFF;
      padding: 4rem;
      flex-direction: column;
      justify-content: space-between;
    }

    @media (min-width: 900px) {
      .brand-panel {
        display: flex;
      }
    }

    .brand-overlay {
      position: absolute;
      inset: 0;
      background: radial-gradient(circle at top right, rgba(255,255,255,0.08), transparent 50%);
      pointer-events: none;
    }

    .brand-content {
      position: relative;
      z-index: 1;
      margin-top: auto;
      margin-bottom: auto;
    }

    .brand-header {
      position: relative;
      z-index: 1;
    }

    .karebo-logo {
      height: 64px;
      width: auto;
      max-width: 100%;
      filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
    }



    .brand-badge {
      display: inline-block;
      padding: 0.5rem 1rem;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 20px;
      font-size: 0.85rem;
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      margin-bottom: 2rem;
      backdrop-filter: blur(10px);
    }

    .brand-title {
      font-size: clamp(3rem, 5vw, 4rem);
      font-weight: 800;
      line-height: 1.1;
      margin: 0 0 1.5rem 0;
      letter-spacing: -0.02em;
    }

    .value-cards {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 1.25rem;
      margin-top: 4.5rem;
      width: 100%;
    }

    .value-card {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      align-items: flex-start;
      backdrop-filter: blur(8px);
      transition: background 0.2s ease, transform 0.2s ease;
    }

    .value-card:hover {
      background: rgba(255, 255, 255, 0.1);
      transform: translateX(4px);
    }

    .value-card mat-icon {
      color: #61B748;
      font-size: 2rem;
      width: 2rem;
      height: 2rem;
      flex-shrink: 0;
      filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
    }

    .card-text h3 {
      font-size: 1.15rem;
      font-weight: 700;
      color: #FFFFFF;
      margin: 0 0 0.35rem 0;
      letter-spacing: 0.01em;
    }

    .card-text p {
      font-size: 0.95rem;
      color: rgba(255, 255, 255, 0.8);
      margin: 0;
      line-height: 1.5;
    }

    .brand-footer {
      position: relative;
      z-index: 1;
      font-size: 0.9rem;
      color: rgba(255, 255, 255, 0.6);
    }

    /* Right Side: Form Panel */
    .form-panel {
      flex: 1;
      max-width: 100%;
      background: var(--surface-strong, #FFFFFF);
      color: var(--ink);
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      padding: 2rem;
      position: relative;
    }

    @media (min-width: 900px) {
      .form-panel {
        max-width: 540px;
      }
    }

    .form-wrapper {
      width: 100%;
      max-width: 400px;
      margin-bottom: 12vh;
      animation: fadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes fadeUp {
      from { opacity: 0; transform: translateY(20px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .form-header {
      margin-bottom: 2.5rem;
      text-align: center;
    }

    .form-logo {
      width: 100%;
      max-width: 140px;
      margin: 0 auto 1.5rem auto;
      display: block;
    }

    .form-header h2 {
      font-size: 2rem;
      font-weight: 700;
      color: #1A1F23;
      margin: 0 0 0.5rem 0;
      letter-spacing: -0.01em;
    }

    .form-header p {
      font-size: 1rem;
      color: #9AA3AD;
      margin: 0;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    /* Customizing Material Inputs */
    ::ng-deep .login-form .mdc-text-field--outlined {
      --mdc-outlined-text-field-container-shape: 12px;
      --mdc-outlined-text-field-focus-outline-color: #61B748;
      --mdc-outlined-text-field-hover-outline-color: #9AA3AD;
      --mdc-outlined-text-field-outline-color: rgba(26, 31, 35, 0.12);
    }

    ::ng-deep .login-form .mat-mdc-form-field-focus-overlay {
      background-color: transparent;
    }

    .submit-btn {
      margin-top: 1rem;
      height: 52px;
      border-radius: 12px;
      font-size: 1.05rem;
      font-weight: 700;
      letter-spacing: 0.02em;
      background-color: #61B748 !important;
      color: #FFFFFF !important;
      transition: all 0.2s ease;
      box-shadow: 0 4px 14px rgba(97, 183, 72, 0.25);
    }

    .submit-btn:hover:not([disabled]) {
      background-color: #4E9639 !important;
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(78, 150, 57, 0.3);
    }

    .submit-btn:disabled {
      background-color: #C8EABD !important;
      color: rgba(255, 255, 255, 0.8) !important;
      box-shadow: none;
    }

    .error-box {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 1rem;
      border-radius: 10px;
      background-color: rgba(235, 67, 53, 0.08); /* Alert #EB4335 */
      border: 1px solid rgba(235, 67, 53, 0.2);
      color: #EB4335;
      font-size: 0.9rem;
      font-weight: 500;
      animation: shake 0.4s ease-in-out;
    }

    .error-box mat-icon {
      font-size: 1.25rem;
      width: 1.25rem;
      height: 1.25rem;
    }

    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      20%, 60% { transform: translateX(-4px); }
      40%, 80% { transform: translateX(4px); }
    }

    .progress-bar-container {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 4px;
    }

    ::ng-deep .login-progress .mdc-linear-progress__bar-inner {
      border-color: #61B748 !important;
    }
  `],
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
