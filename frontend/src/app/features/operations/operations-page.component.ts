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
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { interval } from 'rxjs';
import { RemoteAudioTrack, RemoteVideoTrack } from 'livekit-client';
import { LiveRoomService } from './live-room.service';
import { OperatorApiService } from './operator-api.service';
import { RecordingResponse, SessionResponse } from './operator.models';
import { DashboardEventService } from '@shared/services/dashboard-event.service';

@Component({
  selector: 'app-operations-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatProgressBarModule,
    MatIconModule,
    MatTooltipModule
  ],
  template: `
    <section class="page workspace-grid">
      <mat-card class="panel section-panel" appearance="outlined">
        <div class="section-head">
          <h2>Active Sessions</h2>
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
                <div class="session-card-info">
                  <div class="session-avatar" [class.avatar-live]="session.status === 'ACTIVE'">
                    <mat-icon>person</mat-icon>
                  </div>
                  <div>
                    <p class="session-worker">{{ session.workerName }}</p>
                    <p class="session-room">{{ session.roomName }}</p>
                  </div>
                </div>
                <span class="status-pill" [class.status-live]="session.status === 'ACTIVE'">
                  {{ session.status }}
                </span>
              </div>

              <div class="inline-actions">
                <button
                  mat-flat-button
                  type="button"
                  class="action-btn-join"
                  (click)="joinSession(session); $event.stopPropagation()"
                  [disabled]="session.status !== 'ACTIVE' || isJoining()"
                >
                  <mat-icon>visibility</mat-icon>
                  {{ isJoining() && session.id === joiningSessionId() ? 'Joining...' : 'View Live' }}
                </button>
                <button
                  mat-button
                  type="button"
                  class="action-btn-end"
                  (click)="endSession(session); $event.stopPropagation()"
                  [disabled]="session.status !== 'ACTIVE' || isEnding()"
                >
                  <mat-icon>stop_circle</mat-icon>
                  End Stream
                </button>
              </div>
            </mat-card>
          } @empty {
            <div class="empty-state">
              <mat-icon>no_photography</mat-icon>
              <span>No active sessions.</span>
            </div>
          }
        </div>
      </mat-card>

      <section class="workspace-main">
        <mat-card class="panel viewer-panel" appearance="outlined">
          <div class="section-head">
            <h2>{{ selectedSession()?.workerName || 'Live Viewer' }}</h2>
            <div class="viewer-status">
              <span class="status-pill" [class.status-live]="liveRoom.connectionLabel() === 'Live'">
                {{ liveRoom.connectionLabel() }}
              </span>
            </div>
          </div>

          <div class="viewer-frame" [class.frame-live]="liveRoom.remoteVideoTrack()">
            <div class="viewer-stage" #videoHost>
              @if (!liveRoom.remoteVideoTrack()) {
                <div class="viewer-empty">
                  <mat-icon class="huge-icon">videocam_off</mat-icon>
                  <strong>{{ viewerMessage() }}</strong>
                </div>
              } @else {
                 <div class="hud-overlay">
                    <div class="hud-top-right">
                       <span class="hud-rec"><mat-icon>fiber_manual_record</mat-icon> REC</span>
                    </div>
                 </div>
              }
            </div>
            <div #audioHost class="sr-only" aria-hidden="true"></div>
          </div>
        </mat-card>
      </section>
    </section>
  `
})
export class OperationsPageComponent implements AfterViewInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  private videoElement: HTMLMediaElement | null = null;
  private audioElement: HTMLMediaElement | null = null;

  @ViewChild('videoHost') private videoHost?: ElementRef<HTMLDivElement>;
  @ViewChild('audioHost') private audioHost?: ElementRef<HTMLDivElement>;

  readonly api = inject(OperatorApiService);
  readonly liveRoom = inject(LiveRoomService);
  private readonly events = inject(DashboardEventService);

  readonly sessions = signal<SessionResponse[]>([]);
  readonly selectedSessionId = signal<string | null>(null);
  readonly isRefreshing = signal(false);
  readonly isJoining = signal(false);
  readonly joiningSessionId = signal<string | null>(null);
  readonly isEnding = signal(false);
  readonly pageError = signal<string | null>(null);

  readonly selectedSession = computed(
    () => this.sessions().find((session) => session.id === this.selectedSessionId()) ?? null
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


    this.events.refresh$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        void this.refreshAll();
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



  selectSession(sessionId: string): void {
    this.selectedSessionId.set(sessionId);
  }



  async refreshAll(silent = false): Promise<void> {
    if (!silent) {
      this.isRefreshing.set(true);
      this.pageError.set(null);
    }

    try {
      const sessions = await this.api.listSessions();

      const sortedSessions = [...sessions].sort((left, right) => {
        if (left.status !== right.status) {
          return left.status === 'ACTIVE' ? -1 : 1;
        }

        return right.createdAt.localeCompare(left.createdAt);
      });

      this.sessions.set(sortedSessions);

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
