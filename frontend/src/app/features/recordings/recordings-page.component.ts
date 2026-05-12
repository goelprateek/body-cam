import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { OperatorApiService } from '@features/operations/operator-api.service';
import {
  RecordingInvestigationSearchHitResponse,
  RecordingInvestigationSearchResponse,
  RecordingResponse,
  SessionRecordingIntegrityStatus,
  SessionRecordingExportResponse,
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
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  template: `
    <section class="page workspace-grid">
      <mat-card class="panel section-panel glass-panel" appearance="outlined">
        <div class="section-head premium-head">
          <div class="head-title">
            <h2>Session Archive</h2>
            <span class="subtle-text live-count">{{ recordingSessions().length }}</span>
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

        <div class="archive-search-row">
          <input
            type="search"
            class="transcript-search-input"
            placeholder="Search sessions, references, rooms, or transcript text"
            [value]="investigationSearchQuery()"
            (input)="updateInvestigationSearch(($any($event.target)).value)"
          />
          @if (investigationSearchQuery()) {
            <button
              mat-button
              type="button"
              class="transcript-search-clear"
              (click)="clearInvestigationSearch()"
            >
              Clear
            </button>
          }
        </div>

        @if (isInvestigationSearching()) {
          <div class="subtle-text transcript-search-meta">Searching archive...</div>
        } @else if (investigationSearchQuery()) {
          <div class="subtle-text transcript-search-meta">
            {{ investigationSearchResults()?.totalMatches || 0 }} investigation match{{ (investigationSearchResults()?.totalMatches || 0) === 1 ? '' : 'es' }}
          </div>
        }

        @if (investigationSearchResults()?.hits?.length) {
          <div class="investigation-results premium-scroll">
            @for (hit of investigationSearchResults()?.hits || []; track hit.sessionId + ':' + hit.recordingId + ':' + (hit.transcriptStartSeconds || hit.matchedField)) {
              <button type="button" class="investigation-hit" (click)="openInvestigationHit(hit)">
                <span class="investigation-hit-head">
                  <strong>{{ hit.workerName }}</strong>
                  <span class="subtle-text">{{ investigationFieldLabel(hit.matchedField) }}</span>
                </span>
                <span class="investigation-hit-meta">{{ hit.referenceNumber }} | {{ hit.roomName }}</span>
                <span class="investigation-hit-snippet">{{ hit.snippet }}</span>
              </button>
            }
          </div>
        } @else if (investigationSearchQuery() && !isInvestigationSearching()) {
          <div class="empty-state transcript-empty">
            <strong>No archive matches</strong>
            <span>Try a different term for transcript, reference, room, or worker search.</span>
          </div>
        }

        <div class="recording-list session-list-scroll premium-scroll">
          @for (session of visibleRecordingSessions(); track session.sessionId) {
            <mat-card
              class="archive-card premium-card"
              appearance="outlined"
              [class.archive-card-selected]="session.sessionId === selectedSessionId()"
              (click)="selectSession(session.sessionId)"
            >
              <div class="archive-card-inner">
                <div class="archive-thumb">
                  <div class="thumb-overlay">
                    <mat-icon class="play-icon">play_arrow</mat-icon>
                  </div>
                  <div class="thumb-placeholder">
                    <mat-icon>video_library</mat-icon>
                  </div>
                  <div class="duration-pill">{{ session.recordingCount }} SEG</div>
                </div>

                <div class="archive-details">
                  <div class="archive-header-group">
                    <div class="archive-title-stack">
                      <strong class="archive-worker">{{ session.workerName }}</strong>
                      <span class="archive-room">{{ session.roomName }}</span>
                    </div>
                    <span
                      class="transcript-badge"
                      [class]="transcriptPillClass(session.transcriptStatus)"
                      [matTooltip]="transcriptLabel(session.transcriptStatus)"
                    >
                      <mat-icon class="transcript-icon">{{ transcriptIcon(session.transcriptStatus) }}</mat-icon>
                    </span>
                  </div>

                  <div class="archive-meta-row">
                    @if (session.referenceNumber) {
                      <div class="meta-pill" matTooltip="Reference Number">
                        <mat-icon>tag</mat-icon>
                        <span>{{ session.referenceNumber }}</span>
                      </div>
                    }
                    <div class="meta-pill" matTooltip="Session Duration">
                      <mat-icon>schedule</mat-icon>
                      <span>{{ formatDurationFromSeconds(session.approxDurationSeconds) }}</span>
                    </div>
                    <div class="meta-pill">
                      <mat-icon>event</mat-icon>
                      <span>{{ session.latestCreatedAt | date: 'MMM d, HH:mm' }}</span>
                    </div>
                    @if (session.latitude && session.longitude) {
                      <div class="meta-pill" [matTooltip]="formatSessionCoordinates(session)">
                        <mat-icon>place</mat-icon>
                        <span class="meta-truncate">{{ session.latitude | number:'1.2-2' }}, {{ session.longitude | number:'1.2-2' }}</span>
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
        <mat-card #viewerPanel class="panel viewer-panel glass-panel" appearance="outlined">
          <div class="section-head premium-head viewer-head">
            <div class="viewer-head-copy">
              <h2>{{ selectedSession()?.workerName || 'Playback' }}</h2>
              <p class="viewer-caption">{{ selectedSessionCaption() }}</p>
            </div>
            <div class="viewer-status">
              @if (selectedTimeline()) {
                <span class="status-pill premium-status-pill">
                  {{ selectedTimeline()?.segments?.length || 0 }} SEGMENTS
                </span>
              }
              @if (selectedSessionExport()?.status) {
                <span class="status-pill premium-status-pill">
                  EXPORT {{ selectedSessionExport()?.status }}
                </span>
              }
            </div>
          </div>

          @if (reviewClipNotice()) {
            <div class="notice">
              <strong>{{ reviewClipNotice()?.title }}</strong>
              <span>{{ reviewClipNotice()?.message }}</span>
            </div>
          }

          @if (selectedTimeline()) {
            <div class="timeline-summary-row">
              <div class="timeline-summary-pill">
                <mat-icon>schedule</mat-icon>
                <span>{{ formatDurationMs(selectedTimeline()?.totalDurationMs) }}</span>
              </div>
              <div class="timeline-summary-pill" [class]="integrityPillClass(selectedTimeline()?.integrityStatus)">
                <mat-icon>{{ integrityIcon(selectedTimeline()?.integrityStatus) }}</mat-icon>
                <span>{{ integrityLabel(selectedTimeline()?.integrityStatus) }}</span>
              </div>
              <div class="timeline-summary-pill">
                <mat-icon>queue_play_next</mat-icon>
                <span>Segment {{ selectedTimelineSegmentOrdinal() }} of {{ selectedTimeline()?.segments?.length || 0 }}</span>
              </div>
              @if (selectedTimeline()?.hasTimelineGaps) {
                <div class="timeline-summary-pill timeline-summary-pill-warning">
                  <mat-icon>warning_amber</mat-icon>
                  <span>Timeline has missing or out-of-order uploads</span>
                </div>
              }
            </div>

            <div class="timeline-summary-row timeline-summary-row-compact">
              <div class="timeline-summary-pill">
                <mat-icon>inventory_2</mat-icon>
                <span>{{ exportStatusLabel(selectedSessionExport()) }}</span>
              </div>
              @if (selectedSessionExport()?.artifactCount) {
                <div class="timeline-summary-pill">
                  <mat-icon>folder_zip</mat-icon>
                  <span>{{ selectedSessionExport()?.artifactCount }} artifacts</span>
                </div>
              }
              @if (selectedSessionExport()?.packageSizeBytes) {
                <div class="timeline-summary-pill">
                  <mat-icon>save_alt</mat-icon>
                  <span>{{ formatBytes(selectedSessionExport()?.packageSizeBytes) }}</span>
                </div>
              }
              <div class="transcript-action-row">
                <button
                  mat-stroked-button
                  class="premium-btn premium-btn-secondary"
                  type="button"
                  (click)="requestExportPackage()"
                  [disabled]="isExportRequesting() || isExportLoading()"
                >
                  {{ selectedSessionExport()?.status === 'READY' ? 'Refresh Export Package' : isExportRequesting() ? 'Queueing Export...' : 'Request Export Package' }}
                </button>
                @if (selectedSessionExport()?.downloadUrl) {
                  <a
                    mat-flat-button
                    class="premium-btn"
                    [href]="selectedSessionExport()?.downloadUrl || ''"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Download Package
                  </a>
                }
              </div>
            </div>
          }

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
                  #timelinePlayer
                  class="replay-video"
                  [src]="selectedPlaybackUrl() || ''"
                  controls
                  preload="metadata"
                  (ended)="handlePlaybackEnded()"
                  (loadedmetadata)="handleVideoMetadataLoaded()"
                  (timeupdate)="handleVideoTimeUpdate()"
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
                  <strong>Select a session to play</strong>
                </div>
              }
            </div>

            @if (selectedTimeline()?.segments?.length) {
              <div class="timeline-segment-strip premium-scroll">
                @for (segment of selectedTimeline()?.segments || []; track segment.recordingId; let i = $index) {
                  <button
                    type="button"
                    class="timeline-segment-btn"
                    [class.timeline-segment-btn-active]="i === selectedTimelineSegmentIndex()"
                    [class.timeline-segment-btn-warning]="segmentWarning(segment)"
                    (click)="selectTimelineSegment(i)"
                  >
                    <span class="timeline-segment-label">{{ timelineSegmentLabel(segment, i) }}</span>
                    <span class="timeline-segment-time">{{ formatTimelineSegmentDuration(segment) }}</span>
                  </button>
                }
              </div>
            }
          </div>
        </mat-card>

        <mat-card class="panel glass-panel transcript-panel" appearance="outlined">
          <div class="section-head premium-head">
            <div class="viewer-head-copy">
              <h2>Transcript</h2>
              <p class="viewer-caption">
                Transcript generation follows the full session timeline while subtitles remain segment-specific during playback.
              </p>
            </div>
            @if (selectedSession()) {
              <div class="transcript-action-row">
                @if (hasRetryableTranscriptIntervals()) {
                  <button
                    mat-stroked-button
                    class="premium-btn premium-btn-secondary"
                    type="button"
                    (click)="retryFailedTranscriptIntervals()"
                    [disabled]="isRetryingFailedTranscript() || isTranscriptGenerating() || isTranscriptLoading()"
                  >
                    {{ isRetryingFailedTranscript() ? 'Retrying Failed Intervals...' : 'Retry Failed Or Missing' }}
                  </button>
                }
                <button
                  mat-flat-button
                  class="premium-btn"
                  type="button"
                  (click)="generateTranscript()"
                  [disabled]="isTranscriptGenerating() || isTranscriptLoading() || isRetryingFailedTranscript()"
                >
                  {{ selectedSessionTranscript()?.status === 'FAILED'
                    ? 'Retry Transcript'
                    : selectedSessionTranscript()?.status === 'READY'
                      ? 'Regenerate Session Transcript'
                      : 'Generate Session Transcript' }}
                </button>
              </div>
            }
          </div>

          @if (selectedSessionTranscript()) {
            <div class="timeline-summary-row timeline-summary-row-compact">
              <div class="timeline-summary-pill">
                <mat-icon>article</mat-icon>
                <span>{{ selectedSessionTranscript()?.readyRecordings || 0 }} / {{ selectedSessionTranscript()?.totalRecordings || 0 }} clips ready</span>
              </div>
              @if ((selectedSessionTranscript()?.failedRecordings || 0) > 0) {
                <div class="timeline-summary-pill timeline-summary-pill-warning">
                  <mat-icon>error_outline</mat-icon>
                  <span>{{ selectedSessionTranscript()?.failedRecordings }} failed</span>
                </div>
              }
              @if ((selectedSessionTranscript()?.processingRecordings || 0) > 0 || (selectedSessionTranscript()?.pendingRecordings || 0) > 0) {
                <div class="timeline-summary-pill">
                  <mat-icon>hourglass_top</mat-icon>
                  <span>{{ (selectedSessionTranscript()?.processingRecordings || 0) + (selectedSessionTranscript()?.pendingRecordings || 0) }} pending</span>
                </div>
              }
              @if ((selectedSessionTranscript()?.notRequestedRecordings || 0) > 0) {
                <div class="timeline-summary-pill timeline-summary-pill-warning">
                  <mat-icon>playlist_remove</mat-icon>
                  <span>{{ selectedSessionTranscript()?.notRequestedRecordings }} missing transcript</span>
                </div>
              }
              @if (lowConfidenceTranscriptCount() > 0) {
                <div class="timeline-summary-pill timeline-summary-pill-warning">
                  <mat-icon>hearing_disabled</mat-icon>
                  <span>{{ lowConfidenceTranscriptCount() }} low-confidence segments</span>
                </div>
              }
            </div>
          }

          @if (isTranscriptLoading()) {
            <mat-progress-bar mode="indeterminate" class="premium-progress"></mat-progress-bar>
          }

          @if (transcriptError()) {
            <div class="notice notice-error">{{ transcriptError() }}</div>
          }

          @if (selectedSessionTranscript()) {
            <div class="transcript-status-row">
              <span class="transcript-pill transcript-pill-detail" [class]="transcriptPillClass(selectedSessionTranscript()?.status)">
                {{ transcriptLabel(selectedSessionTranscript()?.status) }}
              </span>
              @if (selectedSessionTranscript()?.engine) {
                <span class="subtle-text">Engine: {{ selectedSessionTranscript()?.engine }}</span>
              }
              @if (selectedSessionTranscript()?.model) {
                <span class="subtle-text">Model: {{ selectedSessionTranscript()?.model }}</span>
              }
            </div>

            @if (selectedSessionTranscript()?.recordings?.length) {
              <div class="transcript-filter-row">
                <button type="button" class="transcript-filter-chip" [class.transcript-filter-chip-active]="transcriptReviewFilter() === 'all'" (click)="setTranscriptReviewFilter('all')">All Clips</button>
                <button type="button" class="transcript-filter-chip" [class.transcript-filter-chip-active]="transcriptReviewFilter() === 'failed'" (click)="setTranscriptReviewFilter('failed')">Failed</button>
                <button type="button" class="transcript-filter-chip" [class.transcript-filter-chip-active]="transcriptReviewFilter() === 'missing'" (click)="setTranscriptReviewFilter('missing')">Missing</button>
                <button type="button" class="transcript-filter-chip" [class.transcript-filter-chip-active]="transcriptReviewFilter() === 'pending'" (click)="setTranscriptReviewFilter('pending')">Pending</button>
                <button type="button" class="transcript-filter-chip" [class.transcript-filter-chip-active]="transcriptReviewFilter() === 'low-confidence'" (click)="setTranscriptReviewFilter('low-confidence')">Low Confidence</button>
              </div>
            }

            @if (selectedSessionTranscript()?.segments?.length) {
              <div class="transcript-search-row">
                <input
                  type="search"
                  class="transcript-search-input"
                  placeholder="Search within this session transcript"
                  [value]="transcriptSearchQuery()"
                  (input)="updateTranscriptSearch(($any($event.target)).value)"
                />
                @if (transcriptSearchQuery()) {
                  <button
                    mat-button
                    type="button"
                    class="transcript-search-clear"
                    (click)="clearTranscriptSearch()"
                  >
                    Clear
                  </button>
                }
              </div>

              @if (isTranscriptSearching()) {
                <div class="subtle-text transcript-search-meta">
                  Searching session transcript...
                </div>
              } @else if (transcriptSearchQuery()) {
                <div class="subtle-text transcript-search-meta">
                  {{ filteredTranscriptSegments().length }} backend match{{ filteredTranscriptSegments().length === 1 ? '' : 'es' }}
                </div>
              }
            }

            @if (filteredTranscriptReviewRecordings().length) {
              <div class="transcript-review-list">
                @for (recording of filteredTranscriptReviewRecordings(); track recording.recordingId) {
                  <div class="transcript-review-card" [class.transcript-review-card-warning]="recording.status === 'FAILED' || recording.status === 'NOT_REQUESTED'">
                    <div class="transcript-review-head">
                      <span class="transcript-pill transcript-pill-detail" [class]="transcriptPillClass(recording.status)">
                        {{ transcriptLabel(recording.status) }}
                      </span>
                      <span class="subtle-text">Clip {{ transcriptRecordingOrdinal(recording) }} - {{ formatTranscriptRecordingWindow(recording) }}</span>
                    </div>
                    <div class="transcript-review-body">
                      <span class="subtle-text">
                        {{ transcriptRecordingBody(recording) }}
                      </span>
                        @if (recording.errorMessage && recording.status !== 'FAILED') {
                          <span class="transcript-review-error">{{ recording.errorMessage }}</span>
                        }
                    </div>
                    <div class="transcript-review-actions">
                      <button mat-button type="button" (click)="reviewTranscriptRecording(recording)">
                        Review Clip
                      </button>
                      @if (canRetryTranscriptRecording(recording)) {
                        <button
                          mat-button
                          type="button"
                          (click)="retryTranscriptRecording(recording)"
                          [disabled]="isRetryingTranscriptRecordingId() === recording.recordingId"
                        >
                          {{ isRetryingTranscriptRecordingId() === recording.recordingId ? 'Retrying...' : 'Retry Clip' }}
                        </button>
                      }
                    </div>
                  </div>
                }
              </div>
            }

            @switch (selectedSessionTranscript()?.status) {
              @case ('NOT_REQUESTED') {
                <div class="empty-state transcript-empty">
                  <strong>No transcript yet</strong>
                  <span>Generate one for the full session when the operator needs it.</span>
                </div>
              }
              @case ('FAILED') {
                <div class="notice notice-error">
                  {{ selectedSessionTranscript()?.errorMessage || 'Session transcript generation failed.' }}
                </div>
              }
              @default {
                @if (shouldShowTranscriptFullText() && selectedSessionTranscript()?.fullText) {
                  <div class="transcript-full-text">
                    {{ selectedSessionTranscript()?.fullText }}
                  </div>
                }

                @if (shouldShowTranscriptSegments()) {
                  @if (filteredTranscriptSegments().length) {
                    <div class="transcript-segments">
                      @for (segment of filteredTranscriptSegments(); track segment.id || segment.segmentIndex) {
                        <button
                          type="button"
                          class="transcript-segment transcript-segment-button"
                          [class.transcript-segment-active]="segment.id === activeTranscriptSegmentId()"
                          [class.transcript-segment-low-confidence]="isLowConfidenceSegment(segment)"
                          (click)="seekToTranscriptSegment(segment)"
                        >
                          <span class="segment-time">{{ formatSegmentTime(segment) }}</span>
                          <span class="segment-copy">
                            <span class="segment-text">{{ segment.text }}</span>
                            <span class="segment-meta-row">
                              @if (segment.confidence) {
                                <span
                                  class="segment-confidence"
                                  [class.segment-confidence-low]="isLowConfidenceSegment(segment)"
                                >
                                  Confidence {{ formatConfidence(segment.confidence) }}
                                </span>
                              }
                              @if (segment.recordingSequence != null) {
                                <span class="segment-recording-ref">
                                  Clip {{ segment.recordingSequence + 1 }}
                                </span>
                              }
                            </span>
                          </span>
                        </button>
                      }
                    </div>
                  } @else if (transcriptSearchQuery()) {
                    <div class="empty-state transcript-empty">
                      <strong>No transcript matches</strong>
                      <span>Try a different search term for this session.</span>
                    </div>
                  } @else if (transcriptReviewFilter() !== 'all') {
                    <div class="empty-state transcript-empty">
                      <strong>No transcript text for this filter</strong>
                      <span>{{ transcriptFilterEmptyMessage() }}</span>
                    </div>
                  }
                } @else if (selectedSessionTranscript()?.status === 'PROCESSING' || selectedSessionTranscript()?.status === 'PENDING') {
                  <div class="empty-state transcript-empty">
                    <strong>Transcript request accepted</strong>
                    <span>The backend is preparing transcript content for the session timeline.</span>
                  </div>
                }
              }
            }
          } @else {
            <div class="empty-state transcript-empty">
              <strong>Select a session</strong>
              <span>Session transcript details will appear here with playback.</span>
            </div>
          }
        </mat-card>
      </section>
    </section>
  `
})
export class RecordingsPageComponent implements OnDestroy {
  readonly api = inject(OperatorApiService);

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
  readonly activeTranscriptSegmentId = signal<string | null>(null);
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
  readonly isRetryingFailedTranscript = signal(false);
  readonly isRetryingTranscriptRecordingId = signal<string | null>(null);
  readonly pageError = signal<string | null>(null);
  readonly playbackError = signal<string | null>(null);
  readonly transcriptError = signal<string | null>(null);

  private pendingAutoplay = false;
  private pendingSeekSecondsWithinSegment: number | null = null;
  private transcriptSearchRequestId = 0;
  private investigationSearchRequestId = 0;
  private exportPollHandle: ReturnType<typeof setTimeout> | null = null;
  private pendingInvestigationHit: RecordingInvestigationSearchHitResponse | null = null;

  constructor() {
    void this.loadRecordings();
  }

  ngOnDestroy(): void {
    this.clearExportPolling();
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
    const timeline = this.selectedTimeline();
    if (!session) {
      return 'Select a session recording to play';
    }

    const captionParts = [session.roomName];
    if (session.referenceNumber) {
      captionParts.push(`Ref ${session.referenceNumber}`);
    }
    if (timeline?.totalDurationMs != null) {
      captionParts.push(this.formatDurationMs(timeline.totalDurationMs));
    }
    return captionParts.join(' | ');
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
    this.pageError.set(null);
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
        this.pageError.set(this.api.explainError(error));
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
          return 'Transcript generation is currently running for this clip.';
      case 'PENDING':
        return 'Transcript generation is queued for this clip.';
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
    this.transcriptError.set(null);
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
        this.transcriptError.set(this.api.explainError(error));
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
    this.pageError.set(null);
    try {
      const recordings = await this.api.listRecordings();
      const sortedRecordings = [...recordings].sort((left, right) =>
        right.createdAt.localeCompare(left.createdAt)
      );
      this.recordings.set(sortedRecordings);
      const recordingSessions = this.buildRecordingSessions(sortedRecordings);
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
        this.revokeSubtitleUrl();
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isLoading.set(false);
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
    this.transcriptSearchQuery.set('');
    this.transcriptReviewFilter.set('all');
    this.transcriptSearchResults.set(null);
    this.transcriptSearchRequestId++;
    this.clearExportPolling();
    this.playbackError.set(null);
    this.transcriptError.set(null);
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
      this.selectedSessionExport.set(exportResponse);
      this.transcriptSearchResults.set(null);
      this.syncTimelineStatuses(timeline);
      this.scheduleExportPollingIfNeeded(exportResponse);

      if (!timeline.segments.length) {
        this.playbackError.set('No uploaded segments are available for this session yet.');
        this.applyPendingInvestigationHitIfReady();
        return;
      }

      await this.activateTimelineSegment(0, false);
      await this.applyPendingInvestigationHitIfReady();
    } catch (error) {
      if (this.selectedSessionId() === sessionId) {
        const message = this.api.explainError(error);
        this.playbackError.set(message);
        this.transcriptError.set(message);
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

    this.isTranscriptGenerating.set(true);
    this.transcriptError.set(null);
    this.revokeSubtitleUrl();

    try {
      const [sessionTranscript, timeline] = await Promise.all([
        this.api.generateSessionTranscript(session.sessionId),
        this.api.getSessionRecordingTimeline(session.sessionId)
      ]);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionTranscript.set(sessionTranscript);
        this.transcriptSearchResults.set(null);
        this.selectedTimeline.set(timeline);
        this.syncTimelineStatuses(timeline);
        this.handleVideoTimeUpdate();
        const activeSegment = this.activeTimelineSegment();
        if (activeSegment) {
          await this.loadSubtitleTrackIfAvailable(activeSegment.recordingId, activeSegment.transcriptStatus);
        }
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
        this.transcriptError.set(this.api.explainError(error));
      }
    } finally {
      this.isTranscriptGenerating.set(false);
    }
  }

  async requestExportPackage(): Promise<void> {
    const session = this.selectedSession();
    if (!session) {
      return;
    }

    this.isExportRequesting.set(true);
    this.pageError.set(null);
    try {
      const exportResponse = await this.api.requestSessionRecordingExport(session.sessionId);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionExport.set(exportResponse);
        this.scheduleExportPollingIfNeeded(exportResponse);
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
        this.pageError.set(this.api.explainError(error));
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

    this.isRetryingFailedTranscript.set(true);
    this.transcriptError.set(null);

    try {
      const [sessionTranscript, timeline] = await Promise.all([
        this.api.retryFailedSessionTranscript(session.sessionId),
        this.api.getSessionRecordingTimeline(session.sessionId)
      ]);
      if (this.selectedSessionId() === session.sessionId) {
        this.selectedSessionTranscript.set(sessionTranscript);
        this.selectedTimeline.set(timeline);
        this.transcriptReviewFilter.set('all');
        this.transcriptSearchResults.set(null);
        this.syncTimelineStatuses(timeline);
        this.handleVideoTimeUpdate();
        const activeSegment = this.activeTimelineSegment();
        if (activeSegment) {
          await this.loadSubtitleTrackIfAvailable(activeSegment.recordingId, activeSegment.transcriptStatus);
        }
      }
    } catch (error) {
      if (this.selectedSessionId() === session.sessionId) {
        this.transcriptError.set(this.api.explainError(error));
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
    this.isRetryingTranscriptRecordingId.set(recording.recordingId);
    this.transcriptError.set(null);
    try {
      await this.api.generateRecordingTranscript(recording.recordingId);
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
        this.selectedTimeline.set(timeline);
        this.transcriptSearchResults.set(null);
        this.syncTimelineStatuses(timeline);
        this.handleVideoTimeUpdate();
        const activeSegment = this.activeTimelineSegment();
        if (activeSegment) {
          await this.loadSubtitleTrackIfAvailable(activeSegment.recordingId, activeSegment.transcriptStatus);
        }
      }
    } catch (error) {
      this.transcriptError.set(this.api.explainError(error));
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
        this.pageError.set(this.api.explainError(error));
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
    this.transcriptError.set(null);
    this.playbackError.set(null);
    this.revokeSubtitleUrl();
    this.pendingAutoplay = autoplay;

    try {
      await this.loadSubtitleTrackIfAvailable(segment.recordingId, segment.transcriptStatus);
      this.handleVideoTimeUpdate();
    } catch (error) {
      if (this.selectedRecordingId() === segment.recordingId) {
        this.transcriptError.set(this.api.explainError(error));
      }
    }
  }

  async seekToTranscriptSegment(segment: SessionTranscriptSegmentResponse): Promise<void> {
    const targetSessionSecond = Number.parseFloat(segment.startSeconds ?? '0');
    const target = this.findTimelineSegmentForSessionSecond(targetSessionSecond);
    if (!target) {
      return;
    }

    const currentSegment = this.activeTimelineSegment();
    if (currentSegment && target.index === this.selectedTimelineSegmentIndex()) {
      const player = this.timelinePlayer?.nativeElement;
      if (player) {
        player.currentTime = Math.max(0, target.segmentSeekSeconds);
        void player.play().catch(() => undefined);
        this.handleVideoTimeUpdate();
      }
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
          approxDurationSeconds: sortedSessionRecordings.reduce(
            (total, recording) => total + (recording.durationSeconds ?? 0),
            0
          ),
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
