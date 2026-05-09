import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { LayoutService } from '@shared/services/layout.service';

@Component({
  selector: 'app-sidebar-nav',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule
  ],
  template: `
    <div class="sidenav-wrapper">
      <header class="sidenav-header">
        <div class="brand-block" [class.compact]="!layout.sidebarExpanded()">
          <img class="brand-icon" src="assets/logo.png" alt="Body Cam Logo" />
          @if (layout.sidebarExpanded()) {
            <div class="brand-copy">
              <span class="brand-name">Body Cam</span>
              <span class="brand-caption">Operator Console</span>
            </div>
          }
        </div>
      </header>

      <nav class="custom-nav" aria-label="Primary navigation">
        <a class="nav-item" routerLink="/" routerLinkActive="active-nav" [routerLinkActiveOptions]="{ exact: true }" matTooltip="Operations" matTooltipPosition="right" [matTooltipDisabled]="layout.sidebarExpanded()">
          <mat-icon class="nav-icon">dashboard</mat-icon>
          @if (layout.sidebarExpanded()) {
            <span class="nav-label">Operations</span>
          }
        </a>
        <a class="nav-item" routerLink="/recordings" routerLinkActive="active-nav" matTooltip="Recordings" matTooltipPosition="right" [matTooltipDisabled]="layout.sidebarExpanded()">
          <mat-icon class="nav-icon">video_library</mat-icon>
          @if (layout.sidebarExpanded()) {
            <span class="nav-label">Recordings</span>
          }
        </a>

      </nav>

      <div class="sidenav-spacer"></div>

      <div class="sidenav-footer">
        @if (layout.sidebarExpanded()) {
          <div class="footer-content">
            <span class="status-indicator">SYSTEM ONLINE</span>
            <span class="version-tag">Secured Workspace</span>
          </div>
        }
      </div>
    </div>
  `
})
export class SidebarNavComponent {
  readonly layout = inject(LayoutService);
}
