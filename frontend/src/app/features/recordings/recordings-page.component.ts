import { Component, inject, OnInit, signal } from '@angular/core';
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
      <mat-card class="panel section-panel glass-panel" appearance="outlined">
        <div class="section-head premium-head">
          <div class="head-title">
            <h2>Archive</h2>
            <span class="subtle-text live-count">{{ recordings().length }}</span>
          </div>
          <button mat-icon-button class="refresh-btn" (click)="loadRecordings()" [disabled]="isLoading()">
             <mat-icon>refresh</mat-icon>
          </button>
        </div>

        @if (isLoading()) {
          <mat-progress-bar mode="indeterminate" class="premium-progress"></mat-progress-bar>
        }

        @if (pageError()) {
          <div class="notice notice-error">{{ pageError() }}</div>
        }

        <div class="recording-list session-list-scroll premium-scroll">
          @for (recording of recordings(); track recording.id) {
            <mat-card
              class="recording-card premium-card"
              appearance="outlined"
              [class.recording-card-selected]="recording.id === selectedRecordingId()"
            >
              <button class="recording-card-button recording-card-button-rich" type="button" (click)="selectRecording(recording.id)">
                <div class="recording-card-top">
                  <div class="recording-card-info">
                    <div class="session-avatar recording-avatar">
                      <mat-icon>person</mat-icon>
                    </div>
                    <div class="recording-card-copy">
                      <strong>{{ recording.workerName }}</strong>
                      <span class="recording-room">{{ recording.roomName }}</span>
                    </div>
                  </div>
                  <span class="recording-chip">REC</span>
                </div>

                <div class="recording-card-meta">
                  <div class="recording-card-meta-row">
                    <mat-icon>schedule</mat-icon>
                    <span>{{ recording.createdAt | date: 'medium' }}</span>
                  </div>

                  @if (recording.metadata?.latitude && recording.metadata?.longitude) {
                    <div class="recording-card-meta-row">
                      <mat-icon>place</mat-icon>
                      <span>{{ formatCoordinates(recording) }}</span>
                    </div>
                  }
                </div>
              </button>
            </mat-card>
          } @empty {
            <div class="empty-state premium-empty">
              <div class="empty-icon-wrap">
                <mat-icon>video_library</mat-icon>
              </div>
              <span class="empty-title">Archive Empty</span>
              <span class="empty-subtitle">No recordings available.</span>
            </div>
          }
        </div>
      </mat-card>

      <section class="workspace-main">
        <mat-card class="panel viewer-panel glass-panel" appearance="outlined">
          <div class="section-head premium-head viewer-head">
            <div class="viewer-head-copy">
              <h2>{{ selectedRecording()?.workerName || 'Playback' }}</h2>
              <p class="viewer-caption">{{ selectedRecording()?.roomName || 'Select a recording to play' }}</p>
            </div>
            <div class="viewer-status">
              <span class="status-pill premium-status-pill">
                OFFLINE
              </span>
            </div>
          </div>

          <div class="recording-player premium-frame">
            @if (isPlaybackLoading()) {
              <mat-progress-bar mode="indeterminate" class="premium-progress"></mat-progress-bar>
            }

            @if (playbackError()) {
              <div class="notice notice-error">{{ playbackError() }}</div>
            }

            <div class="viewer-stage premium-stage">
              @if (selectedPlaybackUrl()) {
                <video
                  class="replay-video"
                  [src]="selectedPlaybackUrl() || ''"
                  controls
                  preload="metadata"
                  style="width: 100%; display: block;"
                ></video>
              } @else {
                <div class="viewer-empty premium-empty-viewer">
                  <div class="radar-scan" style="opacity: 0.2; background: conic-gradient(from 0deg at 50% 50%, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, 0.05) 80%, rgba(255, 255, 255, 0.2) 100%);"></div>
                  <mat-icon class="huge-icon premium-huge-icon" style="color: rgba(255,255,255,0.2); filter: none;">play_circle_outline</mat-icon>
                  <strong>Select a recording to play</strong>
                </div>
              }
            </div>
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
  readonly selectedPlaybackUrl = signal<string | null>(null);
  readonly isLoading = signal(false);
  readonly isPlaybackLoading = signal(false);
  readonly pageError = signal<string | null>(null);
  readonly playbackError = signal<string | null>(null);

  selectedRecording(): RecordingResponse | null {
    const selectedRecordingId = this.selectedRecordingId();
    return this.recordings().find((recording) => recording.id === selectedRecordingId) ?? null;
  }

  formatCoordinates(recording: RecordingResponse): string {
    const latitude = recording.metadata?.latitude;
    const longitude = recording.metadata?.longitude;
    if (!latitude || !longitude) {
      return 'Location unavailable';
    }
    return `${latitude}, ${longitude}`;
  }

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
        await this.selectRecording(sortedRecordings[0].id);
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  async selectRecording(id: string): Promise<void> {
    this.selectedRecordingId.set(id);
    this.selectedPlaybackUrl.set(null);
    this.playbackError.set(null);
    this.isPlaybackLoading.set(true);

    try {
      const response = await this.api.getRecordingPlaybackUrl(id);
      if (this.selectedRecordingId() === id) {
        this.selectedPlaybackUrl.set(response.playbackUrl);
      }
    } catch (error) {
      if (this.selectedRecordingId() === id) {
        this.playbackError.set(this.api.explainError(error));
      }
    } finally {
      if (this.selectedRecordingId() === id) {
        this.isPlaybackLoading.set(false);
      }
    }
  }
}
