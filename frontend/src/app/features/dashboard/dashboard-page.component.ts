import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { interval } from 'rxjs';
import { RemoteAudioTrack, RemoteVideoTrack } from 'livekit-client';
import { LiveRoomService } from './live-room.service';
import { OperatorApiService } from './operator-api.service';
import { RecordingResponse, SessionResponse } from './operator.models';
import { ThemeService } from '../../theme.service';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatProgressBarModule,
    MatToolbarModule,
    MatIconModule
  ],
  template: `
    <main class="workspace-shell">
      <mat-toolbar class="workspace-toolbar">
        <div class="workspace-title">
          <h1>Body Cam</h1>
          <span>{{ api.operatorLabel() }}</span>
        </div>
        <div class="toolbar-actions">
          <button mat-icon-button type="button" (click)="theme.toggleTheme()" aria-label="Toggle theme">
            <mat-icon>{{ theme.theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
          </button>
          <button mat-button type="button" (click)="refreshAll()" [disabled]="isRefreshing()">
            Refresh
          </button>
          <button mat-button type="button" (click)="logout()">Sign Out</button>
        </div>
      </mat-toolbar>

      <section class="page workspace-grid">
        <mat-card class="panel section-panel" appearance="outlined">
          <div class="section-head">
            <h2>Sessions</h2>
            <span class="subtle-text">{{ sessions().length }}</span>
          </div>

          @if (isRefreshing() || isJoining() || isEnding()) {
            <mat-progress-bar mode="indeterminate"></mat-progress-bar>
          }

          @if (pageError()) {
            <div class="notice notice-error">{{ pageError() }}</div>
          }

          <div class="session-list">
            @for (session of sessions(); track session.id) {
              <mat-card
                class="session-card"
                appearance="outlined"
                [class.session-card-selected]="session.id === selectedSessionId()"
                (click)="selectSession(session.id)"
              >
                <div class="session-card-head">
                  <div>
                    <p class="session-worker">{{ session.workerName }}</p>
                    <p class="session-room">{{ session.roomName }}</p>
                  </div>
                  <span class="status-pill" [class.status-live]="session.status === 'ACTIVE'">
                    {{ session.status }}
                  </span>
                </div>

                <div class="inline-actions">
                  <button
                    mat-flat-button
                    type="button"
                    (click)="joinSession(session); $event.stopPropagation()"
                    [disabled]="session.status !== 'ACTIVE' || isJoining()"
                  >
                    {{ isJoining() && session.id === joiningSessionId() ? 'Joining...' : 'Join' }}
                  </button>
                  <button
                    mat-button
                    type="button"
                    (click)="endSession(session); $event.stopPropagation()"
                    [disabled]="session.status !== 'ACTIVE' || isEnding()"
                  >
                    End
                  </button>
                </div>
              </mat-card>
            } @empty {
              <div class="empty-state">No sessions available.</div>
            }
          </div>
        </mat-card>

        <section class="workspace-main">
          <mat-card class="panel viewer-panel" appearance="outlined">
            <div class="section-head">
              <h2>{{ selectedSession()?.workerName || 'Live View' }}</h2>
              <span class="status-pill" [class.status-live]="liveRoom.connectionLabel() === 'Live'">
                {{ liveRoom.connectionLabel() }}
              </span>
            </div>

            <div class="viewer-frame">
              <div class="viewer-stage" #videoHost>
                @if (!liveRoom.remoteVideoTrack()) {
                  <div class="viewer-empty">
                    <strong>{{ viewerMessage() }}</strong>
                  </div>
                }
              </div>
              <div #audioHost class="sr-only" aria-hidden="true"></div>
            </div>
          </mat-card>

          <mat-card class="panel section-panel" appearance="outlined">
            <div class="section-head">
              <h2>Recordings</h2>
              <span class="subtle-text">{{ filteredRecordings().length }}</span>
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
                  <strong>No recording selected</strong>
                </div>
              }
            </div>

            <mat-divider></mat-divider>

            <div class="recording-list">
              @for (recording of filteredRecordings(); track recording.id) {
                <mat-card
                  class="recording-card"
                  appearance="outlined"
                  [class.recording-card-selected]="recording.id === selectedRecordingId()"
                >
                  <button class="recording-card-button" type="button" (click)="selectRecording(recording.id)">
                    <strong>{{ recording.roomName }}</strong>
                    <span>{{ recording.createdAt | date: 'medium' }}</span>
                  </button>
                </mat-card>
              } @empty {
                <div class="empty-state">No recordings available.</div>
              }
            </div>
          </mat-card>
        </section>
      </section>
    </main>
  `
})
export class DashboardPageComponent implements AfterViewInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);

  private videoElement: HTMLMediaElement | null = null;
  private audioElement: HTMLMediaElement | null = null;

  @ViewChild('videoHost') private videoHost?: ElementRef<HTMLDivElement>;
  @ViewChild('audioHost') private audioHost?: ElementRef<HTMLDivElement>;

  readonly api = inject(OperatorApiService);
  readonly liveRoom = inject(LiveRoomService);
  readonly theme = inject(ThemeService);

  readonly sessions = signal<SessionResponse[]>([]);
  readonly recordings = signal<RecordingResponse[]>([]);
  readonly selectedSessionId = signal<string | null>(null);
  readonly selectedRecordingId = signal<string | null>(null);
  readonly isRefreshing = signal(false);
  readonly isJoining = signal(false);
  readonly joiningSessionId = signal<string | null>(null);
  readonly isEnding = signal(false);
  readonly pageError = signal<string | null>(null);

  readonly selectedSession = computed(
    () => this.sessions().find((session) => session.id === this.selectedSessionId()) ?? null
  );
  readonly filteredRecordings = computed(() => {
    const selectedSessionId = this.selectedSessionId();
    const recordings = this.recordings();
    return selectedSessionId
      ? recordings.filter((recording) => recording.sessionId === selectedSessionId)
      : recordings;
  });
  readonly selectedRecording = computed(
    () =>
      this.filteredRecordings().find(
        (recording) => recording.id === this.selectedRecordingId()
      ) ?? null
  );
  readonly viewerMessage = computed(() => {
    if (this.liveRoom.connectionLabel() === 'Connecting') {
      return 'Joining live stream';
    }

    if (this.liveRoom.connectionLabel() === 'Reconnecting') {
      return 'Reconnecting';
    }

    if (this.selectedSession()?.status === 'ACTIVE') {
      return 'Waiting for video';
    }

    return 'Select a session to start';
  });

  constructor() {
    effect(
      () => {
        const visibleRecordings = this.filteredRecordings();
        const selectedRecordingId = this.selectedRecordingId();

        if (!visibleRecordings.length) {
          this.selectedRecordingId.set(null);
          return;
        }

        if (!visibleRecordings.some((recording) => recording.id === selectedRecordingId)) {
          this.selectedRecordingId.set(visibleRecordings[0].id);
        }
      },
      { allowSignalWrites: true }
    );

    interval(10000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        void this.refreshAll(true);
      });

    void this.refreshAll();
  }

  ngAfterViewInit(): void {
    effect(
      () => {
        this.renderVideoTrack(this.liveRoom.remoteVideoTrack());
      },
      { injector: this.injector }
    );

    effect(
      () => {
        this.renderAudioTrack(this.liveRoom.remoteAudioTrack());
      },
      { injector: this.injector }
    );
  }

  ngOnDestroy(): void {
    this.detachMedia();
    this.liveRoom.disconnect();
  }

  logout(): void {
    this.liveRoom.disconnect();
    this.api.logout();
    this.sessions.set([]);
    this.recordings.set([]);
    this.selectedSessionId.set(null);
    this.selectedRecordingId.set(null);
    void this.router.navigateByUrl('/login');
  }

  selectSession(sessionId: string): void {
    this.selectedSessionId.set(sessionId);
  }

  selectRecording(recordingId: string): void {
    this.selectedRecordingId.set(recordingId);
  }

  async refreshAll(silent = false): Promise<void> {
    if (!silent) {
      this.isRefreshing.set(true);
      this.pageError.set(null);
    }

    try {
      const [sessions, recordings] = await Promise.all([
        this.api.listSessions(),
        this.api.listRecordings()
      ]);

      const sortedSessions = [...sessions].sort((left, right) => {
        if (left.status !== right.status) {
          return left.status === 'ACTIVE' ? -1 : 1;
        }

        return right.createdAt.localeCompare(left.createdAt);
      });

      const sortedRecordings = [...recordings].sort((left, right) =>
        right.createdAt.localeCompare(left.createdAt)
      );

      this.sessions.set(sortedSessions);
      this.recordings.set(sortedRecordings);

      if (
        sortedSessions.length &&
        !sortedSessions.some((session) => session.id === this.selectedSessionId())
      ) {
        this.selectedSessionId.set(sortedSessions[0].id);
      }

      if (!sortedSessions.length) {
        this.selectedSessionId.set(null);
      }
    } catch (error) {
      if (!silent) {
        this.pageError.set(this.api.explainError(error));
      }
    } finally {
      if (!silent) {
        this.isRefreshing.set(false);
      }
    }
  }

  async joinSession(session: SessionResponse): Promise<void> {
    const participantName = this.api.operatorLabel();
    this.pageError.set(null);
    this.isJoining.set(true);
    this.joiningSessionId.set(session.id);
    this.selectedSessionId.set(session.id);

    try {
      const tokenResponse = await this.api.createJoinToken(session.id, participantName);
      await this.liveRoom.connect(session.id, participantName, tokenResponse);
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isJoining.set(false);
      this.joiningSessionId.set(null);
    }
  }

  async endSession(session: SessionResponse): Promise<void> {
    this.pageError.set(null);
    this.isEnding.set(true);

    try {
      await this.api.endSession(session.id);
      if (this.liveRoom.sessionId() === session.id) {
        this.liveRoom.disconnect();
      }

      await this.refreshAll();
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isEnding.set(false);
    }
  }

  private renderVideoTrack(track: RemoteVideoTrack | null): void {
    const host = this.videoHost?.nativeElement;
    if (!host) {
      return;
    }

    if (this.videoElement) {
      this.videoElement.remove();
      this.videoElement = null;
    }

    if (!track) {
      return;
    }

    const element = track.attach() as HTMLVideoElement;
    element.autoplay = true;
    element.playsInline = true;
    element.className = 'live-video';
    host.appendChild(element);
    this.videoElement = element;
  }

  private renderAudioTrack(track: RemoteAudioTrack | null): void {
    const host = this.audioHost?.nativeElement;
    if (!host) {
      return;
    }

    if (this.audioElement) {
      this.audioElement.remove();
      this.audioElement = null;
    }

    if (!track) {
      return;
    }

    const element = track.attach();
    element.autoplay = true;
    host.appendChild(element);
    this.audioElement = element;
  }

  private detachMedia(): void {
    if (this.videoElement) {
      this.videoElement.remove();
      this.videoElement = null;
    }

    if (this.audioElement) {
      this.audioElement.remove();
      this.audioElement = null;
    }
  }
}
