import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { Router } from '@angular/router';
import { OperatorApiService } from '@features/operations/operator-api.service';
import { ConfirmDialogComponent } from '@shared/components/confirm-dialog/confirm-dialog.component';
import {
  RecordingInvestigationSearchHitResponse,
  RecordingInvestigationSearchResponse,
  RecordingResponse,
  TranscriptEngineOptionResponse,
  RecordingTranscriptProcessingStage,
  TranscriptSmokeCheckResponse,
  SessionRecordingIntegrityStatus,
  SessionRecordingExportResponse,
  SessionRecordingTimelineGapResponse,
  RecordingTranscriptSegmentResponse,
  RecordingTranscriptStatus,
  SessionTranscriptRecordingResponse,
  SessionRecordingTimelineResponse,
  SessionRecordingTimelineSegmentResponse,
  SessionTranscriptSearchResponse,
  SessionTranscriptSegmentResponse,
  SessionTranscriptResponse
} from '@features/operations/operator.models';

interface RecordingSessionCard {
  sessionId: string;
  workerName: string;
  roomName: string;
  referenceNumber: string;
  latestCreatedAt: string;
  recordingCount: number;
  approxDurationSeconds: number;
  latitude: string | null;
  longitude: string | null;
  transcriptStatus: RecordingTranscriptStatus | null;
}

@Component({
  selector: 'app-recordings-page',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule, MatDialogModule, MatSnackBarModule, ClipboardModule],
  templateUrl: './recordings-page.component.html',
  styleUrl: './recordings-page.component.scss'
})
export class RecordingsPageComponent implements OnDestroy {
  readonly api = inject(OperatorApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  @ViewChild('timelinePlayer')
  private timelinePlayer?: ElementRef<HTMLVideoElement>;

  @ViewChild('viewerPanel')
  private viewerPanel?: ElementRef<HTMLElement>;

  readonly recordings = signal<RecordingResponse[]>([]);
  readonly recordingSessions = signal<RecordingSessionCard[]>([]);
  readonly investigationSearchQuery = signal('');
  readonly investigationSearchResults = signal<RecordingInvestigationSearchResponse | null>(null);
  readonly selectedSessionId = signal<string | null>(null);
  readonly selectedTimeline = signal<SessionRecordingTimelineResponse | null>(null);
  readonly selectedSessionExport = signal<SessionRecordingExportResponse | null>(null);
  readonly selectedTimelineSegmentIndex = signal(0);
  readonly selectedRecordingId = signal<string | null>(null);
  readonly selectedPlaybackUrl = signal<string | null>(null);
  readonly selectedSubtitleUrl = signal<string | null>(null);
  readonly selectedSessionTranscript = signal<SessionTranscriptResponse | null>(null);
  readonly transcriptSmokeCheck = signal<TranscriptSmokeCheckResponse | null>(null);
  readonly transcriptEngineOptions = signal<TranscriptEngineOptionResponse[]>([]);
  readonly selectedTranscriptEngine = signal<string | null>(null);
  readonly activeTranscriptSegmentId = signal<string | null>(null);
  readonly isTranscriptSeekHighlight = signal(false);
  readonly reviewClipNotice = signal<{ title: string; message: string } | null>(null);
  readonly transcriptSearchQuery = signal('');
  readonly transcriptReviewFilter = signal<'all' | 'failed' | 'missing' | 'pending' | 'low-confidence'>('all');
  readonly transcriptSearchResults = signal<SessionTranscriptSearchResponse | null>(null);
  readonly isInvestigationSearching = signal(false);
  readonly isTranscriptSearching = signal(false);
  readonly isLoading = signal(false);
  readonly isPlaybackLoading = signal(false);
  readonly isExportLoading = signal(false);
  readonly isExportRequesting = signal(false);
  readonly isTranscriptLoading = signal(false);
  readonly isTranscriptGenerating = signal(false);
  readonly isTranscriptSummaryLoading = signal(false);
  readonly showTranscriptSummary = signal(false);
  readonly isRetryingFailedTranscript = signal(false);
  readonly isRetryingTranscriptRecordingId = signal<string | null>(null);
  readonly isDeletingSessionRecordings = signal(false);

  readonly nextCursor = signal<string | null>(null);
  readonly pageSize = signal(50);
  readonly totalRecordings = signal(0);
  readonly hasMoreRecordings = signal(true);
  readonly isLoadingMore = signal(false);

  private pendingAutoplay = false;
  private pendingSeekSecondsWithinSegment: number | null = null;
  private transcriptSeekHighlightHandle: ReturnType<typeof setTimeout> | null = null;
  private transcriptSearchRequestId = 0;
  private investigationSearchRequestId = 0;
  private exportPollHandle: ReturnType<typeof setTimeout> | null = null;
  private transcriptPollHandle: ReturnType<typeof setTimeout> | null = null;
  private transcriptPollToken = 0;
  private pendingInvestigationHit: RecordingInvestigationSearchHitResponse | null = null;

  constructor() {
    void this.loadRecordings();
    void this.loadTranscriptEngines();
  }

  private showError(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }

  ngOnDestroy(): void {
    this.clearExportPolling();
    this.clearTranscriptPolling();
    this.clearTranscriptSeekHighlight();
    this.revokeSubtitleUrl();
  }

  selectedSession(): RecordingSessionCard | null {
    const sessionId = this.selectedSessionId();
    return this.recordingSessions().find((session) => session.sessionId === sessionId) ?? null;
  }

  visibleRecordingSessions(): RecordingSessionCard[] {
    const query = this.investigationSearchQuery().trim();
    if (!query) {
      return this.recordingSessions();
    }
    const matchingSessionIds = new Set((this.investigationSearchResults()?.hits ?? []).map((hit) => hit.sessionId));
    return this.recordingSessions().filter((session) => matchingSessionIds.has(session.sessionId));
  }

  activeTimelineSegment(): SessionRecordingTimelineSegmentResponse | null {
    const timeline = this.selectedTimeline();
    if (!timeline) {
      return null;
    }
    return timeline.segments[this.selectedTimelineSegmentIndex()] ?? null;
  }

  selectedTimelineSegmentOrdinal(): number {
    const timeline = this.selectedTimeline();
    if (!timeline?.segments.length) {
      return 0;
    }
    return this.selectedTimelineSegmentIndex() + 1;
  }

  selectedSessionCaption(): string {
    const session = this.selectedSession();
    if (!session) {
      return 'Select a session recording to play';
    }

    return session.roomName;
  }

  formatSessionCoordinates(session: RecordingSessionCard): string {
    if (!session.latitude || !session.longitude) {
      return 'Location unavailable';
    }
    return `${session.latitude}, ${session.longitude}`;
  }

  formatDurationMs(durationMs: number | null | undefined): string {
    if (durationMs == null || durationMs < 0) {
      return 'Unknown';
    }
    return this.formatDurationFromSeconds(Math.round(durationMs / 1000));
  }

  formatDurationFromSeconds(totalSeconds: number | null | undefined): string {
    if (totalSeconds == null || totalSeconds <= 0) {
      return '0:00';
    }
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  formatTimelineSegmentDuration(segment: SessionRecordingTimelineSegmentResponse | null): string {
    return this.formatDurationFromSeconds(segment?.durationSeconds ?? 0);
  }

  formatBytes(value: number | null | undefined): string {
    if (value == null || value <= 0) {
      return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = value;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
  }

  timelineSegmentLabel(segment: SessionRecordingTimelineSegmentResponse, index: number): string {
    if (segment.segmentSequence != null) {
      return `Segment ${segment.segmentSequence + 1}`;
    }
    return `Segment ${index + 1}`;
  }

  segmentWarning(segment: SessionRecordingTimelineSegmentResponse): boolean {
    return segment.sessionElapsedStartMs == null || segment.sessionElapsedEndMs == null;
  }

  integrityLabel(status?: SessionRecordingIntegrityStatus | null): string {
    switch (status) {
      case 'COMPLETE':
        return 'Complete Timeline';
      case 'PROCESSING_UPLOADS':
        return 'Uploads In Progress';
      case 'PARTIAL':
        return 'Partial Timeline';
      case 'HAS_GAPS':
      default:
        return 'Timeline Needs Review';
    }
  }

  integrityIcon(status?: SessionRecordingIntegrityStatus | null): string {
    switch (status) {
      case 'COMPLETE':
        return 'verified';
      case 'PROCESSING_UPLOADS':
        return 'cloud_upload';
      case 'PARTIAL':
        return 'pending';
      case 'HAS_GAPS':
      default:
        return 'warning_amber';
    }
  }

  integrityPillClass(status?: SessionRecordingIntegrityStatus | null): string {
    switch (status) {
      case 'COMPLETE':
        return 'timeline-summary-pill-success';
      case 'PROCESSING_UPLOADS':
        return 'timeline-summary-pill-info';
      case 'PARTIAL':
        return 'timeline-summary-pill-neutral';
      case 'HAS_GAPS':
      default:
        return 'timeline-summary-pill-warning';
    }
  }

  timelineIntegrityDetails(): string[] {
    const timeline = this.selectedTimeline();
    if (!timeline) {
      return [];
    }

    const details: string[] = [];
    if (timeline.hasTimelineGaps || timeline.missingSequenceCount > 0) {
      details.push(`${timeline.missingSequenceCount} missing sequence gap${timeline.missingSequenceCount === 1 ? '' : 's'} detected`);
    }
    if (timeline.duplicateSegmentCount > 0) {
      details.push(`${timeline.duplicateSegmentCount} duplicate upload segment${timeline.duplicateSegmentCount === 1 ? '' : 's'} need review`);
    }
    if (timeline.segmentsMissingTimingCount > 0) {
      details.push(`${timeline.segmentsMissingTimingCount} segment${timeline.segmentsMissingTimingCount === 1 ? '' : 's'} missing timing metadata`);
    }
    return details;
  }

  timelineGaps(): SessionRecordingTimelineGapResponse[] {
    return this.selectedTimeline()?.gaps ?? [];
  }

  timelineGapRangeLabel(gap: SessionRecordingTimelineGapResponse): string {
    if (gap.startMs == null && gap.endMs == null) {
      return 'Timing range unavailable';
    }
    const start = gap.startMs == null ? 'Unknown' : this.formatDurationMs(gap.startMs);
    const end = gap.endMs == null ? 'Unknown' : this.formatDurationMs(gap.endMs);
    return `${start} - ${end}`;
  }

  transcriptSmokeCheckLabel(): string {
    const smokeCheck = this.transcriptSmokeCheck();
    if (!smokeCheck) {
      return 'Transcript health unavailable';
    }
    return smokeCheck.ready ? 'Transcript pipeline ready' : 'Transcript pipeline needs attention';
  }

  transcriptEngineLabel(): string {
    const selected = this.selectedTranscriptEngine();
    if (!selected) {
      return 'Configured default engine';
    }
    return this.transcriptEngineOptions().find((option) => option.key === selected)?.label ?? selected;
  }

  transcriptEngineWarning(recording?: SessionTranscriptRecordingResponse | null): string | null {
    const lastErrorStage = recording?.lastErrorStage ?? this.selectedSessionTranscript()?.lastErrorStage ?? null;
    const retryCount = recording?.retryCount ?? this.selectedSessionTranscript()?.retryCount ?? 0;
    if (!lastErrorStage && retryCount <= 0) {
      return null;
    }
    return `Last failure stage: ${this.transcriptProcessingStageLabel(lastErrorStage)} | Retries: ${retryCount}`;
  }

  setSelectedTranscriptEngine(engine: string): void {
    this.selectedTranscriptEngine.set(engine || null);
  }

  transcriptProcessingStageLabel(stage?: RecordingTranscriptProcessingStage | null): string {
    switch (stage) {
      case 'QUEUED':
        return 'Queued';
      case 'TRANSCRIBING':
        return 'Transcribing';
      case 'TRANSCRIBED':
        return 'Raw STT Ready';
      case 'PUNCTUATED':
        return 'Punctuation Added';
      case 'FINALIZED':
        return 'Finalized';
      case 'FAILED':
        return 'Stage Failed';
      default:
        return 'Stage Unavailable';
    }
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

  transcriptPanelHint(): string {
    const transcript = this.selectedSessionTranscript();
    if (!transcript) {
      return 'Select a session to view or generate a transcript.';
    }

    switch (transcript.status) {
      case 'READY':
        return `${transcript.segments.length} timestamped line${transcript.segments.length === 1 ? '' : 's'} ready. Click a timestamp to jump in the video.`;
      case 'PROCESSING':
      case 'PENDING':
        return 'Transcript generation is in progress for this session.';
      case 'FAILED':
        return transcript.errorMessage?.trim() || 'Transcript generation failed for this session.';
      case 'NOT_REQUESTED':
      default:
        return 'Generate a session transcript when you need to review what was said.';
    }
  }

  transcriptIcon(status?: RecordingTranscriptStatus | null): string {
    switch (status) {
      case 'READY':
        return 'description';
      case 'PROCESSING':
      case 'PENDING':
        return 'hourglass_empty';
      case 'FAILED':
        return 'error_outline';
      case 'NOT_REQUESTED':
      default:
        return 'article';
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

  exportStatusLabel(exportResponse: SessionRecordingExportResponse | null): string {
    switch (exportResponse?.status) {
      case 'READY':
        return 'Export package ready';
      case 'PROCESSING':
        return 'Export packaging in progress';
      case 'PENDING':
        return 'Export queued';
      case 'FAILED':
        return exportResponse.errorMessage || 'Export package failed';
      default:
        return 'No export package requested yet';
    }
  }

  investigationFieldLabel(field: string): string {
    switch (field) {
      case 'referenceNumber':
        return 'Reference match';
      case 'roomName':
        return 'Room match';
      case 'workerName':
        return 'Worker match';
      case 'transcript':
      default:
        return 'Transcript match';
    }
  }

  formatSegmentTime(segment: RecordingTranscriptSegmentResponse): string {
    const startSeconds = Number.parseFloat(segment.startSeconds ?? '0');
    const minutes = Math.floor(startSeconds / 60);
    const seconds = Math.floor(startSeconds % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  filteredTranscriptSegments(): SessionTranscriptSegmentResponse[] {
    const segments = this.selectedSessionTranscript()?.segments ?? [];
    const reviewFilter = this.transcriptReviewFilter();
    const filteredRecordingIds = new Set(this.filteredTranscriptReviewRecordings().map((recording) => recording.recordingId));
    const query = this.transcriptSearchQuery().trim();
    if (query) {
      const matches = this.transcriptSearchResults()?.matches ?? [];
      if (reviewFilter === 'all') {
        return matches;
      }
      return matches.filter((segment) => filteredRecordingIds.has(segment.recordingId));
    }
    if (reviewFilter === 'all') {
      return segments;
    }
    return segments.filter((segment) => filteredRecordingIds.has(segment.recordingId));
  }

  lowConfidenceTranscriptCount(): number {
    return (this.selectedSessionTranscript()?.segments ?? []).filter((segment) => this.isLowConfidenceSegment(segment)).length;
  }

  hasRetryableTranscriptIntervals(): boolean {
    const transcript = this.selectedSessionTranscript();
    if (!transcript) {
      return false;
    }
    return transcript.failedRecordings > 0 || transcript.notRequestedRecordings > 0;
  }

  filteredTranscriptReviewRecordings(): SessionTranscriptRecordingResponse[] {
    const recordings = this.selectedSessionTranscript()?.recordings ?? [];
    switch (this.transcriptReviewFilter()) {
      case 'failed':
        return recordings.filter((recording) => recording.status === 'FAILED');
      case 'missing':
        return recordings.filter((recording) => recording.status === 'NOT_REQUESTED');
      case 'pending':
        return recordings.filter((recording) => recording.status === 'PENDING' || recording.status === 'PROCESSING');
      case 'low-confidence': {
        const lowConfidenceRecordingIds = new Set(
          (this.selectedSessionTranscript()?.segments ?? [])
            .filter((segment) => this.isLowConfidenceSegment(segment))
            .map((segment) => segment.recordingId)
        );
        return recordings.filter((recording) => lowConfidenceRecordingIds.has(recording.recordingId));
      }
      case 'all':
      default:
        return recordings;
    }
  }

  isLowConfidenceSegment(segment: SessionTranscriptSegmentResponse): boolean {
    const confidence = this.parseConfidence(segment.confidence);
    return confidence != null && confidence < 0.6;
  }

  formatConfidence(confidence: string | null | undefined): string {
    const numericConfidence = this.parseConfidence(confidence);
    if (numericConfidence == null) {
      return 'N/A';
    }
    return `${Math.round(numericConfidence * 100)}%`;
  }

  setTranscriptReviewFilter(filter: 'all' | 'failed' | 'missing' | 'pending' | 'low-confidence'): void {
    this.transcriptReviewFilter.set(filter);
  }

  shouldShowTranscriptFullText(): boolean {
    return this.transcriptReviewFilter() === 'all' && !this.transcriptSearchQuery().trim();
  }

  shouldShowTranscriptSummary(): boolean {
    const transcript = this.selectedSessionTranscript();
    return !!(transcript && (transcript.shortSummary || transcript.incidentSummary || transcript.keywords.length));
  }

  shouldShowTranscriptSegments(): boolean {
    const transcript = this.selectedSessionTranscript();
    if (!transcript) {
      return false;
    }
    return transcript.segments.length > 0 || this.transcriptReviewFilter() !== 'all';
  }

  transcriptFilterEmptyMessage(): string {
    switch (this.transcriptReviewFilter()) {
      case 'failed':
        return 'Failed intervals do not have transcript text because generation did not complete for those clips.';
      case 'missing':
        return 'Missing intervals have not been transcribed yet, so there is no transcript text to review.';
      case 'pending':
        return 'Pending intervals are still queued or processing, so transcript text is not available yet.';
      case 'low-confidence':
        return 'No low-confidence transcript segments were found for this session.';
      case 'all':
      default:
        return 'No transcript text is available for this selection.';
    }
  }

  updateInvestigationSearch(query: string): void {
    this.investigationSearchQuery.set(query);
        const normalizedQuery = query.trim();
    if (!normalizedQuery) {
      this.investigationSearchRequestId++;
      this.investigationSearchResults.set(null);
      this.isInvestigationSearching.set(false);
      return;
    }

    this.isInvestigationSearching.set(true);
    const requestId = ++this.investigationSearchRequestId;
    void this.api.searchRecordingsForInvestigation(normalizedQuery)
      .then((response) => {
        if (requestId !== this.investigationSearchRequestId) {
          return;
        }
        this.investigationSearchResults.set(response);
      })
      .catch((error) => {
        if (requestId !== this.investigationSearchRequestId) {
          return;
        }
        this.investigationSearchResults.set({
          query: normalizedQuery,
          totalMatches: 0,
          hits: []
        });
      })
      .finally(() => {
        if (requestId === this.investigationSearchRequestId) {
          this.isInvestigationSearching.set(false);
        }
      });
  }

  clearInvestigationSearch(): void {
    this.investigationSearchQuery.set('');
    this.investigationSearchRequestId++;
    this.investigationSearchResults.set(null);
    this.isInvestigationSearching.set(false);
  }

  transcriptRecordingOrdinal(recording: SessionTranscriptRecordingResponse): number {
    return (recording.recordingSequence ?? 0) + 1;
  }

  formatTranscriptRecordingWindow(recording: SessionTranscriptRecordingResponse): string {
    const startMs = recording.sessionElapsedStartMs ?? 0;
    const endMs = recording.sessionElapsedEndMs ?? startMs + ((recording.durationSeconds ?? 0) * 1000);
    return `${this.formatDurationMs(startMs)} - ${this.formatDurationMs(endMs)}`;
  }

  transcriptRecordingBody(recording: SessionTranscriptRecordingResponse): string {
    switch (recording.status) {
      case 'READY':
        return `${recording.transcriptSegmentCount} transcript segment${recording.transcriptSegmentCount === 1 ? '' : 's'} available for this clip.`;
      case 'FAILED':
        return recording.errorMessage?.trim() || 'Transcript generation failed for this clip and can be retried independently.';
      case 'PROCESSING':
        return `Transcript generation is currently running for this clip at ${this.transcriptProcessingStageLabel(recording.processingStage)}.`;
      case 'PENDING':
        return `Transcript generation is queued for this clip at ${this.transcriptProcessingStageLabel(recording.processingStage)}.`;
      case 'NOT_REQUESTED':
      default:
        return 'This clip is still missing transcript coverage.';
    }
  }

  canRetryTranscriptRecording(recording: SessionTranscriptRecordingResponse): boolean {
    return recording.status === 'FAILED' || recording.status === 'NOT_REQUESTED';
  }

  updateTranscriptSearch(query: string): void {
    this.transcriptSearchQuery.set(query);
        const normalizedQuery = query.trim();
    const sessionId = this.selectedSessionId();
    if (!normalizedQuery || !sessionId) {
      this.transcriptSearchRequestId++;
      this.transcriptSearchResults.set(null);
      this.isTranscriptSearching.set(false);
      return;
    }

    this.isTranscriptSearching.set(true);
    const requestId = ++this.transcriptSearchRequestId;
    void this.api.searchSessionTranscript(sessionId, normalizedQuery)
      .then((response) => {
        if (requestId !== this.transcriptSearchRequestId || this.selectedSessionId() !== sessionId) {
          return;
        }
        this.transcriptSearchResults.set(response);
      })
      .catch((error) => {
        if (requestId !== this.transcriptSearchRequestId || this.selectedSessionId() !== sessionId) {
          return;
        }
        this.transcriptSearchResults.set({
          sessionId,
          query: normalizedQuery,
          status: this.selectedSessionTranscript()?.status ?? 'NOT_REQUESTED',
          totalMatches: 0,
          matches: []
        });
      })
      .finally(() => {
        if (requestId === this.transcriptSearchRequestId && this.selectedSessionId() === sessionId) {
          this.isTranscriptSearching.set(false);
        }
      });
  }

  clearTranscriptSearch(): void {
    this.transcriptSearchQuery.set('');
    this.transcriptSearchRequestId++;
    this.transcriptSearchResults.set(null);
    this.isTranscriptSearching.set(false);
  }

  async loadRecordings(): Promise<void> {
    this.isLoading.set(true);
    this.nextCursor.set(null);
    this.hasMoreRecordings.set(true);
    try {
      const pageResponse = await this.api.listRecordings(this.nextCursor(), this.pageSize());
      const sortedRecordings = [...pageResponse.items].sort((left, right) =>
        right.createdAt.localeCompare(left.createdAt)
      );
      this.recordings.set(sortedRecordings);
      this.totalRecordings.set(sortedRecordings.length);
      this.nextCursor.set(pageResponse.nextCursor);
      this.hasMoreRecordings.set(pageResponse.hasNext);
      
      const recordingSessions = this.buildRecordingSessions(this.recordings());
      this.recordingSessions.set(recordingSessions);

      const currentSessionId = this.selectedSessionId();
      const nextSessionId =
        recordingSessions.find((session) => session.sessionId === currentSessionId)?.sessionId ??
        recordingSessions[0]?.sessionId ??
        null;

      if (nextSessionId) {
        await this.selectSession(nextSessionId);
      } else {
        this.selectedSessionId.set(null);
        this.selectedTimeline.set(null);
        this.selectedSessionExport.set(null);
        this.selectedRecordingId.set(null);
        this.selectedPlaybackUrl.set(null);
        this.selectedSessionTranscript.set(null);
        this.activeTranscriptSegmentId.set(null);
        this.reviewClipNotice.set(null);
        this.transcriptSearchQuery.set('');
        this.transcriptReviewFilter.set('all');
        this.transcriptSearchResults.set(null);
        this.transcriptSearchRequestId++;
        this.clearExportPolling();
        this.clearTranscriptPolling();
        this.revokeSubtitleUrl();
      }
    } catch (error) {
      this.showError(this.api.explainError(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadTranscriptSmokeCheck(): Promise<void> {
    try {
      const response = await this.api.getTranscriptSmokeCheck();
      this.transcriptSmokeCheck.set(response);
      if (!this.selectedTranscriptEngine()) {
        this.selectedTranscriptEngine.set(response.engine);
      }
    } catch {
      this.transcriptSmokeCheck.set(null);
    }
  }

  private async loadTranscriptEngines(): Promise<void> {
    try {
      const engines = await this.api.getTranscriptEngines();
      this.transcriptEngineOptions.set(engines);
      if (!this.selectedTranscriptEngine()) {
        const defaultEngine = engines.find((engine) => engine.configuredDefault) ?? engines[0];
        this.selectedTranscriptEngine.set(defaultEngine?.key ?? null);
      }
    } catch {
      this.transcriptEngineOptions.set([]);
    }
  }

  async loadMoreRecordings(): Promise<void> {
    if (this.isLoading() || this.isLoadingMore() || !this.hasMoreRecordings()) {
      return;
    }

    this.isLoadingMore.set(true);
    try {
      const cursor = this.nextCursor();
      const pageResponse = await this.api.listRecordings(cursor, this.pageSize());
      
      const currentRecordings = this.recordings();
      const combinedRecordings = [...currentRecordings, ...pageResponse.items].sort((left, right) =>
        right.createdAt.localeCompare(left.createdAt)
      );
      
      this.recordings.set(combinedRecordings);
      this.totalRecordings.set(combinedRecordings.length);
      this.nextCursor.set(pageResponse.nextCursor);
      this.hasMoreRecordings.set(pageResponse.hasNext);
      
      const recordingSessions = this.buildRecordingSessions(combinedRecordings);
      this.recordingSessions.set(recordingSessions);
    } catch (error) {
      this.showError(this.api.explainError(error));
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  onScroll(event: Event): void {
    const target = event.target as HTMLElement;
    const scrollBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    
    // threshold of 100px from bottom to load more
    if (scrollBottom <= 100) {
      if (!this.isLoading() && !this.isLoadingMore() && this.hasMoreRecordings()) {
        void this.loadMoreRecordings();
      }
    }
  }

  async selectSession(sessionId: string): Promise<void> {
    this.selectedSessionId.set(sessionId);
    this.selectedTimeline.set(null);
    this.selectedSessionExport.set(null);
    this.selectedTimelineSegmentIndex.set(0);
    this.selectedRecordingId.set(null);
    this.selectedPlaybackUrl.set(null);
    this.selectedSessionTranscript.set(null);
    this.activeTranscriptSegmentId.set(null);
    this.reviewClipNotice.set(null);
    this.clearTranscriptSeekHighlight();
    this.showTranscriptSummary.set(false);
    this.transcriptSearchQuery.set('');
    this.transcriptReviewFilter.set('all');
    this.transcriptSearchResults.set(null);
    this.transcriptSearchRequestId++;
    this.clearExportPolling();
            this.revokeSubtitleUrl();
    this.isPlaybackLoading.set(true);
    this.isTranscriptLoading.set(true);
    this.isExportLoading.set(true);

    try {
      const [timeline, sessionTranscript, exportResponse] = await Promise.all([
        this.api.getSessionRecordingTimeline(sessionId),
        this.api.getSessionTranscript(sessionId),
        this.api.getSessionRecordingExport(sessionId)
      ]);
      if (this.selectedSessionId() !== sessionId) {
        return;
      }

      this.selectedTimeline.set(timeline);
      this.selectedSessionTranscript.set(sessionTranscript);
      this.selectedTranscriptEngine.set(
        this.resolvePreferredTranscriptEngine(this.transcriptEngineOptions(), sessionTranscript.engine, this.selectedTranscriptEngine())
      );
      this.selectedSessionExport.set(exportResponse);
      this.transcriptSearchResults.set(null);
      this.syncTimelineStatuses(timeline);
      this.scheduleExportPollingIfNeeded(exportResponse);

      if (!timeline.segments.length) {
        this.showError('');
        this.applyPendingInvestigationHitIfReady();
        return;
      }

      await this.activateTimelineSegment(0, false);
      await this.applyPendingInvestigationHitIfReady();
    } catch (error) {
      if (this.selectedSessionId() === sessionId) {
        const message = this.api.explainError(error);
        this.showError(message);
        this.showError(message);
      }
    } finally {
      if (this.selectedSessionId() === sessionId) {
        this.isPlaybackLoading.set(false);
        this.isTranscriptLoading.set(false);
        this.isExportLoading.set(false);
      }
    }
  }

  async selectTimelineSegment(index: number): Promise<void> {
    await this.activateTimelineSegment(index, false);
  }

  handlePlaybackEnded(): void {
    const timeline = this.selectedTimeline();
    if (!timeline) {
      return;
    }

    const nextIndex = this.selectedTimelineSegmentIndex() + 1;
    if (nextIndex < timeline.segments.length) {
      void this.activateTimelineSegment(nextIndex, true);
    }
  }

  handleVideoMetadataLoaded(): void {
    const player = this.timelinePlayer?.nativeElement;
    if (!player) {
      return;
    }

    if (this.pendingSeekSecondsWithinSegment != null) {
      player.currentTime = Math.max(0, this.pendingSeekSecondsWithinSegment);
      this.pendingSeekSecondsWithinSegment = null;
    }

    if (!this.pendingAutoplay) {
      this.handleVideoTimeUpdate();
      return;
    }

    this.pendingAutoplay = false;
    void player.play().catch(() => undefined);
    this.handleVideoTimeUpdate();
  }

  handleVideoTimeUpdate(): void {
    const player = this.timelinePlayer?.nativeElement;
    const activeSegment = this.activeTimelineSegment();
    const transcript = this.selectedSessionTranscript();
    if (!player || !activeSegment || !transcript?.segments.length) {
      this.activeTranscriptSegmentId.set(null);
      return;
    }

    const segmentSessionStartSeconds = this.segmentSessionStartSeconds(activeSegment);
    const currentSessionSecond = segmentSessionStartSeconds + player.currentTime;
    let fallbackTranscriptSegment: SessionTranscriptSegmentResponse | null = null;
    const activeTranscriptSegment = transcript.segments.find((segment) => {
      const start = Number.parseFloat(segment.startSeconds ?? '0');
      const end = Number.parseFloat(segment.endSeconds ?? '0');
      if (start <= currentSessionSecond) {
        fallbackTranscriptSegment = segment;
      }
      return currentSessionSecond >= start && currentSessionSecond <= end;
    }) ?? fallbackTranscriptSegment;

    this.activeTranscriptSegmentId.set(activeTranscriptSegment?.id ?? null);
  }

  async generateTranscript(): Promise<void> {
    const session = this.selectedSession();
    if (!session) {
      return;
    }

    const requestedEngine = this.selectedTranscriptEngine();
    this.isTranscriptGenerating.set(true);
        this.revokeSubtitleUrl();

    try {
      const [sessionTranscript, timeline] = await Promise.all([
        this.api.generateSessionTranscript(session.sessionId, requestedEngine),
        this.api.getSessionRecordingTimeline(session.sessionId)
      ]);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionTranscript.set(sessionTranscript);
        this.preserveRequestedTranscriptEngine(requestedEngine, sessionTranscript);
        this.showTranscriptSummary.set(false);
        this.transcriptSearchResults.set(null);
        this.selectedTimeline.set(timeline);
        this.syncTimelineStatuses(timeline);
        this.startTranscriptPolling(session.sessionId, requestedEngine);
        this.handleVideoTimeUpdate();
        const activeSegment = this.activeTimelineSegment();
        if (activeSegment) {
          await this.loadSubtitleTrackIfAvailable(activeSegment.recordingId, activeSegment.transcriptStatus);
        }
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
        this.showError(this.api.explainError(error));
      }
    } finally {
      this.isTranscriptGenerating.set(false);
    }
  }

  canSummarizeTranscript(): boolean {
    const transcript = this.selectedSessionTranscript();
    return !!(transcript && transcript.status === 'READY' && transcript.fullText?.trim());
  }

  async summarizeTranscript(): Promise<void> {
    const session = this.selectedSession();
    if (!session) {
      return;
    }

    this.isTranscriptSummaryLoading.set(true);
    try {
      const sessionTranscript = await this.api.summarizeSessionTranscript(session.sessionId);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionTranscript.set(sessionTranscript);
        this.showTranscriptSummary.set(true);
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
        this.showError(this.api.explainError(error));
      }
    } finally {
      this.isTranscriptSummaryLoading.set(false);
    }
  }

  openTranscriptReview(): void {
    const sessionId = this.selectedSessionId();
    if (!sessionId) {
      return;
    }

    void this.router.navigate(['/recordings', sessionId, 'transcript-review']);
  }

  copyReferenceNumber(): void {
    const ref = this.selectedSession()?.referenceNumber;
    if (ref) {
      this.snackBar.open(`Reference copied: ${ref}`, 'Dismiss', { duration: 3000 });
    }
  }

  async deleteSessionRecordings(session: RecordingSessionCard, event?: Event): Promise<void> {
    event?.stopPropagation();
    if (!session || this.isDeletingSessionRecordings()) {
      return;
    }

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '560px',
      maxWidth: '95vw',
      panelClass: 'recording-delete-dialog',
      data: {
        title: 'Delete Session Recording?',
        message: `This will hide the entire recording archive for ${session.referenceNumber || session.roomName}, including ${session.recordingCount} segment${session.recordingCount === 1 ? '' : 's'}, from playback, transcript review, archive search, and future exports.`,
        confirmLabel: 'Delete Recording',
        cancelLabel: 'Keep Recording',
        destructive: true,
        referenceNumber: session.referenceNumber || session.roomName,
        confirmationValue: session.referenceNumber || session.roomName
      }
    });

    const confirmed = await new Promise<boolean>((resolve) => {
      dialogRef.afterClosed().subscribe((result) => resolve(!!result));
    });
    if (!confirmed) {
      return;
    }

    this.isDeletingSessionRecordings.set(true);
    try {
      await this.api.deleteSessionRecordings(session.sessionId);
      this.snackBar.open('Recording archive deleted.', 'Close', { duration: 4000 });
      await this.loadRecordings();
    } catch (error) {
      this.showError(this.api.explainError(error));
    } finally {
      this.isDeletingSessionRecordings.set(false);
    }
  }

  async requestExportPackage(): Promise<void> {
    const session = this.selectedSession();
    if (!session) {
      return;
    }

    this.isExportRequesting.set(true);
        try {
      const exportResponse = await this.api.requestSessionRecordingExport(session.sessionId);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionExport.set(exportResponse);
        this.scheduleExportPollingIfNeeded(exportResponse);
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
      }
    } finally {
      this.isExportRequesting.set(false);
    }
  }

  async openInvestigationHit(hit: RecordingInvestigationSearchHitResponse): Promise<void> {
    this.pendingInvestigationHit = hit;
    if (this.selectedSessionId() === hit.sessionId && this.selectedTimeline()) {
      await this.applyPendingInvestigationHitIfReady();
      return;
    }
    await this.selectSession(hit.sessionId);
  }

  async retryFailedTranscriptIntervals(): Promise<void> {
    const session = this.selectedSession();
    if (!session) {
      return;
    }

    const requestedEngine = this.selectedTranscriptEngine();
    this.isRetryingFailedTranscript.set(true);
    
    try {
      const [sessionTranscript, timeline] = await Promise.all([
        this.api.retryFailedSessionTranscript(session.sessionId, requestedEngine),
        this.api.getSessionRecordingTimeline(session.sessionId)
      ]);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionTranscript.set(sessionTranscript);
        this.preserveRequestedTranscriptEngine(requestedEngine, sessionTranscript);
        this.selectedTimeline.set(timeline);
        this.transcriptReviewFilter.set('all');
        this.transcriptSearchResults.set(null);
        this.syncTimelineStatuses(timeline);
        this.startTranscriptPolling(session.sessionId, requestedEngine);
        this.handleVideoTimeUpdate();
        const activeSegment = this.activeTimelineSegment();
        if (activeSegment) {
          await this.loadSubtitleTrackIfAvailable(activeSegment.recordingId, activeSegment.transcriptStatus);
        }
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
      }
    } finally {
      this.isRetryingFailedTranscript.set(false);
    }
  }

  async reviewTranscriptRecording(recording: SessionTranscriptRecordingResponse): Promise<void> {
    const timeline = this.selectedTimeline();
    if (!timeline) {
      return;
    }
    const index = timeline.segments.findIndex((segment) => segment.recordingId === recording.recordingId);
    if (index >= 0) {
      this.reviewClipNotice.set({
        title: `Reviewing Clip ${this.transcriptRecordingOrdinal(recording)}`,
        message: recording.status === 'FAILED'
          ? recording.errorMessage?.trim() || 'Transcript generation failed for this clip.'
          : recording.status === 'READY'
            ? `Loaded ${recording.transcriptSegmentCount} transcript segment${recording.transcriptSegmentCount === 1 ? '' : 's'} for this clip.`
            : this.transcriptRecordingBody(recording)
      });
      await this.activateTimelineSegment(index, true);
      this.scrollToViewerPanel();
    }
  }

  async retryTranscriptRecording(recording: SessionTranscriptRecordingResponse): Promise<void> {
    const requestedEngine = this.selectedTranscriptEngine();
    this.isRetryingTranscriptRecordingId.set(recording.recordingId);
        try {
      await this.api.generateRecordingTranscript(recording.recordingId, requestedEngine);
      const sessionId = this.selectedSessionId();
      if (!sessionId) {
        return;
      }
      const [sessionTranscript, timeline] = await Promise.all([
        this.api.getSessionTranscript(sessionId),
        this.api.getSessionRecordingTimeline(sessionId)
      ]);
      if (this.selectedSessionId() === sessionId) {
        this.selectedSessionTranscript.set(sessionTranscript);
        this.preserveRequestedTranscriptEngine(requestedEngine, sessionTranscript);
        this.selectedTimeline.set(timeline);
        this.transcriptSearchResults.set(null);
        this.syncTimelineStatuses(timeline);
        this.startTranscriptPolling(sessionId, requestedEngine);
        this.handleVideoTimeUpdate();
        const activeSegment = this.activeTimelineSegment();
        if (activeSegment) {
          await this.loadSubtitleTrackIfAvailable(activeSegment.recordingId, activeSegment.transcriptStatus);
        }
      }
    } catch (error) {
    } finally {
      this.isRetryingTranscriptRecordingId.set(null);
    }
  }

  private async applyPendingInvestigationHitIfReady(): Promise<void> {
    const hit = this.pendingInvestigationHit;
    const timeline = this.selectedTimeline();
    if (!hit || !timeline || this.selectedSessionId() !== hit.sessionId) {
      return;
    }

    const segmentIndex = timeline.segments.findIndex((segment) => segment.recordingId === hit.recordingId);
    if (segmentIndex < 0) {
      this.pendingInvestigationHit = null;
      return;
    }

    const targetSegment = timeline.segments[segmentIndex];
    if (hit.transcriptStartSeconds != null) {
      const transcriptStartSeconds = Number.parseFloat(hit.transcriptStartSeconds);
      const segmentStartSeconds = this.segmentSessionStartSeconds(targetSegment);
      this.pendingSeekSecondsWithinSegment = Math.max(0, transcriptStartSeconds - segmentStartSeconds);
      await this.activateTimelineSegment(segmentIndex, true);
    } else if (segmentIndex !== this.selectedTimelineSegmentIndex()) {
      await this.activateTimelineSegment(segmentIndex, false);
    }

    this.pendingInvestigationHit = null;
  }

  private scheduleExportPollingIfNeeded(exportResponse: SessionRecordingExportResponse | null): void {
    this.clearExportPolling();
    if (!exportResponse?.status || this.selectedSessionId() !== exportResponse.sessionId) {
      return;
    }
    if (exportResponse.status !== 'PENDING' && exportResponse.status !== 'PROCESSING') {
      return;
    }
    this.exportPollHandle = setTimeout(() => {
      void this.refreshSelectedSessionExport(exportResponse.sessionId);
    }, 5000);
  }

  private clearExportPolling(): void {
    if (this.exportPollHandle != null) {
      clearTimeout(this.exportPollHandle);
      this.exportPollHandle = null;
    }
  }

  private resolvePreferredTranscriptEngine(
    engineOptions: TranscriptEngineOptionResponse[],
    transcriptEngine: string | null | undefined,
    currentSelection: string | null
  ): string | null {
    const availableEngines = new Set(engineOptions.filter((option) => option.ready).map((option) => option.key));
    if (currentSelection && availableEngines.has(currentSelection)) {
      return currentSelection;
    }
    if (transcriptEngine && availableEngines.has(transcriptEngine)) {
      return transcriptEngine;
    }
    return engineOptions.find((option) => option.ready && option.configuredDefault)?.key
      ?? engineOptions.find((option) => option.ready)?.key
      ?? null;
  }

  private preserveRequestedTranscriptEngine(
    requestedEngine: string | null,
    transcript: SessionTranscriptResponse | null
  ): void {
    this.selectedTranscriptEngine.set(
      this.resolvePreferredTranscriptEngine(this.transcriptEngineOptions(), transcript?.engine, requestedEngine)
    );
  }

  private startTranscriptPolling(sessionId: string, requestedEngine: string | null): void {
    this.clearTranscriptPolling();
    const pollToken = ++this.transcriptPollToken;
    void this.pollTranscriptUntilSettled(sessionId, pollToken, requestedEngine, 12);
  }

  private async pollTranscriptUntilSettled(
    sessionId: string,
    pollToken: number,
    requestedEngine: string | null,
    remainingAttempts: number
  ): Promise<void> {
    if (remainingAttempts <= 0 || pollToken !== this.transcriptPollToken) {
      return;
    }

    this.transcriptPollHandle = setTimeout(async () => {
      if (pollToken !== this.transcriptPollToken || this.selectedSessionId() !== sessionId) {
        return;
      }

      try {
        const [sessionTranscript, timeline] = await Promise.all([
          this.api.getSessionTranscript(sessionId),
          this.api.getSessionRecordingTimeline(sessionId)
        ]);
        if (this.selectedSessionId() !== sessionId) {
          return;
        }
        this.selectedSessionTranscript.set(sessionTranscript);
        this.preserveRequestedTranscriptEngine(requestedEngine, sessionTranscript);
        this.selectedTimeline.set(timeline);
        this.syncTimelineStatuses(timeline);
        this.handleVideoTimeUpdate();
      } catch (error) {
        if (this.selectedSessionId() === sessionId) {
          this.showError(this.api.explainError(error));
        }
        this.clearTranscriptPolling();
        return;
      }

      const transcript = this.selectedSessionTranscript();
      if (!transcript || !this.isTranscriptPending(transcript.status)) {
        this.clearTranscriptPolling();
        return;
      }

      void this.pollTranscriptUntilSettled(sessionId, pollToken, requestedEngine, remainingAttempts - 1);
    }, 2000);
  }

  private isTranscriptPending(status: RecordingTranscriptStatus | null | undefined): boolean {
    return status === 'PENDING' || status === 'PROCESSING';
  }

  private clearTranscriptPolling(): void {
    if (this.transcriptPollHandle != null) {
      clearTimeout(this.transcriptPollHandle);
      this.transcriptPollHandle = null;
    }
  }

  private async refreshSelectedSessionExport(sessionId: string): Promise<void> {
    if (this.selectedSessionId() !== sessionId) {
      return;
    }
    try {
      const exportResponse = await this.api.getSessionRecordingExport(sessionId);
      if (this.selectedSessionId() !== sessionId) {
        return;
      }
      this.selectedSessionExport.set(exportResponse);
      this.scheduleExportPollingIfNeeded(exportResponse);
    } catch (error) {
      if (this.selectedSessionId() === sessionId) {
      }
    }
  }

  private async activateTimelineSegment(index: number, autoplay: boolean): Promise<void> {
    const timeline = this.selectedTimeline();
    if (!timeline) {
      return;
    }

    const segment = timeline.segments[index];
    if (!segment) {
      return;
    }

    this.selectedTimelineSegmentIndex.set(index);
    this.selectedRecordingId.set(segment.recordingId);
    this.selectedPlaybackUrl.set(segment.playbackUrl);
            this.revokeSubtitleUrl();
    this.pendingAutoplay = autoplay;

    try {
      await this.loadSubtitleTrackIfAvailable(segment.recordingId, segment.transcriptStatus);
      this.handleVideoTimeUpdate();
    } catch (error) {
      if (this.selectedRecordingId() === segment.recordingId) {
      }
    }
  }

  async seekToTranscriptSegment(segment: SessionTranscriptSegmentResponse): Promise<void> {
    this.triggerTranscriptSeekHighlight();
    const timeline = this.selectedTimeline();
    if (!timeline?.segments.length) {
      return;
    }

    const targetSessionSecond = Number.parseFloat(segment.startSeconds ?? '0');
    const targetIndex = timeline.segments.findIndex((timelineSegment) => timelineSegment.recordingId === segment.recordingId);
    const target = targetIndex >= 0
      ? {
          index: targetIndex,
          segmentSeekSeconds: Math.max(0, targetSessionSecond - this.segmentSessionStartSeconds(timeline.segments[targetIndex]))
        }
      : this.findTimelineSegmentForSessionSecond(targetSessionSecond);

    const currentSegment = this.activeTimelineSegment();
    if (target && currentSegment && target.index === this.selectedTimelineSegmentIndex()) {
      const player = this.timelinePlayer?.nativeElement;
      if (player) {
        player.currentTime = Math.max(0, target.segmentSeekSeconds);
        void player.play().catch(() => undefined);
        this.handleVideoTimeUpdate();
      }
      return;
    }

    if (!target) {
      return;
    }

    this.pendingSeekSecondsWithinSegment = target.segmentSeekSeconds;
    await this.activateTimelineSegment(target.index, true);
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

  private triggerTranscriptSeekHighlight(): void {
    if (this.transcriptSeekHighlightHandle != null) {
      clearTimeout(this.transcriptSeekHighlightHandle);
    }

    this.isTranscriptSeekHighlight.set(true);
    this.transcriptSeekHighlightHandle = setTimeout(() => {
      this.isTranscriptSeekHighlight.set(false);
      this.transcriptSeekHighlightHandle = null;
    }, 1800);
  }

  private clearTranscriptSeekHighlight(): void {
    if (this.transcriptSeekHighlightHandle != null) {
      clearTimeout(this.transcriptSeekHighlightHandle);
      this.transcriptSeekHighlightHandle = null;
    }
    this.isTranscriptSeekHighlight.set(false);
  }

  private findTimelineSegmentForSessionSecond(targetSessionSecond: number): { index: number; segmentSeekSeconds: number } | null {
    const timeline = this.selectedTimeline();
    if (!timeline?.segments.length) {
      return null;
    }

    for (let index = 0; index < timeline.segments.length; index++) {
      const segment = timeline.segments[index];
      const startSeconds = this.segmentSessionStartSeconds(segment);
      const endSeconds = this.segmentSessionEndSeconds(segment);
      if (targetSessionSecond >= startSeconds && targetSessionSecond <= endSeconds) {
        return {
          index,
          segmentSeekSeconds: Math.max(0, targetSessionSecond - startSeconds)
        };
      }
    }

    const lastIndex = timeline.segments.length - 1;
    return {
      index: lastIndex,
      segmentSeekSeconds: 0
    };
  }

  private segmentSessionStartSeconds(segment: SessionRecordingTimelineSegmentResponse): number {
    if (segment.sessionElapsedStartMs != null) {
      return segment.sessionElapsedStartMs / 1000;
    }

    const timeline = this.selectedTimeline();
    if (!timeline) {
      return 0;
    }

    const segmentIndex = timeline.segments.findIndex((entry) => entry.recordingId === segment.recordingId);
    let offsetSeconds = 0;
    for (let index = 0; index < segmentIndex; index++) {
      offsetSeconds += timeline.segments[index].durationSeconds ?? 0;
    }
    return offsetSeconds;
  }

  private segmentSessionEndSeconds(segment: SessionRecordingTimelineSegmentResponse): number {
    if (segment.sessionElapsedEndMs != null) {
      return segment.sessionElapsedEndMs / 1000;
    }
    return this.segmentSessionStartSeconds(segment) + (segment.durationSeconds ?? 0);
  }

  private syncTimelineStatuses(timeline: SessionRecordingTimelineResponse): void {
    const statusesByRecordingId = new Map(
      timeline.segments.map((segment) => [segment.recordingId, segment.transcriptStatus ?? null] as const)
    );
    const updatedRecordings = this.recordings().map((recording) => ({
      ...recording,
      transcriptStatus: statusesByRecordingId.get(recording.id) ?? recording.transcriptStatus ?? null
    }));
    this.recordings.set(updatedRecordings);
    this.recordingSessions.set(this.buildRecordingSessions(updatedRecordings));
  }

  private buildRecordingSessions(recordings: RecordingResponse[]): RecordingSessionCard[] {
    const sessionGroups = new Map<string, RecordingResponse[]>();
    for (const recording of recordings) {
      const sessionRecordings = sessionGroups.get(recording.sessionId) ?? [];
      sessionRecordings.push(recording);
      sessionGroups.set(recording.sessionId, sessionRecordings);
    }

    return [...sessionGroups.entries()]
      .map(([sessionId, sessionRecordings]) => {
        const sortedSessionRecordings = [...sessionRecordings].sort((left, right) =>
          right.createdAt.localeCompare(left.createdAt)
        );
        const latestRecording = sortedSessionRecordings[0];
        return {
          sessionId,
          workerName: latestRecording.workerName,
          roomName: latestRecording.roomName,
          referenceNumber: latestRecording.referenceNumber,
          latestCreatedAt: latestRecording.createdAt,
          recordingCount: sortedSessionRecordings.length,
          approxDurationSeconds: (() => {
            let maxEndMs = 0;
            let sumDuration = 0;
            let hasEndMs = false;
            for (const recording of sortedSessionRecordings) {
              const endMs = recording.metadata?.sessionElapsedEndMs;
              if (endMs != null) {
                hasEndMs = true;
                maxEndMs = Math.max(maxEndMs, endMs);
              }
              sumDuration += recording.durationSeconds ?? 0;
            }
            return hasEndMs && maxEndMs > 0 ? Math.round(maxEndMs / 1000) : sumDuration;
          })(),
          latitude: latestRecording.metadata?.latitude ?? null,
          longitude: latestRecording.metadata?.longitude ?? null,
          transcriptStatus: this.sessionTranscriptStatus(sortedSessionRecordings)
        } satisfies RecordingSessionCard;
      })
      .sort((left, right) => right.latestCreatedAt.localeCompare(left.latestCreatedAt));
  }

  private sessionTranscriptStatus(recordings: RecordingResponse[]): RecordingTranscriptStatus | null {
    const statuses = recordings.map((recording) => recording.transcriptStatus).filter(Boolean) as RecordingTranscriptStatus[];
    if (!statuses.length) {
      return null;
    }
    if (statuses.includes('PROCESSING') || statuses.includes('PENDING')) {
      return statuses.includes('PROCESSING') ? 'PROCESSING' : 'PENDING';
    }
    if (statuses.includes('FAILED')) {
      return 'FAILED';
    }
    if (statuses.includes('READY')) {
      return 'READY';
    }
    return 'NOT_REQUESTED';
  }

  private parseConfidence(confidence: string | null | undefined): number | null {
    if (!confidence) {
      return null;
    }
    const parsed = Number.parseFloat(confidence);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private scrollToViewerPanel(): void {
    this.viewerPanel?.nativeElement.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    });
  }
}
