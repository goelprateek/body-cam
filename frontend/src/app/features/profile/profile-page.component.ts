import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  styleUrl: './profile-page.component.scss',
  template: `
    <section class="page">
      <mat-card class="panel section-panel settings-card" appearance="outlined">
        <div class="section-head">
          <h2>Operator Profile</h2>
          <span class="status-pill status-live">ACTIVE</span>
        </div>
        <div class="empty-state">
          Profile details and operator history will be available here.
        </div>
      </mat-card>
    </section>
  `
})
export class ProfilePageComponent {}
