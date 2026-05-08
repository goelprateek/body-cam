import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatDividerModule } from '@angular/material/divider';
import { Router, RouterModule } from '@angular/router';
import { LayoutService } from '@shared/services/layout.service';
import { ThemeService } from '@app/theme.service';
import { OperatorApiService } from '@features/operations/operator-api.service';
import { LiveRoomService } from '@features/operations/live-room.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatToolbarModule,
    MatDividerModule,
    RouterModule
  ],
  template: `
    <mat-toolbar class="main-header">
      <button mat-icon-button (click)="layout.toggleSidebar()" class="toggle-btn" aria-label="Toggle navigation">
        <mat-icon>{{ layout.sidebarExpanded() ? 'menu_open' : 'menu' }}</mat-icon>
      </button>

      <span style="flex: 1 1 auto;"></span>

      <div class="header-actions">
        <button mat-icon-button type="button" (click)="theme.toggleTheme()" aria-label="Toggle theme">
          <mat-icon>{{ theme.theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
        </button>

        <button mat-icon-button [matMenuTriggerFor]="userMenu" class="user-panel-btn" aria-label="Open user menu">
          <div class="avatar-circle">
            {{ userInitials() }}
          </div>
        </button>
      </div>

      <mat-menu #userMenu="matMenu" xPosition="before" class="user-profile-menu">
        <div class="menu-identity-header" (click)="$event.stopPropagation()">
          <div class="menu-identity-bg"></div>
          <div class="avatar-circle avatar-circle--menu">
            {{ userInitials() }}
          </div>
          <div class="identity-text">
            <span class="user-name">{{ api.operatorLabel() }}</span>
            <span class="user-role">operator&#64;bodycam.local</span>
          </div>
          <span class="status-dot"></span>
        </div>

        <div class="menu-actions">
          <button mat-menu-item routerLink="/profile" class="custom-menu-item">
            <mat-icon>person_outline</mat-icon>
            <span>My Profile</span>
          </button>

          <button mat-menu-item routerLink="/preferences" class="custom-menu-item">
            <mat-icon>tune</mat-icon>
            <span>Preferences</span>
          </button>
        </div>

        <mat-divider></mat-divider>

        <div class="menu-actions">
          <button mat-menu-item (click)="logout()" class="custom-menu-item signout-item">
            <mat-icon>logout</mat-icon>
            <span>Sign out</span>
          </button>
        </div>
      </mat-menu>
    </mat-toolbar>
  `
})
export class HeaderComponent {
  readonly layout = inject(LayoutService);
  readonly theme = inject(ThemeService);
  readonly api = inject(OperatorApiService);
  readonly liveRoom = inject(LiveRoomService);
  private readonly router = inject(Router);

  readonly userInitials = computed(() => {
    const label = this.api.operatorLabel() || '';
    if (!label) return '?';
    const parts = label.trim().split(/\s+/);
    if (parts.length === 1) {
      return parts[0].substring(0, 2).toUpperCase();
    }
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

  logout(): void {
    this.liveRoom.disconnect();
    this.api.logout();
    void this.router.navigateByUrl('/login');
  }
}
