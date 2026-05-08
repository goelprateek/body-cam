import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { OperatorApiService } from '@features/operations/operator-api.service';
import { RecordingResponse } from '@features/operations/operator.models';

@Component({
  selector: 'app-recordings-page',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  template: `
    <section class="page workspace-grid">
      <mat-card class="panel section-panel" appearance="outlined">
        <div class="section-head">
          <h2>Archive</h2>
          <span class="subtle-text">{{ recordings().length }} Recordings</span>
        </div>

        @if (isLoading()) {
          <mat-progress-bar mode="indeterminate"></mat-progress-bar>
        }

        @if (pageError()) {
          <div class="notice notice-error">{{ pageError() }}</div>
        }

        <div class="recording-list">
          @for (recording of recordings(); track recording.id) {
            <mat-card
              class="recording-card"
              appearance="outlined"
              [class.recording-card-selected]="recording.id === selectedRecordingId()"
            >
              <button class="recording-card-button" type="button" (click)="selectRecording(recording.id)">
                <div class="recording-card-info">
                  <mat-icon>videocam</mat-icon>
                  <strong>{{ recording.roomName }}</strong>
                </div>
                <div class="recording-card-time">
                  <mat-icon>schedule</mat-icon>
                  <span>{{ recording.createdAt | date: 'medium' }}</span>
                </div>
              </button>
            </mat-card>
          } @empty {
            <div class="empty-state">
              <mat-icon>video_library</mat-icon>
              <span>No recordings available.</span>
            </div>
          }
        </div>
      </mat-card>

      <section class="workspace-main">
        <mat-card class="panel viewer-panel" appearance="outlined">
          <div class="section-head">
            <h2>Playback</h2>
            <span class="status-pill">OFFLINE</span>
          </div>

          <div class="recording-player">
            @if (selectedRecording()?.playbackUrl) {
              <video
                class="replay-video"
                [src]="selectedRecording()?.playbackUrl || ''"
                controls
                preload="metadata"
              ></video>
            } @else {
              <div class="viewer-empty viewer-empty-small">
                <mat-icon>play_circle_outline</mat-icon>
                <strong>Select a recording to play</strong>
              </div>
            }
          </div>
        </mat-card>
      </section>
    </section>
  `
})
export class RecordingsPageComponent implements OnInit {
  readonly api = inject(OperatorApiService);

  readonly recordings = signal<RecordingResponse[]>([]);
  readonly selectedRecordingId = signal<string | null>(null);
  readonly isLoading = signal(false);
  readonly pageError = signal<string | null>(null);

  readonly selectedRecording = computed(
    () =>
      this.recordings().find((recording) => recording.id === this.selectedRecordingId()) ?? null
  );

  ngOnInit(): void {
    void this.loadRecordings();
  }

  async loadRecordings(): Promise<void> {
    this.isLoading.set(true);
    this.pageError.set(null);
    try {
      const recordings = await this.api.listRecordings();
      const sortedRecordings = [...recordings].sort((left, right) =>
        right.createdAt.localeCompare(left.createdAt)
      );
      this.recordings.set(sortedRecordings);
      if (sortedRecordings.length) {
        this.selectedRecordingId.set(sortedRecordings[0].id);
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  selectRecording(id: string): void {
    this.selectedRecordingId.set(id);
  }
}
