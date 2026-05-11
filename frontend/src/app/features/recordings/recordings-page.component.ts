import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { OperatorApiService } from '@features/operations/operator-api.service';
import {
  RecordingResponse,
  RecordingTranscriptResponse,
  RecordingTranscriptSegmentResponse,
  RecordingTranscriptStatus
} from '@features/operations/operator.models';

@Component({
  selector: 'app-recordings-page',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
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
              class="archive-card premium-card"
              appearance="outlined"
              [class.archive-card-selected]="recording.id === selectedRecordingId()"
              (click)="selectRecording(recording.id)"
            >
              <div class="archive-card-inner">
                <div class="archive-thumb">
                  <div class="thumb-overlay">
                    <mat-icon class="play-icon">play_arrow</mat-icon>
                  </div>
                  <div class="thumb-placeholder">
                     <mat-icon>videocam</mat-icon>
                  </div>
                  <div class="duration-pill">REC</div>
                </div>

                <div class="archive-details">
                  <div class="archive-header">
                    <strong class="archive-worker">{{ recording.workerName }}</strong>
                    <span class="transcript-pill" [class]="transcriptPillClass(recording.transcriptStatus)">
                      {{ transcriptLabel(recording.transcriptStatus) }}
                    </span>
                  </div>
                  <span class="archive-room">{{ recording.roomName }}</span>

                  <div class="archive-meta">
                    <div class="meta-item">
                      <mat-icon>schedule</mat-icon>
                      <span>{{ recording.createdAt | date: 'MMM d, h:mm a' }}</span>
                    </div>
                    @if (recording.metadata?.latitude && recording.metadata?.longitude) {
                      <div class="meta-item">
                        <mat-icon>place</mat-icon>
                        <span class="meta-truncate" [matTooltip]="formatCoordinates(recording)">{{ formatCoordinates(recording) }}</span>
                      </div>
                    }
                  </div>
                </div>
              </div>
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
                >
                  @if (selectedSubtitleUrl()) {
                    <track
                      kind="subtitles"
                      label="Transcript Subtitles"
                      srclang="en"
                      [attr.src]="selectedSubtitleUrl()"
                      default
                    />
                  }
                </video>
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

        <mat-card class="panel glass-panel transcript-panel" appearance="outlined">
          <div class="section-head premium-head">
            <div class="viewer-head-copy">
              <h2>Transcript</h2>
              <p class="viewer-caption">
                Real transcript and subtitles are generated from the uploaded recording audio.
              </p>
            </div>
            @if (selectedRecording()) {
              <button
                mat-flat-button
                class="premium-btn"
                type="button"
                (click)="generateTranscript()"
                [disabled]="isTranscriptGenerating() || isTranscriptLoading()"
              >
                {{ selectedTranscript()?.status === 'FAILED'
                  ? 'Retry Transcript'
                  : selectedTranscript()?.status === 'READY'
                    ? 'Regenerate Transcript'
                    : 'Generate Transcript' }}
              </button>
            }
          </div>

          @if (isTranscriptLoading()) {
            <mat-progress-bar mode="indeterminate" class="premium-progress"></mat-progress-bar>
          }

          @if (transcriptError()) {
            <div class="notice notice-error">{{ transcriptError() }}</div>
          }

          @if (selectedTranscript()) {
            <div class="transcript-status-row">
              <span class="transcript-pill transcript-pill-detail" [class]="transcriptPillClass(selectedTranscript()?.status)">
                {{ transcriptLabel(selectedTranscript()?.status) }}
              </span>
              @if (selectedTranscript()?.engine) {
                <span class="subtle-text">Engine: {{ selectedTranscript()?.engine }}</span>
              }
              @if (selectedTranscript()?.model) {
                <span class="subtle-text">Model: {{ selectedTranscript()?.model }}</span>
              }
            </div>

            @switch (selectedTranscript()?.status) {
              @case ('NOT_REQUESTED') {
                <div class="empty-state transcript-empty">
                  <strong>No transcript yet</strong>
                  <span>Generate one for this recording when the operator needs it.</span>
                </div>
              }
              @case ('FAILED') {
                <div class="notice notice-error">
                  {{ selectedTranscript()?.errorMessage || 'Transcript generation failed.' }}
                </div>
              }
              @default {
                @if (selectedTranscript()?.fullText) {
                  <div class="transcript-full-text">
                    {{ selectedTranscript()?.fullText }}
                  </div>
                }

                @if (selectedTranscript()?.segments?.length) {
                  <div class="transcript-segments">
                    @for (segment of selectedTranscript()?.segments || []; track segment.id || segment.segmentIndex) {
                      <div class="transcript-segment">
                        <span class="segment-time">{{ formatSegmentTime(segment) }}</span>
                        <span class="segment-text">{{ segment.text }}</span>
                      </div>
                    }
                  </div>
                } @else if (selectedTranscript()?.status === 'PROCESSING' || selectedTranscript()?.status === 'PENDING') {
                  <div class="empty-state transcript-empty">
                    <strong>Transcript request accepted</strong>
                    <span>The backend is preparing transcript content for this recording.</span>
                  </div>
                }
              }
            }
          } @else {
            <div class="empty-state transcript-empty">
              <strong>Select a recording</strong>
              <span>Transcript details will appear here with playback.</span>
            </div>
          }
        </mat-card>
      </section>
    </section>
  `
})
export class RecordingsPageComponent implements OnInit, OnDestroy {
  readonly api = inject(OperatorApiService);

  readonly recordings = signal<RecordingResponse[]>([]);
  readonly selectedRecordingId = signal<string | null>(null);
  readonly selectedPlaybackUrl = signal<string | null>(null);
  readonly selectedSubtitleUrl = signal<string | null>(null);
  readonly selectedTranscript = signal<RecordingTranscriptResponse | null>(null);
  readonly isLoading = signal(false);
  readonly isPlaybackLoading = signal(false);
  readonly isTranscriptLoading = signal(false);
  readonly isTranscriptGenerating = signal(false);
  readonly pageError = signal<string | null>(null);
  readonly playbackError = signal<string | null>(null);
  readonly transcriptError = signal<string | null>(null);

  ngOnDestroy(): void {
    this.revokeSubtitleUrl();
  }

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

  transcriptLabel(status?: RecordingTranscriptStatus | null): string {
    switch (status) {
      case 'READY':
        return 'Transcript Ready';
      case 'PROCESSING':
        return 'Processing';
      case 'PENDING':
        return 'Pending';
      case 'FAILED':
        return 'Failed';
      case 'NOT_REQUESTED':
      default:
        return 'Not Requested';
    }
  }

  transcriptPillClass(status?: RecordingTranscriptStatus | null): string {
    switch (status) {
      case 'READY':
        return 'transcript-pill-ready';
      case 'PROCESSING':
      case 'PENDING':
        return 'transcript-pill-progress';
      case 'FAILED':
        return 'transcript-pill-failed';
      case 'NOT_REQUESTED':
      default:
        return 'transcript-pill-idle';
    }
  }

  formatSegmentTime(segment: RecordingTranscriptSegmentResponse): string {
    const startSeconds = Number.parseFloat(segment.startSeconds ?? '0');
    const minutes = Math.floor(startSeconds / 60);
    const seconds = Math.floor(startSeconds % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
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
    this.selectedTranscript.set(null);
    this.playbackError.set(null);
    this.transcriptError.set(null);
    this.revokeSubtitleUrl();
    this.isPlaybackLoading.set(true);
    this.isTranscriptLoading.set(true);

    const [playbackResult, transcriptResult] = await Promise.allSettled([
      this.api.getRecordingPlaybackUrl(id),
      this.api.getRecordingTranscript(id)
    ]);

    if (this.selectedRecordingId() !== id) {
      return;
    }

    if (playbackResult.status === 'fulfilled') {
      this.selectedPlaybackUrl.set(playbackResult.value.playbackUrl);
    } else {
      this.playbackError.set(this.api.explainError(playbackResult.reason));
    }

    if (transcriptResult.status === 'fulfilled') {
      this.selectedTranscript.set(transcriptResult.value);
      this.patchRecordingTranscriptStatus(id, transcriptResult.value.status);
      await this.loadSubtitleTrackIfAvailable(id, transcriptResult.value.status);
    } else {
      this.transcriptError.set(this.api.explainError(transcriptResult.reason));
    }

    this.isPlaybackLoading.set(false);
    this.isTranscriptLoading.set(false);
  }

  async generateTranscript(): Promise<void> {
    const recording = this.selectedRecording();
    if (!recording) {
      return;
    }

    this.isTranscriptGenerating.set(true);
    this.transcriptError.set(null);
    this.revokeSubtitleUrl();

    try {
      const transcript = await this.api.generateRecordingTranscript(recording.id);
      if (this.selectedRecordingId() === recording.id) {
        this.selectedTranscript.set(transcript);
        this.patchRecordingTranscriptStatus(recording.id, transcript.status);
        await this.loadSubtitleTrackIfAvailable(recording.id, transcript.status);
      }
    } catch (error) {
      if (this.selectedRecordingId() === recording.id) {
        this.transcriptError.set(this.api.explainError(error));
      }
    } finally {
      this.isTranscriptGenerating.set(false);
    }
  }

  private async loadSubtitleTrackIfAvailable(recordingId: string, status?: RecordingTranscriptStatus | null): Promise<void> {
    if (status !== 'READY') {
      this.selectedSubtitleUrl.set(null);
      return;
    }

    try {
      const subtitleVtt = await this.api.getRecordingTranscriptSubtitles(recordingId);
      if (this.selectedRecordingId() !== recordingId) {
        return;
      }
      const subtitleUrl = URL.createObjectURL(new Blob([subtitleVtt], { type: 'text/vtt' }));
      this.selectedSubtitleUrl.set(subtitleUrl);
    } catch (error) {
      if (this.selectedRecordingId() === recordingId) {
        this.transcriptError.set(this.api.explainError(error));
      }
    }
  }

  private revokeSubtitleUrl(): void {
    const subtitleUrl = this.selectedSubtitleUrl();
    if (subtitleUrl) {
      URL.revokeObjectURL(subtitleUrl);
    }
    this.selectedSubtitleUrl.set(null);
  }

  private patchRecordingTranscriptStatus(recordingId: string, status: RecordingTranscriptStatus): void {
    this.recordings.update((recordings) =>
      recordings.map((recording) =>
        recording.id === recordingId
          ? { ...recording, transcriptStatus: status }
          : recording
      )
    );
  }
}
