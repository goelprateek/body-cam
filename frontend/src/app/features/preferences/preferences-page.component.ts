import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-preferences-page',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <section class="page">
      <mat-card class="panel section-panel settings-card" appearance="outlined">
        <div class="section-head">
          <h2>Preferences</h2>
        </div>
        <div class="empty-state">
          System and notification preferences will be available here.
        </div>
      </mat-card>
    </section>
  `
})
export class PreferencesPageComponent {}
