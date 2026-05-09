import { Component, inject, signal, OnInit } from '@angular/core';
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
              <button class="recording-card-button" type="button" (click)="selectRecording(recording.id)">
                <div class="recording-card-info" style="display: flex; align-items: flex-start; gap: 0.5rem; width: 100%;">
                  <mat-icon style="flex-shrink: 0;">videocam</mat-icon>
                  <strong style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1; text-align: left;">{{ recording.roomName }}</strong>
                </div>
                <div class="recording-card-time" style="display: flex; align-items: center; gap: 0.5rem; width: 100%;">
                  <mat-icon style="flex-shrink: 0;">schedule</mat-icon>
                  <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis; flex: 1; text-align: left;">{{ recording.createdAt | date: 'medium' }}</span>
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
              <h2>Playback</h2>
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
