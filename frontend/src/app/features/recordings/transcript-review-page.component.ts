import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OperatorApiService } from '@features/operations/operator-api.service';
import {
  RecordingTranscriptProcessingStage,
  RecordingTranscriptStatus,
  SessionRecordingTimelineResponse,
  SessionTranscriptRecordingResponse,
  SessionTranscriptResponse,
  TranscriptEngineOptionResponse
} from '@features/operations/operator.models';

@Component({
  selector: 'app-transcript-review-page',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatSnackBarModule],
  templateUrl: './transcript-review-page.component.html',
  styleUrl: './transcript-review-page.component.scss'
})
export class TranscriptReviewPageComponent {
  readonly api = inject(OperatorApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly sessionId = this.route.snapshot.paramMap.get('sessionId') ?? '';
  readonly transcript = signal<SessionTranscriptResponse | null>(null);
  readonly timeline = signal<SessionRecordingTimelineResponse | null>(null);
  readonly engineOptions = signal<TranscriptEngineOptionResponse[]>([]);
  readonly selectedEngine = signal<string | null>(null);
  readonly isLoading = signal(false);
  readonly isGenerating = signal(false);
  readonly isRetryingFailed = signal(false);
  readonly isRetryingRecordingId = signal<string | null>(null);

  constructor() {
    void this.loadPage();
  }

  statusLabel(status?: RecordingTranscriptStatus | null): string {
    switch (status) {
      case 'READY':
        return 'Ready';
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

  statusPillClass(status?: RecordingTranscriptStatus | null): string {
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

  stageLabel(stage?: RecordingTranscriptProcessingStage | null): string {
    switch (stage) {
      case 'QUEUED':
        return 'Queued';
      case 'TRANSCRIBING':
        return 'Transcribing';
      case 'TRANSCRIBED':
        return 'Raw STT Ready';
      case 'PUNCTUATED':
        return 'Punctuated';
      case 'FINALIZED':
        return 'Finalized';
      case 'FAILED':
        return 'Failed';
      default:
        return 'Stage unavailable';
    }
  }

  setSelectedEngine(engine: string): void {
    this.selectedEngine.set(engine || null);
  }

  hasRetryableRecordings(): boolean {
    const transcript = this.transcript();
    if (!transcript) {
      return false;
    }
    return transcript.failedRecordings > 0 || transcript.notRequestedRecordings > 0;
  }

  canRetryRecording(recording: SessionTranscriptRecordingResponse): boolean {
    return recording.status === 'FAILED' || recording.status === 'NOT_REQUESTED';
  }

  recordingOrdinal(recording: SessionTranscriptRecordingResponse): number {
    return (recording.recordingSequence ?? recording.transcriptSegmentCount ?? 0) + 1;
  }

  recordingWindow(recording: SessionTranscriptRecordingResponse): string {
    const startSeconds = recording.sessionElapsedStartMs != null ? Math.floor(recording.sessionElapsedStartMs / 1000) : null;
    const endSeconds = recording.sessionElapsedEndMs != null ? Math.floor(recording.sessionElapsedEndMs / 1000) : null;
    if (startSeconds == null && endSeconds == null) {
      return 'Session time unavailable';
    }
    return `${this.formatDuration(startSeconds)} - ${this.formatDuration(endSeconds)}`;
  }

  recordingSummary(recording: SessionTranscriptRecordingResponse): string {
    if (recording.status === 'READY') {
      return `${recording.transcriptSegmentCount} transcript line${recording.transcriptSegmentCount === 1 ? '' : 's'} ready for this clip.`;
    }
    if (recording.status === 'FAILED') {
      return recording.errorMessage?.trim() || 'Transcript generation failed for this clip.';
    }
    if (recording.status === 'PROCESSING' || recording.status === 'PENDING') {
      return 'Transcript generation is still running for this clip.';
    }
    return 'Transcript generation has not been requested for this clip yet.';
  }

  async generateTranscript(): Promise<void> {
    if (!this.sessionId) {
      return;
    }

    this.isGenerating.set(true);
    try {
      await this.api.generateSessionTranscript(this.sessionId, this.selectedEngine());
      await this.refreshSessionData();
    } catch (error) {
      this.showError(this.api.explainError(error));
    } finally {
      this.isGenerating.set(false);
    }
  }

  async retryFailedIntervals(): Promise<void> {
    if (!this.sessionId) {
      return;
    }

    this.isRetryingFailed.set(true);
    try {
      await this.api.retryFailedSessionTranscript(this.sessionId, this.selectedEngine());
      await this.refreshSessionData();
    } catch (error) {
      this.showError(this.api.explainError(error));
    } finally {
      this.isRetryingFailed.set(false);
    }
  }

  async retryRecording(recording: SessionTranscriptRecordingResponse): Promise<void> {
    this.isRetryingRecordingId.set(recording.recordingId);
    try {
      await this.api.generateRecordingTranscript(recording.recordingId, this.selectedEngine());
      await this.refreshSessionData();
    } catch (error) {
      this.showError(this.api.explainError(error));
    } finally {
      this.isRetryingRecordingId.set(null);
    }
  }

  backToRecordings(): void {
    void this.router.navigate(['/recordings']);
  }

  private async loadPage(): Promise<void> {
    if (!this.sessionId) {
      this.showError('Transcript review session is missing.');
      void this.router.navigate(['/recordings']);
      return;
    }

    this.isLoading.set(true);
    try {
      const [engineOptions, transcript, timeline] = await Promise.all([
        this.api.getTranscriptEngines(),
        this.api.getSessionTranscript(this.sessionId),
        this.api.getSessionRecordingTimeline(this.sessionId)
      ]);
      this.engineOptions.set(engineOptions);
      this.transcript.set(transcript);
      this.timeline.set(timeline);
      this.selectedEngine.set(transcript.engine ?? engineOptions.find((option) => option.configuredDefault)?.key ?? null);
    } catch (error) {
      this.showError(this.api.explainError(error));
      void this.router.navigate(['/recordings']);
    } finally {
      this.isLoading.set(false);
    }
  }

  private async refreshSessionData(): Promise<void> {
    const [transcript, timeline] = await Promise.all([
      this.api.getSessionTranscript(this.sessionId),
      this.api.getSessionRecordingTimeline(this.sessionId)
    ]);
    this.transcript.set(transcript);
    this.timeline.set(timeline);
    this.selectedEngine.set(transcript.engine ?? this.selectedEngine());
  }

  private formatDuration(totalSeconds: number | null): string {
    if (totalSeconds == null || totalSeconds < 0) {
      return 'Unknown';
    }
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  private showError(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}
