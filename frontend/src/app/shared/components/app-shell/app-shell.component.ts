import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { SidebarNavComponent } from './sidebar-nav/sidebar-nav.component';
import { HeaderComponent } from './header/header.component';
import { LayoutService } from '@shared/services/layout.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatSidenavModule,
    SidebarNavComponent,
    HeaderComponent
  ],
  template: `
    <mat-sidenav-container class="app-container">
      <mat-sidenav mode="side" opened [class.expanded]="layout.sidebarExpanded()">
        <app-sidebar-nav></app-sidebar-nav>
      </mat-sidenav>

      <mat-sidenav-content [class.expanded-content]="layout.sidebarExpanded()">
        <app-header></app-header>
        <main class="content-wrapper">
          <router-outlet></router-outlet>
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    :host {
      display: block;
      height: 100vh;
      width: 100%;
    }
    
    app-sidebar-nav {
      display: flex;
      flex-direction: column;
      height: 100%;
      width: 100%;
    }
  `]
})
export class AppShellComponent {
  readonly layout = inject(LayoutService);
}
