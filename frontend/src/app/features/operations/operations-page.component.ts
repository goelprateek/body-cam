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
import { RemoteAudioTrack, RemoteVideoTrack } from 'livekit-client';
import { LiveRoomService } from './live-room.service';
import { OperatorApiService } from './operator-api.service';
import { SessionResponse } from './operator.models';

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
      <mat-card class="panel section-panel glass-panel" appearance="outlined">
        <div class="section-head premium-head">
          <div class="head-title">
            <h2>Active Sessions</h2>
            <span class="subtle-text live-count">{{ sessions().length }}</span>
          </div>
          <button mat-icon-button class="refresh-btn" (click)="refreshAll()" [disabled]="isRefreshing()">
             <mat-icon>refresh</mat-icon>
          </button>
        </div>

        @if (isRefreshing() || isJoining() || isEnding()) {
          <mat-progress-bar mode="indeterminate" class="premium-progress"></mat-progress-bar>
        }

        @if (pageError()) {
          <div class="notice notice-error">{{ pageError() }}</div>
        }

        <div class="session-list session-list-scroll premium-scroll" (scroll)="onSessionListScroll($event)">
          @for (session of sessions(); track session.id) {
            <mat-card
              class="session-card premium-card"
              appearance="outlined"
              [class.session-card-selected]="session.id === selectedSessionId()"
              (click)="selectSession(session.id)"
            >
              <div class="session-card-head">
                <div class="session-card-info">
                  <div class="session-avatar" [class.avatar-live]="session.status === 'ACTIVE'">
                    <mat-icon>person</mat-icon>
                  </div>
                  <div class="session-details">
                    <p class="session-worker">{{ session.workerName }}</p>
                    <p class="session-room">{{ session.roomName }}</p>
                  </div>
                </div>
                <span class="status-pill premium-status-pill" [class.status-live]="session.status === 'ACTIVE'">
                  @if (session.status === 'ACTIVE') { <span class="pulse-dot"></span> }
                  {{ session.status }}
                </span>
              </div>

              <div class="inline-actions session-actions">
                <button
                  mat-flat-button
                  type="button"
                  class="action-btn-join premium-btn"
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
            <div class="empty-state premium-empty">
              <div class="empty-icon-wrap">
                <mat-icon>satellite_alt</mat-icon>
              </div>
              <span class="empty-title">System Standby</span>
              <span class="empty-subtitle">No active field sessions.</span>
            </div>
          }

          @if (isLoadingMore()) {
            <div class="session-list-footer subtle-text">Loading more active sessions...</div>
          } @else if (!hasMoreSessions() && sessions().length) {
            <div class="session-list-footer subtle-text">All active sessions loaded</div>
          }
        </div>
      </mat-card>

      <section class="workspace-main">
        <mat-card class="panel viewer-panel glass-panel" appearance="outlined">
          <div class="section-head premium-head viewer-head">
            <div class="viewer-head-copy">
              <h2>{{ selectedSession()?.workerName || 'Live Viewer' }}</h2>
              <p class="viewer-caption">
                {{ liveRoom.lastEvent() }}
              </p>
            </div>
            <div class="viewer-status">
              <span class="status-pill premium-status-pill" [class.status-live]="liveRoom.connectionLabel() === 'Live'">
                @if (liveRoom.connectionLabel() === 'Live') { <span class="pulse-dot"></span> }
                {{ liveRoom.connectionLabel() }}
              </span>
            </div>
          </div>

          @if (liveRoom.error()) {
            <div class="notice notice-error">{{ liveRoom.error() }}</div>
          }

          <div class="viewer-frame premium-frame" [class.frame-live]="liveRoom.remoteVideoTrack()">
            <div class="viewer-stage premium-stage" #videoHost>
              @if (!liveRoom.remoteVideoTrack()) {
                <div class="viewer-empty premium-empty-viewer">
                  <div class="radar-scan"></div>
                  <mat-icon class="huge-icon premium-huge-icon">videocam_off</mat-icon>
                  <strong>{{ viewerMessage() }}</strong>
                </div>
              } @else {
                 <div class="hud-overlay premium-hud">
                    <div class="hud-top-right">
                       <span class="hud-rec"><mat-icon>fiber_manual_record</mat-icon> REC</span>
                    </div>
                    <div class="hud-corners">
                       <div class="corner tl"></div>
                       <div class="corner tr"></div>
                       <div class="corner bl"></div>
                       <div class="corner br"></div>
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
  private static readonly SESSION_PAGE_SIZE = 10;

  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  private videoElement: HTMLMediaElement | null = null;
  private audioElement: HTMLMediaElement | null = null;

  @ViewChild('videoHost') private videoHost?: ElementRef<HTMLDivElement>;
  @ViewChild('audioHost') private audioHost?: ElementRef<HTMLDivElement>;

  readonly api = inject(OperatorApiService);
  readonly liveRoom = inject(LiveRoomService);

  readonly sessions = signal<SessionResponse[]>([]);
  readonly selectedSessionId = signal<string | null>(null);
  readonly isRefreshing = signal(false);
  readonly isJoining = signal(false);
  readonly joiningSessionId = signal<string | null>(null);
  readonly isEnding = signal(false);
  readonly isLoadingMore = signal(false);
  readonly hasMoreSessions = signal(false);
  readonly pageError = signal<string | null>(null);

  private nextSessionsPage = 0;

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
      const page = await this.api.listActiveSessions(0, OperationsPageComponent.SESSION_PAGE_SIZE);

      this.sessions.set(page.items);
      this.nextSessionsPage = page.page + 1;
      this.hasMoreSessions.set(page.hasNext);

      if (
        page.items.length &&
        !page.items.some((session) => session.id === this.selectedSessionId())
      ) {
        this.selectedSessionId.set(page.items[0].id);
      }

      if (!page.items.length) {
        this.selectedSessionId.set(null);
        this.liveRoom.disconnect();
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

  async loadMoreSessions(): Promise<void> {
    if (
      this.isRefreshing() ||
      this.isLoadingMore() ||
      !this.hasMoreSessions()
    ) {
      return;
    }

    this.isLoadingMore.set(true);

    try {
      const page = await this.api.listActiveSessions(
        this.nextSessionsPage,
        OperationsPageComponent.SESSION_PAGE_SIZE
      );

      const knownIds = new Set(this.sessions().map((session) => session.id));
      const nextItems = page.items.filter((session) => !knownIds.has(session.id));
      this.sessions.update((sessions) => [...sessions, ...nextItems]);
      this.nextSessionsPage = page.page + 1;
      this.hasMoreSessions.set(page.hasNext);
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isLoadingMore.set(false);
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

  onSessionListScroll(event: Event): void {
    const host = event.target as HTMLDivElement | null;
    if (!host) {
      return;
    }

    const remaining = host.scrollHeight - host.scrollTop - host.clientHeight;
    if (remaining <= 120) {
      void this.loadMoreSessions();
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
